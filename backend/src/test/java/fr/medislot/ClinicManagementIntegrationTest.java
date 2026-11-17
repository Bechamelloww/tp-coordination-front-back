package fr.medislot;
import fr.medislot.application.BookingService;
import fr.medislot.tenant.TenantContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.server.ResponseStatusException;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
/** Modification et suppression d'un cabinet par le master (PUT / DELETE /api/master/clinics/{slug}). Base dédiée. */
@SpringBootTest @Testcontainers
class ClinicManagementIntegrationTest {
 @Container static PostgreSQLContainer db=new PostgreSQLContainer("postgres:18");
 @DynamicPropertySource static void database(DynamicPropertyRegistry r){r.add("spring.datasource.url",db::getJdbcUrl);r.add("spring.datasource.username",db::getUsername);r.add("spring.datasource.password",db::getPassword);r.add("medislot.demo-seed",()->"true");r.add("medislot.demo-password",()->java.util.UUID.randomUUID().toString());}
 @Autowired WebApplicationContext context;
 @Autowired JdbcTemplate jdbc;
 @Autowired BookingService booking;
 MockMvc mvc;
 @BeforeEach void setUp(){mvc=MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();}

 private static final String MASTER="master@medislot.test";
 private ResultActions asMaster(org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder request) throws Exception {
  return mvc.perform(request.with(user(MASTER).roles("MASTER")).with(csrf()));
 }
 /** Nouveau cabinet avec un admin, un praticien et un créneau à venir ; rend l'id du créneau. */
 private long clinicWithSlot(String slug,String admin) throws Exception {
  asMaster(post("/api/master/clinics").contentType(MediaType.APPLICATION_JSON).content("""
   {"slug":"%s","name":"Cabinet %s","city":"Nantes","address":"1 rue du Port","color":"#123456","adminEmail":"%s"}""".formatted(slug,slug,admin)))
   .andExpect(status().isCreated());
  Long clinic=jdbc.queryForObject("select id from clinic where slug=?",Long.class,slug);
  Long pro=jdbc.queryForObject("insert into professional(clinic_id,name,specialty,city,address,bio,fee) values (?,'Dr Test "+slug+"','general','Nantes','1 rue du Port','',30) returning id",Long.class,clinic);
  return jdbc.queryForObject("insert into slot(clinic_id,professional_id,starts_at,ends_at) values (?,?,now()+interval '3 days',now()+interval '3 days 30 minutes') returning id",Long.class,clinic,pro);
 }
 private ResultActions update(String slug,String json) throws Exception {
  return asMaster(put("/api/master/clinics/"+slug).contentType(MediaType.APPLICATION_JSON).content(json));
 }

 @Test void leMasterModifieUnCabinetEtLeChangementEstJournalise() throws Exception {
  clinicWithSlot("cabinet-modif","autre@medislot.test");
  update("cabinet-modif","""
   {"name":"Centre du Port","city":"Nantes","address":"2 quai de la Fosse","color":"#B45309"}""")
   .andExpect(status().isOk()).andExpect(jsonPath("$.slug").value("cabinet-modif")).andExpect(jsonPath("$.name").value("Centre du Port"))
   .andExpect(jsonPath("$.address").value("2 quai de la Fosse")).andExpect(jsonPath("$.color").value("#b45309")).andExpect(jsonPath("$.professionals").value(1));
  mvc.perform(get("/api/clinic").header("X-Clinic","cabinet-modif")).andExpect(jsonPath("$.name").value("Centre du Port"));
  asMaster(get("/api/master/activity").param("clinic","cabinet-modif").param("action","CLINIC_UPDATED"))
   .andExpect(jsonPath("$.entries",hasSize(1)))
   .andExpect(jsonPath("$.entries[0].detail").value("nom : Cabinet cabinet-modif → Centre du Port · adresse : 1 rue du Port → 2 quai de la Fosse · couleur : #123456 → #b45309"))
   .andExpect(jsonPath("$.entries[0].actorEmail").value(MASTER));
  // sans changement : rien de journalisé
  update("cabinet-modif","""
   {"name":"Centre du Port","city":"Nantes","address":"2 quai de la Fosse","color":"#b45309"}""").andExpect(status().isOk());
  asMaster(get("/api/master/activity").param("clinic","cabinet-modif").param("action","CLINIC_UPDATED")).andExpect(jsonPath("$.entries",hasSize(1)));
 }

 @Test void uneModificationInvalideOuSurUnCabinetInconnuEstRefusee() throws Exception {
  update("cabinet-a","""
   {"name":"","city":"Paris","address":" ","color":"bleu"}""").andExpect(status().isBadRequest())
   .andExpect(jsonPath("$.errors[*].field",hasItems("name","address","color")));
  update("cabinet-fantome","""
   {"name":"X","city":"Paris","address":"1 rue","color":"#000000"}""").andExpect(status().isNotFound());
 }

 @Test void seulLeMasterModifieOuSupprime() throws Exception {
  String json="""
   {"name":"Pirate","city":"Paris","address":"1 rue","color":"#000000"}""";
  mvc.perform(put("/api/master/clinics/cabinet-a").with(user("admin-a@medislot.test").roles("PATIENT")).with(csrf()).contentType(MediaType.APPLICATION_JSON).content(json)).andExpect(status().isForbidden());
  mvc.perform(delete("/api/master/clinics/cabinet-a").with(user("admin-a@medislot.test").roles("PATIENT")).with(csrf())).andExpect(status().isForbidden());
  mvc.perform(delete("/api/master/clinics/cabinet-a").with(csrf())).andExpect(status().isUnauthorized());
  assertEquals("Cabinet des Tilleuls",jdbc.queryForObject("select name from clinic where slug='cabinet-a'",String.class));
 }

