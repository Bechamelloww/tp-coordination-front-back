package fr.medislot.calendar;

import fr.medislot.domain.Appointment;
import fr.medislot.domain.Professional;
import fr.medislot.domain.Slot;
import fr.medislot.repository.AppointmentRepository;
import fr.medislot.repository.ProfessionalRepository;
import fr.medislot.repository.SlotRepository;
import fr.medislot.repository.UserRepository;
import jakarta.servlet.http.HttpSession;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.Map;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;
import org.springframework.web.server.ResponseStatusException;

@Service
public class GoogleCalendarService {
 static final String SESSION_STATE = "google.oauth.state";
 static final String SESSION_CODE_VERIFIER = "google.oauth.codeVerifier";
 private static final String NOT_LINKED = "NON_CONNECTE";
 private static final int RANDOM_LENGTH_BYTES = 32;
 private static final int LOCK_STRIPES = 64;
 private static final Logger log = LoggerFactory.getLogger(GoogleCalendarService.class);
 private static final Base64.Encoder URL_ENCODER = Base64.getUrlEncoder().withoutPadding();

 public enum AuthorizationOutcome {
  CONNECTE("connecte"), REFUSE("refuse"), ERREUR("erreur");

  private final String queryValue;

  AuthorizationOutcome(String queryValue) {
   this.queryValue = queryValue;
  }

  public String queryValue() {
   return queryValue;
  }
 }

 public record LinkStatus(boolean enabled, String status) {
 }

 private final GoogleProperties properties;
 private final GoogleOAuthClient oauthClient;
 private final GoogleCalendarClient calendarClient;
 private final GoogleCalendarLinks links;
 private final TokenCipher tokenCipher;
 private final UserRepository users;
 private final AppointmentRepository appointments;
 private final SlotRepository slots;
 private final ProfessionalRepository professionals;
 private final SecureRandom random = new SecureRandom();
 private final Object[] appointmentLocks = new Object[LOCK_STRIPES];

 public GoogleCalendarService(GoogleProperties properties, GoogleOAuthClient oauthClient, GoogleCalendarClient calendarClient,
   GoogleCalendarLinks links, TokenCipher tokenCipher, UserRepository users, AppointmentRepository appointments,
   SlotRepository slots, ProfessionalRepository professionals) {
  this.properties = properties;
  this.oauthClient = oauthClient;
  this.calendarClient = calendarClient;
  this.links = links;
  this.tokenCipher = tokenCipher;
  this.users = users;
  this.appointments = appointments;
  this.slots = slots;
  this.professionals = professionals;
  for (int index = 0; index < LOCK_STRIPES; index++) {
   appointmentLocks[index] = new Object();
  }
 }

 public LinkStatus status(String email) {
  if (!properties.enabled()) {
   return new LinkStatus(false, NOT_LINKED);
  }
  String status = links.find(userId(email)).map(GoogleCalendarLinks.Link::status).orElse(NOT_LINKED);
  return new LinkStatus(true, status);
 }

 public String startAuthorization(HttpSession session) {
  requireEnabled();
  String state = randomUrlSafe();
  String codeVerifier = randomUrlSafe();
  session.setAttribute(SESSION_STATE, state);
  session.setAttribute(SESSION_CODE_VERIFIER, codeVerifier);
  return oauthClient.authorizationUrl(state, codeChallenge(codeVerifier));
 }

 public AuthorizationOutcome completeAuthorization(HttpSession session, String email, String returnedState,
   String authorizationCode, String googleError) {
  String expectedState = session == null ? null : (String) session.getAttribute(SESSION_STATE);
  String codeVerifier = session == null ? null : (String) session.getAttribute(SESSION_CODE_VERIFIER);
  if (session != null) {
   session.removeAttribute(SESSION_STATE);
   session.removeAttribute(SESSION_CODE_VERIFIER);
  }
  if (!properties.enabled() || email == null || !sameState(expectedState, returnedState) || codeVerifier == null) {
   return AuthorizationOutcome.ERREUR;
  }
  if (googleError != null) {
   return AuthorizationOutcome.REFUSE;
  }
  if (authorizationCode == null || authorizationCode.isBlank()) {
   return AuthorizationOutcome.ERREUR;
  }
  GoogleOAuthClient.GrantedTokens tokens = oauthClient.exchangeCode(authorizationCode, codeVerifier);
  if (!tokens.includesCalendarScope()) {
   revokeQuietly(tokens.accessToken());
   return AuthorizationOutcome.REFUSE;
  }
  if (tokens.refreshToken() == null) {
   revokeQuietly(tokens.accessToken());
   return AuthorizationOutcome.ERREUR;
  }
  Long userId = userId(email);
  disconnectExistingLink(userId);
  String calendarId = calendarClient.createCalendar(tokens.accessToken());
  links.save(userId, calendarId, tokenCipher.encrypt(tokens.refreshToken(), userId));
  synchronizeUpcomingAppointments(userId);
  return AuthorizationOutcome.CONNECTE;
 }

 public void disconnect(String email) {
  Long userId = userId(email);
  links.find(userId).ifPresent(link -> {
   removeFromGoogle(link);
   links.delete(userId);
  });
 }

