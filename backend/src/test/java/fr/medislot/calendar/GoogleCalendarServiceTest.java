package fr.medislot.calendar;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import fr.medislot.calendar.GoogleCalendarService.AuthorizationOutcome;
import fr.medislot.domain.AppUser;
import fr.medislot.domain.Appointment;
import fr.medislot.domain.Professional;
import fr.medislot.domain.Slot;
import fr.medislot.repository.AppointmentRepository;
import fr.medislot.repository.ProfessionalRepository;
import fr.medislot.repository.SlotRepository;
import fr.medislot.repository.UserRepository;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.mock.web.MockHttpSession;

class GoogleCalendarServiceTest {
 private static final String EMAIL = "patient@medislot.test";
 private static final long USER_ID = 1L;

 private final GoogleOAuthClient oauthClient = mock(GoogleOAuthClient.class);
 private final GoogleCalendarClient calendarClient = mock(GoogleCalendarClient.class);
 private final GoogleCalendarLinks links = mock(GoogleCalendarLinks.class);
 private final TokenCipher tokenCipher = new TokenCipher(GoogleTestProperties.enabled());
 private final UserRepository users = mock(UserRepository.class);
 private final AppointmentRepository appointments = mock(AppointmentRepository.class);
 private final SlotRepository slots = mock(SlotRepository.class);
 private final ProfessionalRepository professionals = mock(ProfessionalRepository.class);
 private final GoogleCalendarService service = new GoogleCalendarService(GoogleTestProperties.enabled(), oauthClient,
   calendarClient, links, tokenCipher, users, appointments, slots, professionals);
 private final MockHttpSession session = new MockHttpSession();
 private Appointment appointment;

 @BeforeEach
 void sampleData() {
  AppUser patient = new AppUser();
  patient.id = USER_ID;
  patient.email = EMAIL;
  when(users.findByEmail(EMAIL)).thenReturn(Optional.of(patient));
  when(oauthClient.authorizationUrl(anyString(), anyString())).thenReturn("https://auth.test/authorize");
  appointment = new Appointment();
  appointment.id = 42L;
  appointment.patientId = USER_ID;
  appointment.slotId = 7L;
  appointment.status = "CONFIRMED";
  Slot slot = new Slot();
  slot.id = 7L;
  slot.professionalId = 3L;
  slot.startsAt = Instant.now().plusSeconds(86_400);
  slot.endsAt = slot.startsAt.plusSeconds(1_800);
  Professional professional = new Professional();
  professional.id = 3L;
  professional.name = "Dr Sarah Martin";
  professional.address = "18 avenue de la République";
  professional.city = "Paris";
  when(appointments.findById(42L)).thenReturn(Optional.of(appointment));
  when(slots.findById(7L)).thenReturn(Optional.of(slot));
  when(professionals.findById(3L)).thenReturn(Optional.of(professional));
 }

 private GoogleCalendarLinks.Link connectedLink() {
  return new GoogleCalendarLinks.Link(USER_ID, "agenda-medislot", tokenCipher.encrypt("rafraichissement", USER_ID),
    GoogleCalendarLinks.CONNECTED);
 }

 private String startedState() {
  service.startAuthorization(session);
  return (String) session.getAttribute(GoogleCalendarService.SESSION_STATE);
 }

 @Test
 void unEtatDifferentEstRefuseSansAppelerGoogle() {
  startedState();
  AuthorizationOutcome outcome = service.completeAuthorization(session, EMAIL, "etat-forge", "code", null);
  assertEquals(AuthorizationOutcome.ERREUR, outcome);
  verify(oauthClient, never()).exchangeCode(anyString(), anyString());
 }

 @Test
 void unEtatNeSertQuUneFois() {
  String state = startedState();
  when(oauthClient.exchangeCode(eq("code"), anyString())).thenReturn(
    new GoogleOAuthClient.GrantedTokens("acces", "rafraichissement", GoogleOAuthClient.CALENDAR_SCOPE));
  when(calendarClient.createCalendar("acces")).thenReturn("agenda-medislot");
  assertEquals(AuthorizationOutcome.CONNECTE, service.completeAuthorization(session, EMAIL, state, "code", null));
  assertEquals(AuthorizationOutcome.ERREUR, service.completeAuthorization(session, EMAIL, state, "code", null));
  assertNull(session.getAttribute(GoogleCalendarService.SESSION_CODE_VERIFIER));
 }

 @Test
 void unRefusSurLEcranGoogleEstRapporte() {
  String state = startedState();
  assertEquals(AuthorizationOutcome.REFUSE, service.completeAuthorization(session, EMAIL, state, null, "access_denied"));
 }

