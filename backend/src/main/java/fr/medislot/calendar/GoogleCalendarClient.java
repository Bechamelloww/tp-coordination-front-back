package fr.medislot.calendar;

import java.util.HashMap;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;

@Component
public class GoogleCalendarClient {
 public static final String CALENDAR_NAME = "MediSlot";
 private static final ParameterizedTypeReference<Map<String, Object>> JSON_OBJECT = new ParameterizedTypeReference<>() {
 };

 public static class CalendarNotFoundException extends RuntimeException {
  public CalendarNotFoundException() {
   super("L'agenda MediSlot n'existe plus chez Google.");
  }
 }

 private final RestClient http;

 @Autowired
 public GoogleCalendarClient(GoogleProperties properties) {
  this(properties, GoogleHttp.builderWithTimeouts());
 }

 GoogleCalendarClient(GoogleProperties properties, RestClient.Builder httpBuilder) {
  this.http = httpBuilder.baseUrl(properties.calendarApiUri()).build();
 }

 public String createCalendar(String accessToken) {
  Map<String, Object> calendar = Map.of(
    "summary", CALENDAR_NAME,
    "description", "Rendez-vous pris sur MediSlot. Pour déplacer ou annuler, passez par MediSlot.",
    "timeZone", CalendarEventView.TIME_ZONE);
  Map<String, Object> created = http.post()
    .uri("/calendars")
    .headers(headers -> headers.setBearerAuth(accessToken))
    .contentType(MediaType.APPLICATION_JSON)
    .body(calendar)
    .retrieve()
    .body(JSON_OBJECT);
  if (created == null || created.get("id") == null) {
   throw new IllegalStateException("Google n'a pas renvoyé l'identifiant de l'agenda.");
  }
  return created.get("id").toString();
 }

 public void deleteCalendar(String accessToken, String calendarId) {
  try {
   http.delete()
     .uri("/calendars/{calendarId}", calendarId)
     .headers(headers -> headers.setBearerAuth(accessToken))
     .retrieve()
     .toBodilessEntity();
  } catch (HttpClientErrorException.NotFound | HttpClientErrorException.Gone alreadyDeleted) {
   return;
  }
 }

 public void upsertEvent(String accessToken, String calendarId, String eventId, Map<String, Object> event) {
  try {
   http.put()
     .uri("/calendars/{calendarId}/events/{eventId}", calendarId, eventId)
     .headers(headers -> headers.setBearerAuth(accessToken))
     .contentType(MediaType.APPLICATION_JSON)
     .body(event)
     .retrieve()
     .toBodilessEntity();
  } catch (HttpClientErrorException.NotFound eventMissing) {
   insertEvent(accessToken, calendarId, eventId, event);
  }
 }

 private void insertEvent(String accessToken, String calendarId, String eventId, Map<String, Object> event) {
  Map<String, Object> eventWithId = new HashMap<>(event);
  eventWithId.put("id", eventId);
  try {
   http.post()
     .uri("/calendars/{calendarId}/events", calendarId)
     .headers(headers -> headers.setBearerAuth(accessToken))
     .contentType(MediaType.APPLICATION_JSON)
     .body(eventWithId)
     .retrieve()
     .toBodilessEntity();
  } catch (HttpClientErrorException.NotFound calendarMissing) {
   throw new CalendarNotFoundException();
  }
 }

 public void deleteEvent(String accessToken, String calendarId, String eventId) {
  try {
   http.delete()
     .uri("/calendars/{calendarId}/events/{eventId}", calendarId, eventId)
     .headers(headers -> headers.setBearerAuth(accessToken))
     .retrieve()
     .toBodilessEntity();
  } catch (HttpClientErrorException deletionRejected) {
   boolean alreadyGone = deletionRejected.getStatusCode().isSameCodeAs(HttpStatus.NOT_FOUND)
     || deletionRejected.getStatusCode().isSameCodeAs(HttpStatus.GONE);
   if (!alreadyGone) {
    throw deletionRejected;
   }
  }
 }
}
