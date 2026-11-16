package fr.medislot.calendar;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import fr.medislot.application.BookingService;
import fr.medislot.domain.Appointment;
import fr.medislot.domain.Slot;
import fr.medislot.repository.SlotRepository;
import fr.medislot.repository.UserRepository;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Queue;
import java.util.UUID;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BooleanSupplier;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.web.util.UriComponentsBuilder;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

@SpringBootTest
@Testcontainers
class GoogleCalendarIntegrationTest {
 private static final String PATIENT = "patient@medislot.test";
 private static final Duration WAIT_LIMIT = Duration.ofSeconds(10);
 private static final Queue<String> googleRequests = new ConcurrentLinkedQueue<>();
 private static final AtomicBoolean googleDown = new AtomicBoolean(false);
 private static final HttpServer fakeGoogle = startFakeGoogle();

 @Container
 static PostgreSQLContainer database = new PostgreSQLContainer("postgres:18");

 @DynamicPropertySource
 static void properties(DynamicPropertyRegistry registry) {
  String fakeGoogleUrl = "http://localhost:" + fakeGoogle.getAddress().getPort();
  registry.add("spring.datasource.url", database::getJdbcUrl);
  registry.add("spring.datasource.username", database::getUsername);
  registry.add("spring.datasource.password", database::getPassword);
  registry.add("medislot.demo-seed", () -> "true");
  registry.add("medislot.demo-password", () -> UUID.randomUUID().toString());
  registry.add("medislot.google.client-id", () -> "client-id-test");
  registry.add("medislot.google.client-secret", () -> "client-secret-test");
  registry.add("medislot.google.token-key", () -> GoogleTestProperties.TOKEN_KEY);
  registry.add("medislot.google.authorization-uri", () -> fakeGoogleUrl + "/authorize");
  registry.add("medislot.google.token-uri", () -> fakeGoogleUrl + "/token");
  registry.add("medislot.google.revoke-uri", () -> fakeGoogleUrl + "/revoke");
  registry.add("medislot.google.calendar-api-uri", () -> fakeGoogleUrl + "/calendar/v3");
 }

 @Autowired
 private GoogleCalendarService calendarService;
 @Autowired
 private BookingService bookingService;
 @Autowired
 private SlotRepository slots;
 @Autowired
 private UserRepository users;
 @Autowired
 private JdbcTemplate jdbc;

 @BeforeEach
 void resetFakeGoogle() {
  googleDown.set(false);
  googleRequests.clear();
 }

 @AfterAll
 static void stopFakeGoogle() {
  fakeGoogle.stop(0);
 }

 @Test
 void leCycleDeVieDuRendezVousEstRepliqueDansGoogleAgenda() {
  connectPatient();
  List<Slot> freeSlots = slots.available(3L, Instant.now(), Instant.now().plus(Duration.ofDays(14)));

  Appointment appointment = bookingService.book(PATIENT, freeSlots.get(0).id);
  String eventPath = "/calendar/v3/calendars/agenda-test/events/medislotrdv" + appointment.id;
  waitForRequest("PUT " + eventPath);

  googleRequests.clear();
  bookingService.reschedule(PATIENT, appointment.id, freeSlots.get(1).id);
  waitForRequest("PUT " + eventPath);

  googleRequests.clear();
  bookingService.cancel(PATIENT, appointment.id);
  waitForRequest("DELETE " + eventPath);
 }

 @Test
 void leJetonDeRafraichissementEstChiffreEnBase() {
  connectPatient();
  Long patientId = users.findByEmail(PATIENT).orElseThrow().id;
  byte[] storedToken = jdbc.queryForObject("select refresh_token from google_calendar_link where user_id = ?", byte[].class, patientId);
  assertNotNull(storedToken);
  assertFalse(new String(storedToken, StandardCharsets.ISO_8859_1).contains("jeton-rafraichissement-test"));
  assertEquals("CONNECTE", calendarService.status(PATIENT).status());
 }