 public void synchronize(Long appointmentId) {
  if (!properties.enabled()) {
   return;
  }
  synchronized (appointmentLocks[Math.floorMod(appointmentId, LOCK_STRIPES)]) {
   Optional<Appointment> appointment = appointments.findById(appointmentId);
   if (appointment.isEmpty()) {
    return;
   }
   Optional<GoogleCalendarLinks.Link> link = links.find(appointment.get().patientId).filter(GoogleCalendarLinks.Link::connected);
   if (link.isEmpty()) {
    return;
   }
   String accessToken = accessToken(link.get());
   if (accessToken == null) {
    return;
   }
   pushToGoogle(link.get(), accessToken, appointment.get());
  }
 }

 private void pushToGoogle(GoogleCalendarLinks.Link link, String accessToken, Appointment appointment) {
  String eventId = CalendarEventView.eventId(appointment.id);
  try {
   if ("CONFIRMED".equals(appointment.status)) {
    Slot slot = slots.findById(appointment.slotId).orElseThrow();
    Professional professional = professionals.findById(slot.professionalId).orElseThrow();
    upsertRecreatingCalendar(link, accessToken, eventId, CalendarEventView.of(appointment, slot, professional));
   } else {
    calendarClient.deleteEvent(accessToken, link.calendarId(), eventId);
   }
  } catch (RestClientException | GoogleCalendarClient.CalendarNotFoundException failure) {
   links.recordError(link.userId());
   throw failure;
  }
 }

 private void upsertRecreatingCalendar(GoogleCalendarLinks.Link link, String accessToken, String eventId,
   Map<String, Object> event) {
  try {
   calendarClient.upsertEvent(accessToken, link.calendarId(), eventId, event);
  } catch (GoogleCalendarClient.CalendarNotFoundException calendarDeletedByUser) {
   String newCalendarId = calendarClient.createCalendar(accessToken);
   links.updateCalendar(link.userId(), newCalendarId);
   calendarClient.upsertEvent(accessToken, newCalendarId, eventId, event);
  }
 }

 private void synchronizeUpcomingAppointments(Long userId) {
  Instant now = Instant.now();
  appointments.findByPatientIdOrderByCreatedAtDesc(userId).stream()
    .filter(appointment -> "CONFIRMED".equals(appointment.status))
    .filter(appointment -> slots.findById(appointment.slotId).map(slot -> slot.startsAt.isAfter(now)).orElse(false))
    .forEach(appointment -> {
     try {
      synchronize(appointment.id);
     } catch (RuntimeException failure) {
      log.warn("Synchronisation initiale Google Agenda du rendez-vous {} impossible : {}", appointment.id,
        failure.getClass().getSimpleName());
     }
    });
 }

 private String accessToken(GoogleCalendarLinks.Link link) {
  try {
   return oauthClient.refreshAccessToken(tokenCipher.decrypt(link.encryptedRefreshToken(), link.userId()));
  } catch (GoogleAccessRevokedException revoked) {
   links.markNeedsReconnect(link.userId());
   return null;
  }
 }

 private void disconnectExistingLink(Long userId) {
  links.find(userId).ifPresent(this::removeFromGoogle);
 }

 private void removeFromGoogle(GoogleCalendarLinks.Link link) {
  String refreshToken;
  try {
   refreshToken = tokenCipher.decrypt(link.encryptedRefreshToken(), link.userId());
  } catch (IllegalStateException unreadable) {
   log.warn("Jeton Google illisible pour l'utilisateur {}", link.userId());
   return;
  }
  try {
   String accessToken = oauthClient.refreshAccessToken(refreshToken);
   calendarClient.deleteCalendar(accessToken, link.calendarId());
  } catch (RuntimeException failure) {
   log.warn("Suppression de l'agenda Google impossible pour l'utilisateur {} : {}", link.userId(),
     failure.getClass().getSimpleName());
  }
  revokeQuietly(refreshToken);
 }

 private void revokeQuietly(String token) {
  if (token == null) {
   return;
  }
  try {
   oauthClient.revoke(token);
  } catch (RuntimeException failure) {
   log.warn("Révocation du jeton Google impossible : {}", failure.getClass().getSimpleName());
  }
 }

 private Long userId(String email) {
  return users.findByEmail(email)
    .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Connexion requise."))
    .id;
 }

 private void requireEnabled() {
  if (!properties.enabled()) {
   throw new ResponseStatusException(HttpStatus.NOT_FOUND, "La synchronisation Google Agenda n'est pas activée.");
  }
 }

 private String randomUrlSafe() {
  byte[] bytes = new byte[RANDOM_LENGTH_BYTES];
  random.nextBytes(bytes);
  return URL_ENCODER.encodeToString(bytes);
 }

 public static String codeChallenge(String codeVerifier) {
  try {
   byte[] digest = MessageDigest.getInstance("SHA-256").digest(codeVerifier.getBytes(StandardCharsets.US_ASCII));
   return URL_ENCODER.encodeToString(digest);
  } catch (NoSuchAlgorithmException missingAlgorithm) {
   throw new IllegalStateException(missingAlgorithm);
  }
 }

 private static boolean sameState(String expectedState, String returnedState) {
  if (expectedState == null || returnedState == null) {
   return false;
  }
  return MessageDigest.isEqual(expectedState.getBytes(StandardCharsets.UTF_8), returnedState.getBytes(StandardCharsets.UTF_8));
 }
}
