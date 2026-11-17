package fr.medislot;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
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
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
@SpringBootTest @Testcontainers
class StatisticsIntegrationTest {
 @Container static PostgreSQLContainer db=new PostgreSQLContainer("postgres:18");
 @DynamicPropertySource static void database(DynamicPropertyRegistry r){r.add("spring.datasource.url",db::getJdbcUrl);r.add("spring.datasource.username",db::getUsername);r.add("spring.datasource.password",db::getPassword);r.add("medislot.demo-seed",()->"true");r.add("medislot.demo-password",()->java.util.UUID.randomUUID().toString());}
 @Autowired WebApplicationContext context;
 @Autowired JdbcTemplate jdbc;
 MockMvc mvc;
 static boolean seeded;

 @BeforeEach void setUp(){
  mvc=MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
  if(seeded)return;
  book("cabinet-a","Dr Sarah Martin","patient@medislot.test","CONFIRMED",0);
  book("cabinet-a","Dr Sarah Martin","autre@medislot.test","CANCELLED",1);
  book("cabinet-a","Dr Thomas Leroy","patient@medislot.test","CONFIRMED",2);
  book("cabinet-b","Dr Camille Perrin","patient@medislot.test","CONFIRMED",0);
  seeded=true;
 }

 private void book(String clinic,String professional,String patient,String status,int slotRank){
  jdbc.update("""
   insert into appointment(clinic_id,patient_id,slot_id,status,created_at)
   select s.clinic_id,u.id,s.id,?,now() from slot s join professional p on p.id=s.professional_id join clinic c on c.id=s.clinic_id,app_user u
   where c.slug=? and p.name=? and u.email=? and s.starts_at>now() order by s.starts_at offset ? limit 1""",status,clinic,professional,patient,slotRank);
 }

 private ResultActions call(String path,String clinic,String email,String role) throws Exception {
  var request=get(path).with(user(email).roles(role));
  if(clinic!=null)request.header("X-Clinic",clinic);
  return mvc.perform(request);
 }

 @Test void leDirecteurVoitLesStatistiquesDeSonCabinet() throws Exception {
  call("/api/admin/statistics","cabinet-a","admin-a@medislot.test","PATIENT")
   .andExpect(status().isOk())
   .andExpect(jsonPath("$.slug").value("cabinet-a"))
   .andExpect(jsonPath("$.summary.total").value(3))
   .andExpect(jsonPath("$.summary.confirmed").value(2))
   .andExpect(jsonPath("$.summary.cancelled").value(1))
   .andExpect(jsonPath("$.summary.upcoming").value(2))
   .andExpect(jsonPath("$.summary.patients").value(2))
   .andExpect(jsonPath("$.summary.lastThirtyDays").value(3))
   .andExpect(jsonPath("$.summary.upcomingSlots").value(greaterThan(2)))
   .andExpect(jsonPath("$.professionals[0].name").value("Dr Sarah Martin"))
   .andExpect(jsonPath("$.professionals[0].total").value(2))
   .andExpect(jsonPath("$.professionals[0].cancelled").value(1))
   .andExpect(jsonPath("$.professionals[?(@.name=='Dr Thomas Leroy')].total").value(contains(1)))
   .andExpect(jsonPath("$.professionals[*].name",not(hasItem("Dr Camille Perrin"))));
 }

 @Test void leDirecteurNeVoitPasUnAutreCabinet() throws Exception {
  call("/api/admin/statistics","cabinet-b","admin-a@medislot.test","PATIENT").andExpect(status().isNotFound());
  call("/api/admin/statistics","cabinet-a","patient@medislot.test","PATIENT").andExpect(status().isForbidden());
  call("/api/master/statistics",null,"admin-a@medislot.test","PATIENT").andExpect(status().isForbidden());
 }

 @Test void leMasterVoitTousLesCabinets() throws Exception {
  call("/api/master/statistics",null,"master@medislot.test","MASTER")
   .andExpect(status().isOk())
   .andExpect(jsonPath("$.clinics").value(2))
   .andExpect(jsonPath("$.summary.total").value(4))
   .andExpect(jsonPath("$.summary.patients").value(2))
   .andExpect(jsonPath("$.clinicSummaries[0].slug").value("cabinet-a"))
   .andExpect(jsonPath("$.clinicSummaries[0].summary.total").value(3))
   .andExpect(jsonPath("$.clinicSummaries[1].summary.total").value(1));
 }

 @Test void leMasterOuvreLeDetailDUnCabinet() throws Exception {
  call("/api/master/statistics/cabinet-b",null,"master@medislot.test","MASTER")
   .andExpect(status().isOk())
   .andExpect(jsonPath("$.summary.total").value(1))
   .andExpect(jsonPath("$.professionals[0].name").value("Dr Camille Perrin"));
  call("/api/master/statistics/inconnu",null,"master@medislot.test","MASTER").andExpect(status().isNotFound());
 }
}
