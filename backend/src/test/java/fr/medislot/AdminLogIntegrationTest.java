package fr.medislot;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataAccessException;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
/** Journal des actions d'administration : écrit à chaque action, lu par cabinet, jamais public. Base dédiée. */
@SpringBootTest @Testcontainers
class AdminLogIntegrationTest {
 @Container static PostgreSQLContainer db=new PostgreSQLContainer("postgres:18");
 @DynamicPropertySource static void database(DynamicPropertyRegistry r){r.add("spring.datasource.url",db::getJdbcUrl);r.add("spring.datasource.username",db::getUsername);r.add("spring.datasource.password",db::getPassword);r.add("medislot.demo-seed",()->"true");r.add("medislot.demo-password",()->java.util.UUID.randomUUID().toString());}
 @Autowired WebApplicationContext context;
 @Autowired JdbcTemplate jdbc;
 MockMvc mvc;
 @BeforeEach void setUp(){mvc=MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();}

 private void addMember(String clinic,String admin,String role,String email) throws Exception {
  mvc.perform(post("/api/admin/members").header("X-Clinic",clinic).with(user(admin).roles(role)).with(csrf())
   .contentType(MediaType.APPLICATION_JSON).content("{\"email\":\""+email+"\"}")).andExpect(status().isCreated());
 }

 @Test void lAjoutDUnMembreEstJournaliseDansSonCabinetSeulement() throws Exception {
  addMember("cabinet-b","admin-b@medislot.test","PATIENT","autre@medislot.test");
  mvc.perform(get("/api/admin/logs").header("X-Clinic","cabinet-b").with(user("admin-b@medislot.test").roles("PATIENT")))
   .andExpect(status().isOk())
   .andExpect(jsonPath("$[0].action").value("MEMBER_ADDED"))
   .andExpect(jsonPath("$[0].actor").value("admin-b@medislot.test"))
   .andExpect(jsonPath("$[0].detail").value("autre@medislot.test ajouté comme patient"))
   .andExpect(jsonPath("$[0].clinicSlug").value("cabinet-b"));
  mvc.perform(get("/api/admin/logs").header("X-Clinic","cabinet-a").with(user("admin-a@medislot.test").roles("PATIENT")))
   .andExpect(status().isOk()).andExpect(jsonPath("$[*].clinicSlug",everyItem(is("cabinet-a"))));
 }

 @Test void laCreationDUnCabinetOuvreSonJournal() throws Exception {
  mvc.perform(post("/api/master/clinics").with(user("master@medislot.test").roles("MASTER")).with(csrf()).contentType(MediaType.APPLICATION_JSON).content("""
   {"slug":"cabinet-journal","name":"Cabinet du Journal","city":"Nantes","address":"1 rue du Port","color":"#123456","adminEmail":"autre@medislot.test"}"""))
   .andExpect(status().isCreated());
  mvc.perform(get("/api/admin/logs").header("X-Clinic","cabinet-journal").with(user("autre@medislot.test").roles("PATIENT")))
   .andExpect(status().isOk()).andExpect(jsonPath("$",hasSize(2)))
   .andExpect(jsonPath("$[0].detail").value("autre@medislot.test ajouté comme admin"))
   .andExpect(jsonPath("$[1].action").value("CLINIC_CREATED"))
   .andExpect(jsonPath("$[1].actor").value("master@medislot.test"));
  mvc.perform(get("/api/master/logs").with(user("master@medislot.test").roles("MASTER")))
   .andExpect(status().isOk()).andExpect(jsonPath("$[*].clinicSlug",hasItem("cabinet-journal")));
 }

 @Test void uneActionRefuseeNeLaissePasDeTrace() throws Exception {
  Long before=jdbc.queryForObject("select count(*) from admin_log",Long.class);
  mvc.perform(post("/api/admin/members").header("X-Clinic","cabinet-a").with(user("admin-a@medislot.test").roles("PATIENT")).with(csrf())
   .contentType(MediaType.APPLICATION_JSON).content("{\"email\":\"personne@medislot.test\"}")).andExpect(status().isNotFound());
  mvc.perform(post("/api/master/clinics").with(user("master@medislot.test").roles("MASTER")).with(csrf()).contentType(MediaType.APPLICATION_JSON).content("""
   {"slug":"cabinet-rate","name":"Raté","city":"Lille","address":"5 rue Y","color":"#123456","adminEmail":"personne@medislot.test"}"""))
   .andExpect(status().isBadRequest());
  org.junit.jupiter.api.Assertions.assertEquals(before,jdbc.queryForObject("select count(*) from admin_log",Long.class));
 }

 @Test void leJournalNEstPasPublic() throws Exception {
  mvc.perform(get("/api/admin/logs").header("X-Clinic","cabinet-a")).andExpect(status().isUnauthorized());
  mvc.perform(get("/api/master/logs")).andExpect(status().isUnauthorized());
  mvc.perform(get("/api/admin/logs").header("X-Clinic","cabinet-a").with(user("patient@medislot.test").roles("PATIENT"))).andExpect(status().isForbidden());
  // admin d'un autre cabinet : le cabinet n'existe pas pour lui
  mvc.perform(get("/api/admin/logs").header("X-Clinic","cabinet-b").with(user("admin-a@medislot.test").roles("PATIENT"))).andExpect(status().isNotFound());
  mvc.perform(get("/api/master/logs").with(user("admin-a@medislot.test").roles("PATIENT"))).andExpect(status().isForbidden());
 }

 @Test void leJournalSePagineDuPlusRecentAuPlusAncien() throws Exception {
  for(int i=0;i<55;i++)jdbc.update("insert into admin_log(clinic_id,actor_email,action,detail) select id,'robot@medislot.test','MEMBER_ADDED','ligne "+i+"' from clinic where slug='cabinet-a'");
  String first=mvc.perform(get("/api/admin/logs").header("X-Clinic","cabinet-a").with(user("admin-a@medislot.test").roles("PATIENT")))
   .andExpect(jsonPath("$",hasSize(50))).andExpect(jsonPath("$[0].detail").value("ligne 54")).andReturn().getResponse().getContentAsString();
  long last=com.jayway.jsonpath.JsonPath.<Number>read(first,"$[49].id").longValue();
  mvc.perform(get("/api/admin/logs").param("before",String.valueOf(last)).header("X-Clinic","cabinet-a").with(user("admin-a@medislot.test").roles("PATIENT")))
   .andExpect(jsonPath("$[*].detail",hasItem("ligne 0"))).andExpect(jsonPath("$[*].detail",not(hasItem("ligne 54"))));
 }

 @Test void uneLigneDuJournalNeSeModifieNiNeSEfface() {
  jdbc.update("insert into admin_log(clinic_id,actor_email,action,detail) select id,'robot@medislot.test','MEMBER_ADDED','à garder' from clinic where slug='cabinet-a'");
  assertThrows(DataAccessException.class,()->jdbc.update("update admin_log set detail='effacé'"));
  assertThrows(DataAccessException.class,()->jdbc.update("delete from admin_log"));
 }
}
