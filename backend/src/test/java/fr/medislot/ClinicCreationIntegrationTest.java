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
/** Création d'un cabinet par le master (POST /api/master/clinics). Base dédiée : les cabinets créés ne gênent pas les autres tests. */
@SpringBootTest @Testcontainers
class ClinicCreationIntegrationTest {
 @Container static PostgreSQLContainer db=new PostgreSQLContainer("postgres:18");
 @DynamicPropertySource static void database(DynamicPropertyRegistry r){r.add("spring.datasource.url",db::getJdbcUrl);r.add("spring.datasource.username",db::getUsername);r.add("spring.datasource.password",db::getPassword);r.add("medislot.demo-seed",()->"true");r.add("medislot.demo-password",()->java.util.UUID.randomUUID().toString());}
 @Autowired WebApplicationContext context;
 MockMvc mvc;
 @BeforeEach void setUp(){mvc=MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();}

 private ResultActions create(String email,String role,String json) throws Exception {
  return mvc.perform(post("/api/master/clinics").with(user(email).roles(role)).with(csrf()).contentType(MediaType.APPLICATION_JSON).content(json));
 }

 @Test void leMasterCreeUnCabinetAvecSonAdmin() throws Exception {
  create("master@medislot.test","MASTER","""
   {"slug":"cabinet-c","name":"Centre médical du Port","city":"Marseille","address":"12 quai du Port","color":"#B45309","adminEmail":"Autre@medislot.test"}""")
   .andExpect(status().isCreated()).andExpect(jsonPath("$.slug").value("cabinet-c")).andExpect(jsonPath("$.color").value("#b45309"))
   .andExpect(jsonPath("$.address").value("12 quai du Port")).andExpect(jsonPath("$.professionals").value(0)).andExpect(jsonPath("$.admins",contains("autre@medislot.test")));
  mvc.perform(get("/api/master/clinics").with(user("master@medislot.test").roles("MASTER"))).andExpect(jsonPath("$[*].slug",hasItem("cabinet-c")));
  mvc.perform(get("/api/admin/overview").header("X-Clinic","cabinet-c").with(user("autre@medislot.test").roles("PATIENT")))
   .andExpect(status().isOk()).andExpect(jsonPath("$.admins").value(1));
 }

 @Test void unCabinetSansAdminEstAccepte() throws Exception {
  create("master@medislot.test","MASTER","""
   {"slug":"cabinet-sans-admin","name":"Cabinet du Lac","city":"Annecy","address":"3 rue du Lac","color":"#0f766e"}""")
   .andExpect(status().isCreated()).andExpect(jsonPath("$.admins",empty()));
 }

 @Test void unIdentifiantDejaPrisEst409() throws Exception {
  create("master@medislot.test","MASTER","""
   {"slug":"cabinet-a","name":"Doublon","city":"Paris","address":"1 rue X","color":"#000000"}""").andExpect(status().isConflict());
 }

 @Test void lesChampsInvalidesSontSignalesUnParUn() throws Exception {
  create("master@medislot.test","MASTER","""
   {"slug":"Cabinet C","name":"","city":"Nice","address":" ","color":"bleu"}""")
   .andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors[*].field",hasItems("slug","name","address","color")));
 }

 @Test void unAdminInconnuEst400EtRienNEstCree() throws Exception {
  create("master@medislot.test","MASTER","""
   {"slug":"cabinet-fantome","name":"Fantôme","city":"Lille","address":"5 rue Y","color":"#123456","adminEmail":"personne@medislot.test"}""").andExpect(status().isBadRequest());
  mvc.perform(get("/api/master/clinics").with(user("master@medislot.test").roles("MASTER"))).andExpect(jsonPath("$[*].slug",not(hasItem("cabinet-fantome"))));
 }

 @Test void seulLeMasterPeutCreer() throws Exception {
  create("admin-a@medislot.test","PATIENT","""
   {"slug":"cabinet-pirate","name":"Pirate","city":"Paris","address":"1 rue Z","color":"#123456"}""").andExpect(status().isForbidden());
 }

 @Test void laListeDesAdminsPossiblesExclutLesMasters() throws Exception {
  mvc.perform(get("/api/master/accounts").with(user("master@medislot.test").roles("MASTER"))).andExpect(status().isOk())
   .andExpect(jsonPath("$[*].email",hasItems("autre@medislot.test","admin-a@medislot.test"))).andExpect(jsonPath("$[*].email",not(hasItem("master@medislot.test"))));
  mvc.perform(get("/api/master/accounts").with(user("admin-a@medislot.test").roles("PATIENT"))).andExpect(status().isForbidden());
 }
}
