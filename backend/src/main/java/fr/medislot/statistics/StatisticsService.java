package fr.medislot.statistics;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
@Service
public class StatisticsService {
 private static final String APPOINTMENT_COUNTS = """
  count(a.id) total,
  count(a.id) filter (where a.status='CONFIRMED') confirmed,
  count(a.id) filter (where a.status='CANCELLED') cancelled,
  count(a.id) filter (where a.status='CONFIRMED' and s.starts_at>=now()) upcoming""";
 private static final String SUMMARY_COUNTS = APPOINTMENT_COUNTS + """
  ,
  count(distinct a.patient_id) patients,
  count(a.id) filter (where a.created_at>=now()-interval '30 days') last_thirty_days,
  count(a.id) filter (where a.created_at>=now()-interval '60 days' and a.created_at<now()-interval '30 days') previous_thirty_days""";
 private static final String UPCOMING_SLOTS = "select count(*) from slot where starts_at>=now()";

 public record Summary(long total,long confirmed,long cancelled,long upcoming,long patients,long lastThirtyDays,long previousThirtyDays,long upcomingSlots){}
 public record ProfessionalStatistics(Long id,String name,String specialty,long total,long confirmed,long cancelled,long upcoming){}
 public record ClinicStatistics(String slug,String name,String city,String color,Summary summary,List<ProfessionalStatistics> professionals){}
 public record ClinicSummary(String slug,String name,String city,String color,long professionals,Summary summary){}
 public record NetworkStatistics(long clinics,long professionals,Summary summary,List<ClinicSummary> clinicSummaries){}

 private final JdbcTemplate jdbc;
 public StatisticsService(JdbcTemplate jdbc){this.jdbc=jdbc;}

 public Optional<ClinicStatistics> forClinic(String slug){
  return jdbc.query("select id,slug,name,city,color from clinic where slug=? and deleted_at is null",(rs,i)->forClinic(rs.getLong("id"),rs.getString("slug"),rs.getString("name"),rs.getString("city"),rs.getString("color")),slug)
   .stream().findFirst();
 }

 public ClinicStatistics forClinic(Long clinicId){
  return jdbc.queryForObject("select slug,name,city,color from clinic where id=?",(rs,i)->forClinic(clinicId,rs.getString("slug"),rs.getString("name"),rs.getString("city"),rs.getString("color")),clinicId);
 }

 public NetworkStatistics forNetwork(){
  Summary summary=jdbc.queryForObject("select "+SUMMARY_COUNTS+",("+UPCOMING_SLOTS+" and clinic_id in (select id from clinic where deleted_at is null)) upcoming_slots from appointment a join slot s on s.id=a.slot_id join clinic c on c.id=a.clinic_id and c.deleted_at is null",(rs,i)->summary(rs));
  List<ClinicSummary> clinics=jdbc.query("select c.slug,c.name,c.city,c.color,"
   +"(select count(*) from professional p where p.clinic_id=c.id) professionals,"
   +SUMMARY_COUNTS+",("+UPCOMING_SLOTS+" and clinic_id=c.id) upcoming_slots "
   +"from clinic c left join appointment a on a.clinic_id=c.id left join slot s on s.id=a.slot_id "
   +"where c.deleted_at is null group by c.id order by total desc,c.name",
   (rs,i)->new ClinicSummary(rs.getString("slug"),rs.getString("name"),rs.getString("city"),rs.getString("color"),rs.getLong("professionals"),summary(rs)));
  long professionals=clinics.stream().mapToLong(ClinicSummary::professionals).sum();
  return new NetworkStatistics(clinics.size(),professionals,summary,clinics);
 }

 private ClinicStatistics forClinic(Long clinicId,String slug,String name,String city,String color){
  Summary summary=jdbc.queryForObject("select "+SUMMARY_COUNTS+",("+UPCOMING_SLOTS+" and clinic_id=?) upcoming_slots from appointment a join slot s on s.id=a.slot_id where a.clinic_id=?",
   (rs,i)->summary(rs),clinicId,clinicId);
  List<ProfessionalStatistics> professionals=jdbc.query("select p.id,p.name,p.specialty,"+APPOINTMENT_COUNTS
   +" from professional p left join slot s on s.professional_id=p.id left join appointment a on a.slot_id=s.id"
   +" where p.clinic_id=? group by p.id order by total desc,p.name",
   (rs,i)->new ProfessionalStatistics(rs.getLong("id"),rs.getString("name"),rs.getString("specialty"),rs.getLong("total"),rs.getLong("confirmed"),rs.getLong("cancelled"),rs.getLong("upcoming")),
   clinicId);
  return new ClinicStatistics(slug,name,city,color,summary,professionals);
 }

 private static Summary summary(ResultSet rs) throws SQLException {
  return new Summary(rs.getLong("total"),rs.getLong("confirmed"),rs.getLong("cancelled"),rs.getLong("upcoming"),rs.getLong("patients"),
   rs.getLong("last_thirty_days"),rs.getLong("previous_thirty_days"),rs.getLong("upcoming_slots"));
 }
}
