package fr.medislot.api;
import fr.medislot.tenant.TenantContext;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.util.Locale;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import java.time.Instant;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;
@RestController @RequestMapping("/api/admin")
public class AdminController {
 private final JdbcTemplate jdbc;
 public AdminController(JdbcTemplate jdbc){this.jdbc=jdbc;}
 public record Overview(long professionals,long upcoming,long members,long admins){}
 public record MemberDto(Long id,String name,String email,String role){}
 /** Ajout d'un compte existant au cabinet courant ; role MEMBER (défaut) ou ADMIN. */
 public record NewMember(
  @NotBlank(message="Saisissez l'adresse e-mail du compte.") @Email(message="Adresse e-mail attendue.") @Size(max=254,message="254 caractères au plus.") String email,
  @Pattern(regexp="MEMBER|ADMIN",message="Rôle attendu : MEMBER ou ADMIN.") String role){}
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

 @PostMapping("/members") @ResponseStatus(HttpStatus.CREATED) @Transactional
 public MemberDto addMember(@Valid @RequestBody NewMember in){
  String email=in.email().strip().toLowerCase(Locale.ROOT);
  String role=in.role()==null?"MEMBER":in.role();
  var user=jdbc.query("select id,name,role from app_user where email=?",(rs,i)->new Object[]{rs.getLong("id"),rs.getString("name"),rs.getString("role")},email)
   .stream().findFirst().orElseThrow(()->new ResponseStatusException(HttpStatus.NOT_FOUND,"Aucun compte avec cette adresse. La personne doit d'abord créer son compte."));
  if("MASTER".equals(user[2]))throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Un compte master a déjà accès à tous les cabinets.");
  int added=jdbc.update("insert into membership(user_id,clinic_id,role) values (?,?,?) on conflict do nothing",user[0],clinic(),role);
  if(added==0)throw new ResponseStatusException(HttpStatus.CONFLICT,"Ce compte est déjà membre du cabinet.");
  return new MemberDto((Long)user[0],(String)user[1],email,role);
 }

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
