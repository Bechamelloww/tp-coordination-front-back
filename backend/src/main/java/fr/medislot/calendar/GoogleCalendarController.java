package fr.medislot.calendar;

import fr.medislot.activity.Activity;
import fr.medislot.activity.ActivityLog;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import java.net.URI;
import java.security.Principal;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/google")
public class GoogleCalendarController {
 private static final Logger log = LoggerFactory.getLogger(GoogleCalendarController.class);
 private final GoogleCalendarService calendarService;
 private final ActivityLog activityLog;

 public GoogleCalendarController(GoogleCalendarService calendarService, ActivityLog activityLog) {
  this.calendarService = calendarService;
  this.activityLog = activityLog;
 }

 @GetMapping("/status")
 public GoogleCalendarService.LinkStatus status(Principal principal) {
  return calendarService.status(principal.getName());
 }

 @PostMapping("/authorize")
 public ResponseEntity<Map<String, String>> authorize(HttpSession session) {
  String authorizationUrl = calendarService.startAuthorization(session);
  return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(Map.of("url", authorizationUrl));
 }

 @GetMapping("/callback")
 public ResponseEntity<Void> callback(HttpServletRequest request,
   @RequestParam(name = "state", required = false) String state,
   @RequestParam(name = "code", required = false) String code,
   @RequestParam(name = "error", required = false) String error) {
  Principal principal = request.getUserPrincipal();
  String email = principal == null ? null : principal.getName();
  GoogleCalendarService.AuthorizationOutcome outcome;
  try {
   outcome = calendarService.completeAuthorization(request.getSession(false), email, state, code, error);
  } catch (RuntimeException failure) {
   log.warn("Liaison Google Agenda impossible : {}", failure.getClass().getSimpleName());
   outcome = GoogleCalendarService.AuthorizationOutcome.ERREUR;
  }
  if (outcome == GoogleCalendarService.AuthorizationOutcome.CONNECTE) {
   activityLog.recordByEmail(null, email, Activity.GOOGLE_CALENDAR_LINKED, null);
  }
  return ResponseEntity.status(HttpStatus.FOUND)
    .location(URI.create("/?google=" + outcome.queryValue()))
    .cacheControl(CacheControl.noStore())
    .header("Referrer-Policy", "no-referrer")
    .build();
 }

 @DeleteMapping("/link")
 public ResponseEntity<Void> disconnect(Principal principal) {
  calendarService.disconnect(principal.getName());
  activityLog.recordByEmail(null, principal.getName(), Activity.GOOGLE_CALENDAR_UNLINKED, null);
  return ResponseEntity.noContent().build();
 }
}
