package fr.medislot.api;
import fr.medislot.repository.UserRepository;
import java.security.Principal;
import java.time.Instant;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
/**
 * Lectures volontairement inter-cabinets, pour un visiteur ou un patient qui voit tous les cabinets ensemble.
 * Elles ne passent pas par TenantFilter (pas d'en-tête X-Clinic) et restent bornées :
 * l'annuaire ne renvoie que des données publiques, les rendez-vous et notifications que ceux du patient connecté.
 * Toute écriture (réserver, déplacer, annuler) reste faite dans le cabinet du rendez-vous, avec X-Clinic.
 */
@RestController @RequestMapping("/api")
public class CrossClinicController {
 private final JdbcTemplate jdbc; private final UserRepository users;
 public CrossClinicController(JdbcTemplate jdbc,UserRepository users){this.jdbc=jdbc;this.users=users;}
 public record DirectoryEntry(Long id,String name,String specialty,String city,String address,String bio,int fee,Double latitude,Double longitude,String clinicSlug,String clinicName){}
 public record MyAppointment(Long id,Long slotId,Long professionalId,String professionalName,String specialty,String address,String city,Instant startsAt,String status,String clinicSlug,String clinicName){}
 private static final String FILTER=" where (?='' or p.specialty=?) and (?='' or lower(p.city) like lower('%'||?||'%')) and (?='' or lower(p.name) like lower('%'||?||'%'))";

 /** Annuaire de tous les cabinets, mêmes filtres et même pagination que /api/professionals. */
 @GetMapping("/directory") public DirectoryController.PageDto<DirectoryEntry> directory(@RequestParam(defaultValue="") String specialty,@RequestParam(defaultValue="") String city,@RequestParam(defaultValue="") String q,@RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="6") int size){
  if(page<0||size<1||size>50||city.length()>80||q.length()>120||specialty.length()>60)throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Paramètres de recherche invalides.");
  Object[] f={specialty,specialty,city,city,q,q};
  Long total=jdbc.queryForObject("select count(*) from professional p"+FILTER,Long.class,f);
  List<DirectoryEntry> items=jdbc.query("select p.*,c.slug clinic_slug,c.name clinic_name from professional p join clinic c on c.id=p.clinic_id"+FILTER+" order by p.name,p.id limit ? offset ?",
   (rs,i)->new DirectoryEntry(rs.getLong("id"),rs.getString("name"),rs.getString("specialty"),rs.getString("city"),rs.getString("address"),rs.getString("bio"),rs.getInt("fee"),
    (Double)rs.getObject("latitude"),(Double)rs.getObject("longitude"),rs.getString("clinic_slug"),rs.getString("clinic_name")),
   specialty,specialty,city,city,q,q,size,(long)page*size);
  return new DirectoryController.PageDto<>(items,page,size,total==null?0:total);
 }

 /** Rendez-vous du patient connecté, tous cabinets confondus. */
 @GetMapping("/me/appointments") public List<MyAppointment> appointments(Principal principal){
  return jdbc.query("""
   select a.id,a.slot_id,s.professional_id,p.name,p.specialty,p.address,p.city,s.starts_at,a.status,c.slug,c.name clinic_name
   from appointment a join slot s on s.id=a.slot_id join professional p on p.id=s.professional_id join clinic c on c.id=a.clinic_id
   where a.patient_id=? order by a.created_at desc""",
   (rs,i)->new MyAppointment(rs.getLong("id"),rs.getLong("slot_id"),rs.getLong("professional_id"),rs.getString("name"),rs.getString("specialty"),rs.getString("address"),rs.getString("city"),
    rs.getTimestamp("starts_at").toInstant(),rs.getString("status"),rs.getString("slug"),rs.getString("clinic_name")),patient(principal));
 }

 /** 30 dernières notifications du patient connecté, tous cabinets confondus. */
 @GetMapping("/me/notifications") public List<AppointmentController.NotificationDto> notifications(Principal principal){
  return jdbc.query("select id,message,created_at from notification where patient_id=? order by created_at desc limit 30",
   (rs,i)->new AppointmentController.NotificationDto(rs.getLong("id"),rs.getString("message"),rs.getTimestamp("created_at").toInstant()),patient(principal));
 }

 private Long patient(Principal principal){return users.findByEmail(principal.getName()).orElseThrow(()->new ResponseStatusException(HttpStatus.UNAUTHORIZED,"Connexion requise.")).id;}
}