 @Test
 void unePanneGoogleNEmpechePasLaReservation() {
  connectPatient();
  googleDown.set(true);
  List<Slot> freeSlots = slots.available(5L, Instant.now(), Instant.now().plus(Duration.ofDays(14)));
  Appointment appointment = bookingService.book(PATIENT, freeSlots.get(0).id);
  assertEquals("CONFIRMED", appointment.status);
  waitForRequest("PUT /calendar/v3/calendars/agenda-test/events/medislotrdv" + appointment.id);
  bookingService.cancel(PATIENT, appointment.id);
 }

 @Test
 void laDeconnexionRevoqueLAccesEtEffaceLaLiaison() {
  connectPatient();
  googleRequests.clear();
  calendarService.disconnect(PATIENT);
  assertTrue(googleRequests.contains("DELETE /calendar/v3/calendars/agenda-test"));
  assertTrue(googleRequests.contains("POST /revoke"));
  assertEquals("NON_CONNECTE", calendarService.status(PATIENT).status());
 }

 private void connectPatient() {
  MockHttpSession session = new MockHttpSession();
  String authorizationUrl = calendarService.startAuthorization(session);
  String state = UriComponentsBuilder.fromUriString(authorizationUrl).build().getQueryParams().getFirst("state");
  GoogleCalendarService.AuthorizationOutcome outcome =
    calendarService.completeAuthorization(session, PATIENT, state, "code-test", null);
  assertEquals(GoogleCalendarService.AuthorizationOutcome.CONNECTE, outcome);
 }

 private static void waitForRequest(String expectedRequest) {
  waitUntil(() -> googleRequests.contains(expectedRequest), expectedRequest);
 }

 private static void waitUntil(BooleanSupplier condition, String description) {
  Instant deadline = Instant.now().plus(WAIT_LIMIT);
  while (!condition.getAsBoolean()) {
   if (Instant.now().isAfter(deadline)) {
    throw new AssertionError("Requête Google attendue : " + description + ", reçues : " + googleRequests);
   }
   try {
    Thread.sleep(50);
   } catch (InterruptedException interrupted) {
    Thread.currentThread().interrupt();
    throw new AssertionError(interrupted);
   }
  }
 }

 private static HttpServer startFakeGoogle() {
  try {
   HttpServer server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
   server.createContext("/", GoogleCalendarIntegrationTest::answer);
   server.start();
   return server;
  } catch (IOException failure) {
   throw new UncheckedIOException(failure);
  }
 }

 private static void answer(HttpExchange exchange) throws IOException {
  String method = exchange.getRequestMethod();
  String path = exchange.getRequestURI().getPath();
  exchange.getRequestBody().readAllBytes();
  googleRequests.add(method + " " + path);
  if (googleDown.get() && path.contains("/events/")) {
   respond(exchange, 503, "{}");
  } else if (path.equals("/token")) {
   respond(exchange, 200, """
     {"access_token":"jeton-acces-test","refresh_token":"jeton-rafraichissement-test","scope":"%s"}
     """.formatted(GoogleOAuthClient.CALENDAR_SCOPE));
  } else if (method.equals("POST") && path.equals("/calendar/v3/calendars")) {
   respond(exchange, 200, "{\"id\":\"agenda-test\"}");
  } else if (method.equals("DELETE")) {
   respond(exchange, 204, null);
  } else {
   respond(exchange, 200, "{}");
  }
 }

 private static void respond(HttpExchange exchange, int status, String body) throws IOException {
  if (body == null) {
   exchange.sendResponseHeaders(status, -1);
   exchange.close();
   return;
  }
  byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
  exchange.getResponseHeaders().add("Content-Type", "application/json");
  exchange.sendResponseHeaders(status, bytes.length);
  exchange.getResponseBody().write(bytes);
  exchange.close();
 }
}
