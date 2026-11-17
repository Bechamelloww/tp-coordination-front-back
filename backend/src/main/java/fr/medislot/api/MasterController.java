package fr.medislot.api;
import fr.medislot.activity.Activity;
import fr.medislot.activity.ActivityLog;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.sql.Array;
import java.sql.SQLException;
import java.util.List;
import java.util.Locale;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
@RestController @RequestMapping("/api/master")
public class MasterController {
 private static final String STATS = """
  select c.slug,c.name,c.city,c.address,c.color,
   (select count(*) from professional p where p.clinic_id=c.id) professionals,
   (select count(*) from appointment a join slot s on s.id=a.slot_id where a.clinic_id=c.id and a.status='CONFIRMED' and s.starts_at>=now()) upcoming,
   (select count(*) from membership m where m.clinic_id=c.id) members,
   array(select u.email from membership m join app_user u on u.id=m.user_id where m.clinic_id=c.id and m.role='ADMIN' order by u.email) admins
  from clinic c""";
 private final JdbcTemplate jdbc; private final ActivityLog activityLog;
 public MasterController(JdbcTemplate jdbc,ActivityLog activityLog){this.jdbc=jdbc;this.activityLog=activityLog;}
 public record ClinicStats(String slug,String name,String city,String address,String color,long professionals,long upcoming,long members,List<String> admins){}
 /** Nouveau cabinet. adminEmail, facultatif : un compte existant qui devient admin du cabinet. */
 public record NewClinic(
  @NotBlank(message="Saisissez un identifiant.") @Size(max=40,message="40 caractères au plus.")
  @Pattern(regexp="^[a-z0-9]+(-[a-z0-9]+)*$",message="Lettres minuscules, chiffres et tirets, par exemple cabinet-c.") String slug,
  @NotBlank(message="Saisissez le nom du cabinet.") @Size(max=120,message="120 caractères au plus.") String name,
  @NotBlank(message="Saisissez la ville.") @Size(max=80,message="80 caractères au plus.") String city,
  @NotBlank(message="Saisissez l'adresse postale.") @Size(max=200,message="200 caractères au plus.") String address,
  @NotBlank(message="Choisissez une couleur.") @Pattern(regexp="^#[0-9a-fA-F]{6}$",message="Couleur attendue au format #rrggbb.") String color,
  @Email(message="Adresse e-mail attendue.") @Size(max=254,message="254 caractères au plus.") String adminEmail){}

 /** Comptes proposés comme admin d'un nouveau cabinet (liste déroulante) : tous sauf les masters. */
 public record Account(String email,String name){}
 @GetMapping("/accounts") public List<Account> accounts(){
  return jdbc.query("select email,name from app_user where role<>'MASTER' order by name,email",(rs,i)->new Account(rs.getString("email"),rs.getString("name")));
 }

 @GetMapping("/clinics") public List<ClinicStats> clinics(){
  return jdbc.query(STATS+" order by c.slug",(rs,i)->stats(rs));
 }

 @PostMapping("/clinics") @ResponseStatus(HttpStatus.CREATED) @Transactional
 public ClinicStats create(@Valid @RequestBody NewClinic in,java.security.Principal principal){
  String slug=in.slug().strip();
  if(Boolean.TRUE.equals(jdbc.queryForObject("select exists(select 1 from clinic where slug=?)",Boolean.class,slug)))
   throw new ResponseStatusException(HttpStatus.CONFLICT,"Cet identifiant est déjà pris par un autre cabinet.");
  Long admin=null;
  if(in.adminEmail()!=null&&!in.adminEmail().isBlank()){
   String email=in.adminEmail().strip().toLowerCase(Locale.ROOT);
   admin=jdbc.query("select id from app_user where email=?",(rs,i)->rs.getLong(1),email).stream().findFirst()
    .orElseThrow(()->new ResponseStatusException(HttpStatus.BAD_REQUEST,"Aucun compte avec cette adresse : créez-le d'abord."));
  }
  Long id=jdbc.queryForObject("insert into clinic(slug,name,city,address,color) values (?,?,?,?,?) returning id",Long.class,
   slug,in.name().strip(),in.city().strip(),in.address().strip(),in.color().toLowerCase(Locale.ROOT));
  if(admin!=null)jdbc.update("insert into membership(user_id,clinic_id,role) values (?,?,'ADMIN')",admin,id);
  activityLog.recordByEmail(id,principal.getName(),Activity.CLINIC_CREATED,in.name().strip()+" · "+in.city().strip()+(admin!=null?" · admin "+in.adminEmail().strip().toLowerCase(Locale.ROOT):""));
  return jdbc.queryForObject(STATS+" where c.id=?",(rs,i)->stats(rs),id);
 }

 private static ClinicStats stats(java.sql.ResultSet rs) throws SQLException {
  return new ClinicStats(rs.getString("slug"),rs.getString("name"),rs.getString("city"),rs.getString("address"),rs.getString("color"),
   rs.getLong("professionals"),rs.getLong("upcoming"),rs.getLong("members"),strings(rs.getArray("admins")));
 }
 private static List<String> strings(Array array) throws SQLException {return List.of((String[])array.getArray());}
}
