package fr.medislot.repository;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Repository;
/** Clés d'idempotence des réservations (table booking_key). */
@Repository
public class BookingKeys {
 public static final Duration RETENTION=Duration.ofDays(7);
 public record Entry(Long slotId,Long appointmentId){}
 private final JdbcTemplate jdbc;
 public BookingKeys(JdbcTemplate jdbc){this.jdbc=jdbc;}
 public Optional<Entry> find(Long patientId,UUID key){
  return jdbc.query("select slot_id,appointment_id from booking_key where patient_id=? and idem_key=?",(rs,i)->new Entry(rs.getLong(1),rs.getLong(2)),patientId,key).stream().findFirst();
 }
 public void save(Long patientId,UUID key,Long slotId,Long appointmentId){
  jdbc.update("insert into booking_key(patient_id,idem_key,slot_id,appointment_id,created_at) values (?,?,?,?,?)",patientId,key,slotId,appointmentId,Timestamp.from(Instant.now()));
 }
 @Scheduled(fixedDelay=3_600_000) public void purge(){jdbc.update("delete from booking_key where created_at<?",Timestamp.from(Instant.now().minus(RETENTION)));}
}
