package fr.medislot.api;
import fr.medislot.tenant.TenantContext;
import java.time.Instant;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;
/**
 * Tableau de bord d'un cabinet, réservé à ses admins (ROLE_CLINIC_ADMIN, posé par TenantFilter) et aux MASTER.
 * Requêtes JDBC : chacune filtre sur le cabinet du contexte, jamais sur un identifiant reçu du client.
 */
@RestController @RequestMapping("/api/admin")
public class AdminController {
 private final JdbcTemplate jdbc;
 public AdminController(JdbcTemplate jdbc){this.jdbc=jdbc;}
 public record Overview(long professionals,long upcoming,long members,long admins){}
 public record MemberDto(Long id,String name,String email,String role){}
 public record AppointmentDto(Long id,Instant startsAt,String professionalName,String specialty,String patientName,String status){}

 @GetMapping("/overview") public Overview overview(){
  return jdbc.queryForObject("""
   select (select count(*) from professional where clinic_id=?) professionals,
    (select count(*) from appointment a join slot s on s.id=a.slot_id where a.clinic_id=? and a.status='CONFIRMED' and s.starts_at>=now()) upcoming,
    (select count(*) from membership where clinic_id=?) members,
    (select count(*) from membership where clinic_id=? and role='ADMIN') admins""",
   (rs,i)->new Overview(rs.getLong("professionals"),rs.getLong("upcoming"),rs.getLong("members"),rs.getLong("admins")),
   clinic(),clinic(),clinic(),clinic());
 }

 @GetMapping("/members") public List<MemberDto> members(){
  return jdbc.query("select u.id,u.name,u.email,m.role from membership m join app_user u on u.id=m.user_id where m.clinic_id=? order by m.role,u.name",
   (rs,i)->new MemberDto(rs.getLong("id"),rs.getString("name"),rs.getString("email"),rs.getString("role")),clinic());
 }

 /** Rendez-vous à venir du cabinet, les 50 prochains. */
 @GetMapping("/appointments") public List<AppointmentDto> appointments(){
  return jdbc.query("""
   select a.id,s.starts_at,p.name professional,p.specialty,u.name patient,a.status
   from appointment a join slot s on s.id=a.slot_id join professional p on p.id=s.professional_id join app_user u on u.id=a.patient_id
   where a.clinic_id=? and s.starts_at>=now() order by s.starts_at limit 50""",
   (rs,i)->new AppointmentDto(rs.getLong("id"),rs.getTimestamp("starts_at").toInstant(),rs.getString("professional"),rs.getString("specialty"),rs.getString("patient"),rs.getString("status")),
   clinic());
 }

 private static Long clinic(){return TenantContext.require();}
}
