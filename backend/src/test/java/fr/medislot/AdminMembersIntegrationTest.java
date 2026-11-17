package fr.medislot;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import static org.hamcrest.Matchers.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
/** Ajout d'un membre au cabinet courant (POST /api/admin/members). Base dédiée : les ajouts ne gênent pas les autres tests. */
@SpringBootTest @Testcontainers
class AdminMembersIntegrationTest {
 @Container static PostgreSQLContainer db=new PostgreSQLContainer("postgres:18");
 @DynamicPropertySource static void database(DynamicPropertyRegistry r){r.add("spring.datasource.url",db::getJdbcUrl);r.add("spring.datasource.username",db::getUsername);r.add("spring.datasource.password",db::getPassword);r.add("medislot.demo-seed",()->"true");r.add("medislot.demo-password",()->java.util.UUID.randomUUID().toString());}
 @Autowired WebApplicationContext context;
 MockMvc mvc;
 @BeforeEach void setUp(){mvc=MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();}

 private ResultActions add(String clinic,String email,String role,String json) throws Exception {
  return mvc.perform(post("/api/admin/members").header("X-Clinic",clinic).with(user(email).roles(role)).with(csrf()).contentType(MediaType.APPLICATION_JSON).content(json));
 }

 @Test void lAdminAjouteUnMembreASonCabinet() throws Exception {
  add("cabinet-b","admin-b@medislot.test","PATIENT","{\"email\":\"Autre@medislot.test\"}")
   .andExpect(status().isCreated()).andExpect(jsonPath("$.email").value("autre@medislot.test")).andExpect(jsonPath("$.role").value("MEMBER"));
  mvc.perform(get("/api/admin/members").header("X-Clinic","cabinet-b").with(user("admin-b@medislot.test").roles("PATIENT")))
   .andExpect(jsonPath("$[*].email",hasItem("autre@medislot.test")));
  add("cabinet-b","admin-b@medislot.test","PATIENT","{\"email\":\"autre@medislot.test\"}").andExpect(status().isConflict());
 }

 @Test void leMasterPeutNommerUnAdmin() throws Exception {
  add("cabinet-a","master@medislot.test","MASTER","{\"email\":\"admin-b@medislot.test\",\"role\":\"ADMIN\"}")
   .andExpect(status().isCreated()).andExpect(jsonPath("$.role").value("ADMIN"));
 }

 @Test void lesCasRefusesNeCreentRien() throws Exception {
  add("cabinet-a","admin-a@medislot.test","PATIENT","{\"email\":\"personne@medislot.test\"}").andExpect(status().isNotFound());
  add("cabinet-a","admin-a@medislot.test","PATIENT","{\"email\":\"master@medislot.test\"}").andExpect(status().isBadRequest());
  add("cabinet-a","admin-a@medislot.test","PATIENT","{\"email\":\"pas-un-email\",\"role\":\"ROOT\"}")
   .andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors[*].field",hasItems("email","role")));
 }

 @Test void unPatientOuLAdminDUnAutreCabinetNePeutPasAjouter() throws Exception {
  add("cabinet-a","patient@medislot.test","PATIENT","{\"email\":\"autre@medislot.test\"}").andExpect(status().isForbidden());
  add("cabinet-b","admin-a@medislot.test","PATIENT","{\"email\":\"autre@medislot.test\"}").andExpect(status().isNotFound());
 }
}
