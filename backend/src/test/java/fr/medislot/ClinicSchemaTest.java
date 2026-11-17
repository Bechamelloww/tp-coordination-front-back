package fr.medislot;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
class ClinicSchemaTest extends PostgresTestBase {
 @Autowired JdbcTemplate jdbc;
 private Long clinic(String slug){return jdbc.queryForObject("select id from clinic where slug=?",Long.class,slug);}
 private List<String> clinicsOf(String email){return jdbc.queryForList("select c.slug from membership m join clinic c on c.id=m.clinic_id join app_user u on u.id=m.user_id where u.email=? order by c.slug",String.class,email);}
 @Test void twoClinicsExist(){assertEquals(List.of("cabinet-a","cabinet-b"),jdbc.queryForList("select slug from clinic order by slug",String.class));}
 @Test void demoAccountsBelongToTheRightClinics(){
  assertEquals(List.of("cabinet-a","cabinet-b"),clinicsOf("patient@medislot.test"));
  assertEquals(List.of("cabinet-a"),clinicsOf("autre@medislot.test"));
 }
 @Test void eachClinicHasItsAdminAndRolesAreChecked(){
  assertEquals("ADMIN",jdbc.queryForObject("select m.role from membership m join app_user u on u.id=m.user_id where u.email='admin-b@medislot.test' and m.clinic_id=?",String.class,clinic("cabinet-b")));
  assertThrows(DataIntegrityViolationException.class,()->jdbc.update("update membership set role='OWNER' where clinic_id=?",clinic("cabinet-a")));
  assertThrows(DataIntegrityViolationException.class,()->jdbc.update("update app_user set role='ROOT' where email='autre@medislot.test'"));
 }
 @Test void bothClinicsHaveTheirOwnPractitionersAndSlots(){
  for(String slug:List.of("cabinet-a","cabinet-b")){
   assertTrue(jdbc.queryForObject("select count(*) from professional where clinic_id=?",Long.class,clinic(slug))>0,slug);
   assertTrue(jdbc.queryForObject("select count(*) from slot where clinic_id=?",Long.class,clinic(slug))>0,slug);
  }
 }
 @Test void slotCannotPointToAPractitionerOfAnotherClinic(){
  Long proA=jdbc.queryForObject("select id from professional where clinic_id=? limit 1",Long.class,clinic("cabinet-a"));
  assertThrows(DataIntegrityViolationException.class,()->jdbc.update("insert into slot(clinic_id,professional_id,starts_at,ends_at) values (?,?,now()+interval '300 days',now()+interval '300 days 30 minutes')",clinic("cabinet-b"),proA));
 }
 @Test void appointmentCannotPointToASlotOfAnotherClinic(){
  Long slotB=jdbc.queryForObject("select id from slot where clinic_id=? limit 1",Long.class,clinic("cabinet-b"));
  Long patient=jdbc.queryForObject("select id from app_user where email='patient@medislot.test'",Long.class);
  assertThrows(DataIntegrityViolationException.class,()->jdbc.update("insert into appointment(clinic_id,patient_id,slot_id,status,created_at) values (?,?,?,'CONFIRMED',now())",clinic("cabinet-a"),patient,slotB));
 }
 @Test void clinicSlugIsUnique(){assertThrows(DataIntegrityViolationException.class,()->jdbc.update("insert into clinic(slug,name,city,color) values ('cabinet-a','Doublon','Nice','#000000')"));}
 @Test void applicationRoleIsSubjectToRowLevelSecurity(){
  var role=jdbc.queryForMap("select rolsuper,rolbypassrls from pg_roles where rolname='medislot_app'");
  assertEquals(false,role.get("rolsuper"));assertEquals(false,role.get("rolbypassrls"));
 }
}
