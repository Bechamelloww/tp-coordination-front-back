package fr.medislot;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
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
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
/** Annuaire et « mes rendez-vous » tous cabinets confondus. Base dédiée : le test insère un rendez-vous. */
@SpringBootTest @Testcontainers
class CrossClinicIntegrationTest {
 @Container static PostgreSQLContainer db=new PostgreSQLContainer("postgres:18");
 @DynamicPropertySource static void database(DynamicPropertyRegistry r){r.add("spring.datasource.url",db::getJdbcUrl);r.add("spring.datasource.username",db::getUsername);r.add("spring.datasource.password",db::getPassword);r.add("medislot.demo-seed",()->"true");r.add("medislot.demo-password",()->java.util.UUID.randomUUID().toString());}
 @Autowired WebApplicationContext context; @Autowired JdbcTemplate jdbc;
 MockMvc mvc;
 @BeforeEach void setUp(){mvc=MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();}

 @Test void unVisiteurVoitLesPraticiensDeTousLesCabinets() throws Exception {
  long all=jdbc.queryForObject("select count(*) from professional",Long.class);
  mvc.perform(get("/api/directory?size=50")).andExpect(status().isOk()).andExpect(jsonPath("$.total").value((int)all))
   .andExpect(jsonPath("$.items[*].clinicSlug",hasItems("cabinet-a","cabinet-b"))).andExpect(jsonPath("$.items[0].clinicName").isNotEmpty());
  mvc.perform(get("/api/directory?size=2&page=0")).andExpect(jsonPath("$.items",hasSize(2)));
  mvc.perform(get("/api/directory?size=500")).andExpect(status().isBadRequest());
 }

 @Test void lesFiltresSAppliquentATousLesCabinets() throws Exception {
  String specialty=jdbc.queryForObject("select specialty from professional where clinic_id=(select id from clinic where slug='cabinet-b') limit 1",String.class);
  mvc.perform(get("/api/directory?size=50&specialty="+specialty)).andExpect(jsonPath("$.items[*].specialty",everyItem(is(specialty))));
 }

 @Test void lePatientVoitSesRendezVousDeTousLesCabinetsEtSeulementLesSiens() throws Exception {
  Long clinicB=jdbc.queryForObject("select id from clinic where slug='cabinet-b'",Long.class);
  Long slot=jdbc.queryForObject("select id from slot where clinic_id=? and starts_at>now() limit 1",Long.class,clinicB);
  Long autre=jdbc.queryForObject("select id from app_user where email='autre@medislot.test'",Long.class);
  jdbc.update("insert into appointment(patient_id,slot_id,status,created_at,clinic_id) values (?,?,'CONFIRMED',now(),?)",autre,slot,clinicB);
  jdbc.update("insert into notification(patient_id,message,created_at,clinic_id) values (?,'Bienvenue au Canal',now(),?)",autre,clinicB);
  mvc.perform(get("/api/me/appointments").with(user("autre@medislot.test").roles("PATIENT"))).andExpect(status().isOk())
   .andExpect(jsonPath("$[*].clinicSlug",hasItem("cabinet-b"))).andExpect(jsonPath("$[*].slotId",hasItem(slot.intValue())));
  mvc.perform(get("/api/me/notifications").with(user("autre@medislot.test").roles("PATIENT"))).andExpect(jsonPath("$[*].message",hasItem("Bienvenue au Canal")))
   .andExpect(jsonPath("$[?(@.message=='Bienvenue au Canal')].readAt",contains(nullValue())));
  Long notice=jdbc.queryForObject("select id from notification where message='Bienvenue au Canal'",Long.class);
  mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/api/me/notifications/read").with(user("patient@medislot.test").roles("PATIENT"))
   .with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf()).contentType("application/json").content("{\"ids\":["+notice+"]}")).andExpect(status().isNoContent());
  org.junit.jupiter.api.Assertions.assertNull(jdbc.queryForObject("select read_at from notification where id=?",java.sql.Timestamp.class,notice),"un autre patient ne peut pas la marquer lue");
  mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/api/me/notifications/read").with(user("autre@medislot.test").roles("PATIENT"))
   .with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf()).contentType("application/json").content("{\"ids\":["+notice+"]}")).andExpect(status().isNoContent());
  org.junit.jupiter.api.Assertions.assertNotNull(jdbc.queryForObject("select read_at from notification where id=?",java.sql.Timestamp.class,notice));
  mvc.perform(get("/api/me/appointments").with(user("patient@medislot.test").roles("PATIENT"))).andExpect(jsonPath("$[*].slotId",not(hasItem(slot.intValue()))));
  mvc.perform(get("/api/me/appointments")).andExpect(status().isUnauthorized());
 }
}
