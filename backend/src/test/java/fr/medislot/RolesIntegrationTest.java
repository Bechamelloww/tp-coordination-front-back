package fr.medislot;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import static org.hamcrest.Matchers.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
@SpringBootTest @Testcontainers
class RolesIntegrationTest {
 @Container static PostgreSQLContainer db=new PostgreSQLContainer("postgres:18");
 @DynamicPropertySource static void database(DynamicPropertyRegistry r){r.add("spring.datasource.url",db::getJdbcUrl);r.add("spring.datasource.username",db::getUsername);r.add("spring.datasource.password",db::getPassword);r.add("medislot.demo-seed",()->"true");r.add("medislot.demo-password",()->java.util.UUID.randomUUID().toString());}
 @Autowired WebApplicationContext context;
 MockMvc mvc;
 @BeforeEach void setUp(){mvc=MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();}

 private org.springframework.test.web.servlet.ResultActions call(String path,String clinic,String email,String role) throws Exception {
  var req=get(path).with(user(email).roles(role));
  if(clinic!=null)req.header("X-Clinic",clinic);
  return mvc.perform(req);
 }

 @Test void unPatientNAccedePasAuTableauDeBord() throws Exception {
  call("/api/admin/overview","cabinet-a","patient@medislot.test","PATIENT").andExpect(status().isForbidden());
 }

 @Test void lAdminVoitSonCabinet() throws Exception {
  call("/api/admin/overview","cabinet-a","admin-a@medislot.test","PATIENT").andExpect(status().isOk()).andExpect(jsonPath("$.admins").value(1));
  call("/api/admin/members","cabinet-a","admin-a@medislot.test","PATIENT").andExpect(status().isOk())
   .andExpect(jsonPath("$[*].email",hasItem("autre@medislot.test"))).andExpect(jsonPath("$[*].email",not(hasItem("admin-b@medislot.test"))));
 }

 @Test void lAdminDuCabinetANeVoitPasLeCabinetB() throws Exception {
  call("/api/admin/members","cabinet-b","admin-a@medislot.test","PATIENT").andExpect(status().isNotFound());
 }

 @Test void leMasterVoitLesDeuxCabinets() throws Exception {
  call("/api/admin/members","cabinet-b","master@medislot.test","MASTER").andExpect(status().isOk()).andExpect(jsonPath("$[*].email",hasItem("admin-b@medislot.test")));
  call("/api/master/clinics",null,"master@medislot.test","MASTER").andExpect(status().isOk())
   .andExpect(jsonPath("$[*].slug",contains("cabinet-a","cabinet-b"))).andExpect(jsonPath("$[1].admins[0]").value("admin-b@medislot.test"));
  call("/api/me/clinics",null,"master@medislot.test","MASTER").andExpect(jsonPath("$[*].role",everyItem(is("ADMIN"))));
 }

 @Test void seulLeMasterAccedeALaVueGlobale() throws Exception {
  call("/api/master/clinics",null,"admin-a@medislot.test","PATIENT").andExpect(status().isForbidden());
 }

 @Test void unMasterRetrogradeEnBaseNAccedePlusALaVueGlobale() throws Exception {
  call("/api/master/clinics",null,"admin-a@medislot.test","MASTER").andExpect(status().isForbidden());
 }

 @Test void leSelecteurDonneLeRoleDansChaqueCabinet() throws Exception {
  call("/api/me/clinics",null,"admin-a@medislot.test","PATIENT").andExpect(jsonPath("$[0].slug").value("cabinet-a")).andExpect(jsonPath("$[0].role").value("ADMIN"));
  call("/api/me/clinics",null,"patient@medislot.test","PATIENT").andExpect(jsonPath("$[*].role",everyItem(is("MEMBER"))));
 }

 @Test void lePatientVoitTousLesCabinetsEtLeGerantSeulementLeSien() throws Exception {
  call("/api/me/clinics",null,"autre@medislot.test","PATIENT").andExpect(jsonPath("$[*].slug",hasItems("cabinet-a","cabinet-b")))
   .andExpect(jsonPath("$[?(@.slug=='cabinet-b')].role",contains(nullValue())));
  call("/api/professionals","cabinet-b","autre@medislot.test","PATIENT").andExpect(status().isOk());
  call("/api/me/clinics",null,"admin-a@medislot.test","PATIENT").andExpect(jsonPath("$[*].slug",contains("cabinet-a")));
  call("/api/professionals","cabinet-b","admin-a@medislot.test","PATIENT").andExpect(status().isNotFound());
 }

 @Test void unVisiteurNonConnecteVoitLaListeDesCabinets() throws Exception {
  mvc.perform(get("/api/clinics")).andExpect(status().isOk()).andExpect(jsonPath("$[*].slug",hasItems("cabinet-a","cabinet-b")))
   .andExpect(jsonPath("$[0].role").value(nullValue()));
 }
}