 @Test
 void uneLiaisonReussieStockeLeJetonChiffreEtCreeLAgenda() {
  String state = startedState();
  when(oauthClient.exchangeCode(eq("code"), anyString())).thenReturn(
    new GoogleOAuthClient.GrantedTokens("acces", "rafraichissement", GoogleOAuthClient.CALENDAR_SCOPE));
  when(calendarClient.createCalendar("acces")).thenReturn("agenda-medislot");
  service.completeAuthorization(session, EMAIL, state, "code", null);
  ArgumentCaptor<byte[]> storedToken = ArgumentCaptor.forClass(byte[].class);
  verify(links).save(eq(USER_ID), eq("agenda-medislot"), storedToken.capture());
  assertEquals("rafraichissement", tokenCipher.decrypt(storedToken.getValue(), USER_ID));
  assertFalse(new String(storedToken.getValue(), StandardCharsets.ISO_8859_1).contains("rafraichissement"));
 }

 @Test
 void uneAutorisationSansLaPorteeAgendaEstRefusee() {
  String state = startedState();
  when(oauthClient.exchangeCode(eq("code"), anyString())).thenReturn(
    new GoogleOAuthClient.GrantedTokens("acces", "rafraichissement", "openid"));
  assertEquals(AuthorizationOutcome.REFUSE, service.completeAuthorization(session, EMAIL, state, "code", null));
  verify(links, never()).save(any(), any(), any());
  verify(oauthClient).revoke("acces");
 }

 @Test
 void unRendezVousConfirmeEstEnvoyeDansLAgendaMediSlot() {
  when(links.find(USER_ID)).thenReturn(Optional.of(connectedLink()));
  when(oauthClient.refreshAccessToken("rafraichissement")).thenReturn("acces");
  service.synchronize(42L);
  verify(calendarClient).upsertEvent(eq("acces"), eq("agenda-medislot"), eq("medislotrdv42"), any());
 }

 @Test
 void unRendezVousAnnuleEstRetireDeLAgenda() {
  appointment.status = "CANCELLED";
  when(links.find(USER_ID)).thenReturn(Optional.of(connectedLink()));
  when(oauthClient.refreshAccessToken("rafraichissement")).thenReturn("acces");
  service.synchronize(42L);
  verify(calendarClient).deleteEvent("acces", "agenda-medislot", "medislotrdv42");
 }

 @Test
 void unPatientNonLieNeDeclencheAucunAppelGoogle() {
  when(links.find(USER_ID)).thenReturn(Optional.empty());
  service.synchronize(42L);
  verifyNoInteractions(oauthClient, calendarClient);
 }

 @Test
 void unAccesRetireMarqueLaLiaisonAReconnecter() {
  when(links.find(USER_ID)).thenReturn(Optional.of(connectedLink()));
  when(oauthClient.refreshAccessToken("rafraichissement")).thenThrow(new GoogleAccessRevokedException());
  service.synchronize(42L);
  verify(links).markNeedsReconnect(USER_ID);
  verifyNoInteractions(calendarClient);
 }

 @Test
 void unAgendaSupprimeParLePatientEstRecree() {
  when(links.find(USER_ID)).thenReturn(Optional.of(connectedLink()));
  when(oauthClient.refreshAccessToken("rafraichissement")).thenReturn("acces");
  doThrow(new GoogleCalendarClient.CalendarNotFoundException())
    .when(calendarClient).upsertEvent(eq("acces"), eq("agenda-medislot"), anyString(), any());
  when(calendarClient.createCalendar("acces")).thenReturn("nouvel-agenda");
  service.synchronize(42L);
  verify(links).updateCalendar(USER_ID, "nouvel-agenda");
  verify(calendarClient).upsertEvent(eq("acces"), eq("nouvel-agenda"), eq("medislotrdv42"), any());
 }

 @Test
 void laDeconnexionSupprimeLAgendaRevoqueEtEfface() {
  when(links.find(USER_ID)).thenReturn(Optional.of(connectedLink()));
  when(oauthClient.refreshAccessToken("rafraichissement")).thenReturn("acces");
  service.disconnect(EMAIL);
  verify(calendarClient).deleteCalendar("acces", "agenda-medislot");
  verify(oauthClient).revoke("rafraichissement");
  verify(links).delete(USER_ID);
 }

 @Test
 void sansConfigurationGoogleLaFonctionEstDesactivee() {
  GoogleCalendarService disabledService = new GoogleCalendarService(GoogleTestProperties.disabled(), oauthClient,
    calendarClient, links, new TokenCipher(GoogleTestProperties.disabled()), users, appointments, slots, professionals);
  assertFalse(disabledService.status(EMAIL).enabled());
  disabledService.synchronize(42L);
  verifyNoInteractions(links, calendarClient);
 }
}
