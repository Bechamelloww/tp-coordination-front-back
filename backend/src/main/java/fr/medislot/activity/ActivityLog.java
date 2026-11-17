package fr.medislot.activity;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
public class ActivityLog {
 public static final int PAGE_SIZE = 50;
 static final ZoneId CLINIC_ZONE = ZoneId.of("Europe/Paris");

 public sealed interface Scope {
  record Everything() implements Scope {}
  record OneClinic(Long clinicId) implements Scope {}
  record OutsideClinics() implements Scope {}
 }

 public record Filter(Activity action, LocalDate from, LocalDate to, String text, Long before) {}

 public record Entry(Long id, Instant createdAt, Activity action, String detail, String actorName, String actorEmail,
   String clinicSlug, String clinicName) {}

 public record Page(List<Entry> entries, boolean more) {}

 private final JdbcTemplate jdbc;

 public ActivityLog(JdbcTemplate jdbc) {
  this.jdbc = jdbc;
 }

 public void record(Long clinicId, Long actorId, Activity action, String detail) {
  jdbc.update("insert into activity_log(clinic_id, actor_id, action, detail) values (?, ?, ?, ?)",
    clinicId, actorId, action.name(), shorten(detail));
 }

 public void recordByEmail(Long clinicId, String actorEmail, Activity action, String detail) {
  jdbc.update("insert into activity_log(clinic_id, actor_id, action, detail) values (?, (select id from app_user where email = ?), ?, ?)",
    clinicId, actorEmail, action.name(), shorten(detail));
 }

 public void recordAppointment(Long appointmentId, Activity action) {
  jdbc.update("""
    insert into activity_log(clinic_id, actor_id, action, detail)
    select a.clinic_id, a.patient_id, ?, p.name || ' · ' || to_char(s.starts_at at time zone 'Europe/Paris', 'DD/MM/YYYY HH24:MI')
    from appointment a join slot s on s.id = a.slot_id join professional p on p.id = s.professional_id
    where a.id = ?""", action.name(), appointmentId);
 }

 public Page search(Scope scope, Filter filter) {
  StringBuilder sql = new StringBuilder("""
    select l.id, l.created_at, l.action, l.detail, u.name actor_name, u.email actor_email, c.slug clinic_slug, c.name clinic_name
    from activity_log l left join app_user u on u.id = l.actor_id left join clinic c on c.id = l.clinic_id
    where true""");
  List<Object> params = new ArrayList<>();
  switch (scope) {
   case Scope.Everything _ -> {}
   case Scope.OneClinic one -> { sql.append(" and l.clinic_id = ?"); params.add(one.clinicId()); }
   case Scope.OutsideClinics _ -> sql.append(" and l.clinic_id is null");
  }
  if (filter.action() != null) { sql.append(" and l.action = ?"); params.add(filter.action().name()); }
  if (filter.from() != null) { sql.append(" and l.created_at >= ?"); params.add(startOf(filter.from())); }
  if (filter.to() != null) { sql.append(" and l.created_at < ?"); params.add(startOf(filter.to().plusDays(1))); }
  if (filter.text() != null && !filter.text().isBlank()) {
   String pattern = "%" + escapeLike(filter.text().strip()) + "%";
   sql.append(" and (u.name ilike ? escape '\\' or u.email ilike ? escape '\\' or l.detail ilike ? escape '\\')");
   params.add(pattern); params.add(pattern); params.add(pattern);
  }
  if (filter.before() != null) { sql.append(" and l.id < ?"); params.add(filter.before()); }
  sql.append(" order by l.id desc limit ").append(PAGE_SIZE + 1);
  List<Entry> rows = jdbc.query(sql.toString(), (rs, i) -> entry(rs), params.toArray());
  boolean more = rows.size() > PAGE_SIZE;
  return new Page(more ? rows.subList(0, PAGE_SIZE) : rows, more);
 }

 private static Entry entry(ResultSet rs) throws SQLException {
  return new Entry(rs.getLong("id"), rs.getTimestamp("created_at").toInstant(), Activity.valueOf(rs.getString("action")),
    rs.getString("detail"), rs.getString("actor_name"), rs.getString("actor_email"), rs.getString("clinic_slug"), rs.getString("clinic_name"));
 }

 private static OffsetDateTime startOf(LocalDate day) {
  return day.atStartOfDay(CLINIC_ZONE).toOffsetDateTime();
 }

 private static String escapeLike(String text) {
  return text.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
 }

 private static String shorten(String detail) {
  return detail == null || detail.length() <= 300 ? detail : detail.substring(0, 300);
 }
}
