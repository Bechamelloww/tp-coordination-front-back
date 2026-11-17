package fr.medislot.audit;
import java.time.Instant;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Component;
/**
 * Journal des actions d'administration (table admin_log, en ajout seul).
 * Chaque ligne appartient à un cabinet : ses admins la lisent, le master lit tout. Rien n'est exposé en public.
 */
@Component
public class AdminLog {
 public static final String CLINIC_CREATED="CLINIC_CREATED";
 public static final String MEMBER_ADDED="MEMBER_ADDED";
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