 @Test void unCabinetAvecDesRendezVousAVenirNeSeSupprimePas() throws Exception {
  long slot=clinicWithSlot("cabinet-occupe","admin-b@medislot.test");
  Long patient=jdbc.queryForObject("select id from app_user where email='patient@medislot.test'",Long.class);
  jdbc.update("insert into appointment(clinic_id,patient_id,slot_id,status,created_at) select clinic_id,?,id,'CONFIRMED',now() from slot where id=?",patient,slot);
  asMaster(delete("/api/master/clinics/cabinet-occupe")).andExpect(status().isConflict())
   .andExpect(jsonPath("$.detail").value("Ce cabinet a encore 1 rendez-vous à venir. Il doit être annulé avant la suppression."));
  mvc.perform(get("/api/clinic").header("X-Clinic","cabinet-occupe")).andExpect(status().isOk());
  jdbc.update("update appointment set status='CANCELLED' where slot_id=?",slot);
  asMaster(delete("/api/master/clinics/cabinet-occupe")).andExpect(status().isNoContent());
 }

 @Test void unCabinetSupprimeDisparaitPartoutMaisResteDansLHistorique() throws Exception {
  // compte neuf, patient de cabinet-a, qui devient admin du cabinet supprimé
  mvc.perform(post("/api/register").header("X-Clinic","cabinet-a").with(csrf()).contentType(MediaType.APPLICATION_JSON)
   .content("{\"email\":\"ancien-admin@medislot.test\",\"password\":\"une phrase assez longue\",\"name\":\"Ancien Admin\"}")).andExpect(status().isCreated());
  long slot=clinicWithSlot("cabinet-ferme","ancien-admin@medislot.test");
  asMaster(delete("/api/master/clinics/cabinet-ferme")).andExpect(status().isNoContent());
  // plus de page, plus de liste, plus d'annuaire
  mvc.perform(get("/api/clinic").header("X-Clinic","cabinet-ferme")).andExpect(status().isNotFound());
  mvc.perform(get("/api/clinics")).andExpect(jsonPath("$[*].slug",not(hasItem("cabinet-ferme"))));
  asMaster(get("/api/master/clinics")).andExpect(jsonPath("$[*].slug",not(hasItem("cabinet-ferme"))));
  mvc.perform(get("/api/directory").param("q","Dr Test cabinet-ferme")).andExpect(jsonPath("$.total").value(0)).andExpect(jsonPath("$.items",empty()));
  asMaster(get("/api/master/statistics")).andExpect(jsonPath("$.clinicSummaries[*].slug",not(hasItem("cabinet-ferme"))));
  // l'ancien admin n'en est plus membre, et reste un simple patient ailleurs
  mvc.perform(get("/api/me/clinics").with(user("ancien-admin@medislot.test").roles("PATIENT"))).andExpect(jsonPath("$[*].slug",not(hasItem("cabinet-ferme"))));
  mvc.perform(get("/api/clinic").header("X-Clinic","cabinet-a").with(user("ancien-admin@medislot.test").roles("PATIENT"))).andExpect(status().isOk());
  // l'identifiant reste pris, et le cabinet ne se supprime ni ne se modifie deux fois
  asMaster(post("/api/master/clinics").contentType(MediaType.APPLICATION_JSON).content("""
   {"slug":"cabinet-ferme","name":"Nouveau","city":"Nantes","address":"1 rue","color":"#123456"}""")).andExpect(status().isConflict())
   .andExpect(jsonPath("$.detail").value("Cet identifiant a appartenu à un cabinet supprimé : choisissez-en un autre."));
  asMaster(delete("/api/master/clinics/cabinet-ferme")).andExpect(status().isNotFound());
  update("cabinet-ferme","""
   {"name":"X","city":"Paris","address":"1 rue","color":"#000000"}""").andExpect(status().isNotFound());
  // l'historique garde la trace
  asMaster(get("/api/master/activity").param("action","CLINIC_DELETED"))
   .andExpect(jsonPath("$.entries[*].detail",hasItem("Cabinet cabinet-ferme · Nantes · 1 membre retiré")))
   .andExpect(jsonPath("$.entries[*].clinicSlug",hasItem("cabinet-ferme")));
  // une réservation qui arrive après la suppression est refusée
  TenantContext.set(jdbc.queryForObject("select id from clinic where slug='cabinet-ferme'",Long.class));
  try{
   var refused=assertThrows(ResponseStatusException.class,()->booking.book("patient@medislot.test",slot));
   assertEquals(404,refused.getStatusCode().value());
  }finally{TenantContext.clear();}
 }

 @Test void lApplicationNePeutNiEffacerUnCabinetNiChangerSonIdentifiant() {
  assertFalse(jdbc.queryForObject("select has_table_privilege('medislot_app','clinic','DELETE')",Boolean.class));
  assertFalse(jdbc.queryForObject("select has_column_privilege('medislot_app','clinic','slug','UPDATE')",Boolean.class));
  assertTrue(jdbc.queryForObject("select has_column_privilege('medislot_app','clinic','deleted_at','UPDATE')",Boolean.class));
 }
}
