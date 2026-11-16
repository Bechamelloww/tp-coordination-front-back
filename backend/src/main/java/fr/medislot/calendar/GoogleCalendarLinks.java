package fr.medislot.calendar;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class GoogleCalendarLinks {
 public static final String CONNECTED = "CONNECTE";
 public static final String NEEDS_RECONNECT = "A_RECONNECTER";

 public record Link(Long userId, String calendarId, byte[] encryptedRefreshToken, String status) {
  public boolean connected() {
   return CONNECTED.equals(status);
  }
 }

 private final JdbcTemplate jdbc;

 public GoogleCalendarLinks(JdbcTemplate jdbc) {
  this.jdbc = jdbc;
 }

 public Optional<Link> find(Long userId) {
  return jdbc.query(
    "select user_id, calendar_id, refresh_token, status from google_calendar_link where user_id = ?",
    (row, index) -> new Link(row.getLong(1), row.getString(2), row.getBytes(3), row.getString(4)),
    userId).stream().findFirst();
 }

 public void save(Long userId, String calendarId, byte[] encryptedRefreshToken) {
  jdbc.update("""
    insert into google_calendar_link (user_id, calendar_id, refresh_token, status, connected_at, last_error_at)
    values (?, ?, ?, ?, ?, null)
    on conflict (user_id) do update set
     calendar_id = excluded.calendar_id,
     refresh_token = excluded.refresh_token,
     status = excluded.status,
     connected_at = excluded.connected_at,
     last_error_at = null
    """, userId, calendarId, encryptedRefreshToken, CONNECTED, Timestamp.from(Instant.now()));
 }

 public void updateCalendar(Long userId, String calendarId) {
  jdbc.update("update google_calendar_link set calendar_id = ? where user_id = ?", calendarId, userId);
 }

 public void markNeedsReconnect(Long userId) {
  jdbc.update("update google_calendar_link set status = ?, last_error_at = ? where user_id = ?",
    NEEDS_RECONNECT, Timestamp.from(Instant.now()), userId);
 }

 public void recordError(Long userId) {
  jdbc.update("update google_calendar_link set last_error_at = ? where user_id = ?", Timestamp.from(Instant.now()), userId);
 }

 public void delete(Long userId) {
  jdbc.update("delete from google_calendar_link where user_id = ?", userId);
 }
}
