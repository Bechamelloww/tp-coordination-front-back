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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
/** Journal d'activité des cabinets : écrit à chaque action, lu par cabinet, jamais public. Base dédiée. */
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
   .andExpect(jsonPath("$[0].detail").value("Alex Martin (autre@medislot.test) ajouté comme patient"))
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
   .andExpect(jsonPath("$[0].detail").value("Alex Martin (autre@medislot.test) ajouté comme admin"))
   .andExpect(jsonPath("$[1].action").value("CLINIC_CREATED"))
   .andExpect(jsonPath("$[1].actor").value("master@medislot.test"));
  mvc.perform(get("/api/master/logs").with(user("master@medislot.test").roles("MASTER")))
   .andExpect(status().isOk()).andExpect(jsonPath("$[*].clinicSlug",hasItem("cabinet-journal")));
 }

 private org.springframework.test.web.servlet.ResultActions logsOf(String clinic,String admin) throws Exception {
  return mvc.perform(get("/api/admin/logs").header("X-Clinic",clinic).with(user(admin).roles("PATIENT"))).andExpect(status().isOk());
 }

 @Test void leParcoursDUnPatientEstRaconteDansLeJournal() throws Exception {
  // inscription dans cabinet-a
  mvc.perform(post("/api/register").header("X-Clinic","cabinet-a").with(csrf()).contentType(MediaType.APPLICATION_JSON)
   .content("{\"email\":\"Jeanne@medislot.test\",\"password\":\"une phrase assez longue\",\"name\":\"Jeanne Durand\"}")).andExpect(status().isCreated());
  logsOf("cabinet-a","admin-a@medislot.test").andExpect(jsonPath("$[0].action").value("ACCOUNT_CREATED"))
   .andExpect(jsonPath("$[0].detail").value("Jeanne Durand (jeanne@medislot.test) a créé son compte"));
  // rendez-vous dans cabinet-b : elle en devient membre, puis déplace et annule
  Long pro=jdbc.queryForObject("select min(id) from professional where clinic_id=(select id from clinic where slug='cabinet-b')",Long.class);
  String name=jdbc.queryForObject("select name from professional where id=?",String.class,pro);
  var free=jdbc.queryForList("select s.id from slot s where s.professional_id=? and s.starts_at>now()+interval '1 day' and not exists(select 1 from appointment a where a.slot_id=s.id and a.status='CONFIRMED') order by s.starts_at limit 2",Long.class,pro);
  String booked=mvc.perform(post("/api/appointments").header("X-Clinic","cabinet-b").with(user("jeanne@medislot.test").roles("PATIENT")).with(csrf())
   .contentType(MediaType.APPLICATION_JSON).content("{\"slotId\":"+free.get(0)+"}")).andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
  long id=com.jayway.jsonpath.JsonPath.<Number>read(booked,"$.id").longValue();
  mvc.perform(patch("/api/appointments/"+id).header("X-Clinic","cabinet-b").with(user("jeanne@medislot.test").roles("PATIENT")).with(csrf())
   .contentType(MediaType.APPLICATION_JSON).content("{\"slotId\":"+free.get(1)+"}")).andExpect(status().isOk());
  mvc.perform(post("/api/appointments/"+id+"/cancel").header("X-Clinic","cabinet-b").with(user("jeanne@medislot.test").roles("PATIENT")).with(csrf())).andExpect(status().isOk());
  String rdv="Jeanne Durand a pris rendez-vous avec "+name+" (";
  logsOf("cabinet-b","admin-b@medislot.test")
   .andExpect(jsonPath("$[0].action").value("APPOINTMENT_CANCELLED"))
   .andExpect(jsonPath("$[0].detail",startsWith("Jeanne Durand a annulé son rendez-vous avec "+name)))
   .andExpect(jsonPath("$[1].action").value("APPOINTMENT_MOVED"))
   .andExpect(jsonPath("$[1].detail",allOf(startsWith("Jeanne Durand a déplacé son rendez-vous avec "+name),containsString(" : nouveau rendez-vous avec "))))
   .andExpect(jsonPath("$[2].action").value("MEMBER_JOINED"))
   .andExpect(jsonPath("$[3].action").value("APPOINTMENT_BOOKED"))
   .andExpect(jsonPath("$[3].actor").value("jeanne@medislot.test"))
   .andExpect(jsonPath("$[3].detail",allOf(startsWith(rdv),matchesPattern(".* pour le \\p{L}+ \\d{1,2} \\p{L}+ \\d{4} à \\d{2}h\\d{2}"))));
  // rien de tout ça dans le journal de cabinet-a, à part l'inscription
  logsOf("cabinet-a","admin-a@medislot.test").andExpect(jsonPath("$[*].detail",not(hasItem(startsWith(rdv)))));
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
