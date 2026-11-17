package fr.medislot.audit;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Component;
/**
 * Journal d'activité des cabinets (table admin_log, en ajout seul) : rendez-vous pris, déplacés, annulés,
 * comptes créés ou modifiés, membres ajoutés, cabinets créés.
 * Chaque ligne appartient à un cabinet : ses admins la lisent, le master lit tout. Rien n'est exposé en public.
 */
@Component
public class AdminLog {
 public static final String CLINIC_CREATED="CLINIC_CREATED";
 public static final String MEMBER_ADDED="MEMBER_ADDED";
 public static final String MEMBER_JOINED="MEMBER_JOINED";
 public static final String ACCOUNT_CREATED="ACCOUNT_CREATED";
 public static final String ACCOUNT_GOOGLE_LINKED="ACCOUNT_GOOGLE_LINKED";
 public static final String APPOINTMENT_BOOKED="APPOINTMENT_BOOKED";
 public static final String APPOINTMENT_MOVED="APPOINTMENT_MOVED";
 public static final String APPOINTMENT_CANCELLED="APPOINTMENT_CANCELLED";
 private static final Map<String,String> SPECIALTIES=Map.of("general","Médecine générale","cardiology","Cardiologie","dermatology","Dermatologie","dentistry","Chirurgie dentaire");
 private static final DateTimeFormatter WHEN=DateTimeFormatter.ofPattern("EEEE d MMMM yyyy 'à' HH'h'mm",Locale.FRENCH).withZone(ZoneId.of("Europe/Paris"));
 public static final int PAGE=50;
 public record Entry(long id,Instant createdAt,String actor,String action,String detail,String clinicSlug,String clinicName){}
 private static final String SELECT="""
  select l.id,l.created_at,l.actor_email,l.action,l.detail,c.slug,c.name
  from admin_log l join clinic c on c.id=l.clinic_id""";
 private static final RowMapper<Entry> ROW=(rs,i)->new Entry(rs.getLong("id"),rs.getTimestamp("created_at").toInstant(),
  rs.getString("actor_email"),rs.getString("action"),rs.getString("detail"),rs.getString("slug"),rs.getString("name"));
 private final JdbcTemplate jdbc;
 public AdminLog(JdbcTemplate jdbc){this.jdbc=jdbc;}

 /** À appeler dans la transaction de l'action : si l'action échoue, la ligne n'est pas écrite. */
 public void record(long clinicId,String actorEmail,String action,String detail){
  jdbc.update("insert into admin_log(clinic_id,actor_email,action,detail) values (?,?,?,?)",
   clinicId,actorEmail,action,detail.length()>300?detail.substring(0,300):detail);
 }

 /** Événement d'un compte qui ne dépend d'aucun cabinet : une ligne dans chaque cabinet dont il est membre. */
 public void recordForUser(Long userId,String actorEmail,String action,String detail){
  jdbc.update("insert into admin_log(clinic_id,actor_email,action,detail) select clinic_id,?,?,? from membership where user_id=?",
   actorEmail,action,detail.length()>300?detail.substring(0,300):detail,userId);
 }

 /**
  * Rendez-vous pris, déplacé ou annulé, raconté en une phrase, par exemple
  * « Jeanne Patient a pris rendez-vous avec Dr Sarah Martin (Médecine générale) pour le mardi 18 novembre 2026 à 10h00 ».
  * oldSlotId : créneau d'avant pour un déplacement, sinon null.
  */
 public void appointment(String action,long appointmentId,Long oldSlotId){
  record Row(long clinic,String email,String patient,long slot){}
  Row r=jdbc.queryForObject("select a.clinic_id,u.email,u.name,a.slot_id from appointment a join app_user u on u.id=a.patient_id where a.id=?",
   (rs,i)->new Row(rs.getLong(1),rs.getString(2),rs.getString(3),rs.getLong(4)),appointmentId);
  String detail=switch(action){
   case APPOINTMENT_BOOKED->r.patient()+" a pris rendez-vous "+slot(r.slot());
   case APPOINTMENT_CANCELLED->r.patient()+" a annulé son rendez-vous "+slot(r.slot());
   case APPOINTMENT_MOVED->r.patient()+" a déplacé son rendez-vous "+slot(oldSlotId)+" : nouveau rendez-vous "+slot(r.slot());
   default->throw new IllegalArgumentException(action);
  };
  record(r.clinic(),r.email(),action,detail);
 }

 /** « avec Dr X (Spécialité) pour le mardi 18 novembre 2026 à 10h00 » */
 private String slot(long slotId){
  return jdbc.queryForObject("select p.name,p.specialty,s.starts_at from slot s join professional p on p.id=s.professional_id where s.id=?",
   (rs,i)->"avec "+rs.getString(1)+" ("+SPECIALTIES.getOrDefault(rs.getString(2),rs.getString(2))+") pour le "+WHEN.format(rs.getTimestamp(3).toInstant()),slotId);
 }

 /** Les plus récentes d'abord ; before (id) pour la page suivante. */
 public List<Entry> ofClinic(long clinicId,Long before){
  return before==null
   ?jdbc.query(SELECT+" where l.clinic_id=? order by l.id desc limit "+PAGE,ROW,clinicId)
   :jdbc.query(SELECT+" where l.clinic_id=? and l.id<? order by l.id desc limit "+PAGE,ROW,clinicId,before);
 }

 public List<Entry> all(Long before){
  return before==null
   ?jdbc.query(SELECT+" order by l.id desc limit "+PAGE,ROW)
   :jdbc.query(SELECT+" where l.id<? order by l.id desc limit "+PAGE,ROW,before);
 }
}
