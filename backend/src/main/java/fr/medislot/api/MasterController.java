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
import org.springframework.http.ResponseEntity;
import java.security.Principal;
import java.util.ArrayList;
import java.util.Objects;
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

 /** Modification d'un cabinet : tout sauf l'identifiant, qui sert dans les adresses /c/<slug>/ déjà partagées. */
 public record ClinicChanges(
  @NotBlank(message="Saisissez le nom du cabinet.") @Size(max=120,message="120 caractères au plus.") String name,
  @NotBlank(message="Saisissez la ville.") @Size(max=80,message="80 caractères au plus.") String city,
  @NotBlank(message="Saisissez l'adresse postale.") @Size(max=200,message="200 caractères au plus.") String address,
  @NotBlank(message="Choisissez une couleur.") @Pattern(regexp="^#[0-9a-fA-F]{6}$",message="Couleur attendue au format #rrggbb.") String color){}
 private record Current(long id,String name,String city,String address,String color){}

 /** Comptes proposés comme admin d'un nouveau cabinet (liste déroulante) : tous sauf les masters. */
 public record Account(String email,String name){}
 @GetMapping("/accounts") public List<Account> accounts(){
  return jdbc.query("select email,name from app_user where role<>'MASTER' order by name,email",(rs,i)->new Account(rs.getString("email"),rs.getString("name")));
 }

 @GetMapping("/clinics") public List<ClinicStats> clinics(){
  return jdbc.query(STATS+" where c.deleted_at is null order by c.slug",(rs,i)->stats(rs));
 }

 @PostMapping("/clinics") @ResponseStatus(HttpStatus.CREATED) @Transactional
 public ClinicStats create(@Valid @RequestBody NewClinic in,java.security.Principal principal){
  String slug=in.slug().strip();
  Boolean deleted=jdbc.query("select deleted_at is not null from clinic where slug=?",(rs,i)->rs.getBoolean(1),slug).stream().findFirst().orElse(null);
  if(deleted!=null)throw new ResponseStatusException(HttpStatus.CONFLICT,deleted
   ?"Cet identifiant a appartenu à un cabinet supprimé : choisissez-en un autre."
   :"Cet identifiant est déjà pris par un autre cabinet.");
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

 @PutMapping("/clinics/{slug}") @Transactional
 public ClinicStats update(@PathVariable String slug,@Valid @RequestBody ClinicChanges in,Principal principal){
  Current before=lock(slug);
  String name=in.name().strip(),city=in.city().strip(),address=in.address().strip(),color=in.color().toLowerCase(Locale.ROOT);
  List<String> changes=new ArrayList<>();
  if(!name.equals(before.name()))changes.add("nom : "+before.name()+" → "+name);
  if(!city.equals(before.city()))changes.add("ville : "+before.city()+" → "+city);
  if(!Objects.equals(address,before.address()))changes.add("adresse : "+(before.address()==null?"—":before.address())+" → "+address);
  if(!color.equals(before.color()))changes.add("couleur : "+before.color()+" → "+color);
  if(!changes.isEmpty()){
   jdbc.update("update clinic set name=?,city=?,address=?,color=? where id=?",name,city,address,color,before.id());
   activityLog.recordByEmail(before.id(),principal.getName(),Activity.CLINIC_UPDATED,String.join(" · ",changes));
  }
  return jdbc.queryForObject(STATS+" where c.id=?",(rs,i)->stats(rs),before.id());
 }

 /**
  * Suppression (archivage, voir V15) : refusée tant qu'il reste des rendez-vous à venir.
  * Les créneaux à venir sont verrouillés d'abord : une réservation en cours attend, puis voit le cabinet supprimé.
  * Les adhésions sont retirées : un ancien admin n'est plus « gérant » d'un cabinet qui n'existe plus.
  */
 @DeleteMapping("/clinics/{slug}") @Transactional
 public ResponseEntity<Void> delete(@PathVariable String slug,Principal principal){
  Current clinic=lock(slug);
  jdbc.queryForList("select id from slot where clinic_id=? and starts_at>=now() for update",clinic.id());
  Long upcoming=jdbc.queryForObject("select count(*) from appointment a join slot s on s.id=a.slot_id where a.clinic_id=? and a.status='CONFIRMED' and s.starts_at>=now()",Long.class,clinic.id());
  if(upcoming!=null&&upcoming>0)throw new ResponseStatusException(HttpStatus.CONFLICT,
   "Ce cabinet a encore "+upcoming+(upcoming==1?" rendez-vous à venir. Il doit être annulé":" rendez-vous à venir. Ils doivent être annulés")+" avant la suppression.");
  int members=jdbc.update("delete from membership where clinic_id=?",clinic.id());
  jdbc.update("update clinic set deleted_at=now() where id=?",clinic.id());
  activityLog.recordByEmail(clinic.id(),principal.getName(),Activity.CLINIC_DELETED,clinic.name()+" · "+clinic.city()+" · "+members+(members>1?" membres retirés":" membre retiré"));
  return ResponseEntity.noContent().build();
 }

 /** Cabinet existant (non supprimé), verrouillé jusqu'à la fin de la transaction ; sinon 404. */
 private Current lock(String slug){
  return jdbc.query("select id,name,city,address,color from clinic where slug=? and deleted_at is null for update",
   (rs,i)->new Current(rs.getLong("id"),rs.getString("name"),rs.getString("city"),rs.getString("address"),rs.getString("color")),slug)
   .stream().findFirst().orElseThrow(()->new ResponseStatusException(HttpStatus.NOT_FOUND,"Cabinet introuvable."));
 }

 private static ClinicStats stats(java.sql.ResultSet rs) throws SQLException {
  return new ClinicStats(rs.getString("slug"),rs.getString("name"),rs.getString("city"),rs.getString("address"),rs.getString("color"),
   rs.getLong("professionals"),rs.getLong("upcoming"),rs.getLong("members"),strings(rs.getArray("admins")));
 }
 private static List<String> strings(Array array) throws SQLException {return List.of((String[])array.getArray());}
}
