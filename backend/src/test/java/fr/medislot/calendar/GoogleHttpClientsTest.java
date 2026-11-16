package fr.medislot.calendar;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriComponentsBuilder;

class GoogleHttpClientsTest {
 private static final String EVENTS_URI = GoogleTestProperties.CALENDAR_API_URI + "/calendars/agenda-medislot/events";

 @Test
 void lUrlDeConsentementDemandeLaPorteeMinimaleAvecPkce() {
  GoogleOAuthClient oauthClient = new GoogleOAuthClient(GoogleTestProperties.enabled(), RestClient.builder());
  Map<String, List<String>> parameters = UriComponentsBuilder
    .fromUriString(oauthClient.authorizationUrl("etat-aleatoire", "defi-pkce")).build().getQueryParams();
  assertEquals(GoogleOAuthClient.CALENDAR_SCOPE, URLDecoder.decode(parameters.get("scope").getFirst(), StandardCharsets.UTF_8));
  assertEquals("S256", parameters.get("code_challenge_method").getFirst());
  assertEquals("defi-pkce", parameters.get("code_challenge").getFirst());
  assertEquals("etat-aleatoire", parameters.get("state").getFirst());
  assertEquals("offline", parameters.get("access_type").getFirst());
 }

 @Test
 void lEchangeDuCodeEnvoieLeVerificateurPkce() {
  RestClient.Builder builder = RestClient.builder();
  MockRestServiceServer google = MockRestServiceServer.bindTo(builder).build();
  google.expect(requestTo(GoogleTestProperties.TOKEN_URI))
    .andExpect(method(HttpMethod.POST))
    .andExpect(content().formDataContains(Map.of("grant_type", "authorization_code", "code", "code-google", "code_verifier", "verificateur")))
    .andRespond(withSuccess("""
      {"access_token":"acces","refresh_token":"rafraichissement","scope":"https://www.googleapis.com/auth/calendar.app.created"}
      """, MediaType.APPLICATION_JSON));
  GoogleOAuthClient.GrantedTokens tokens = new GoogleOAuthClient(GoogleTestProperties.enabled(), builder)
    .exchangeCode("code-google", "verificateur");
  assertEquals("rafraichissement", tokens.refreshToken());
  assertTrue(tokens.includesCalendarScope());
  google.verify();
 }

 @Test
 void unAccesRetireCoteGoogleEstReconnu() {
  RestClient.Builder builder = RestClient.builder();
  MockRestServiceServer google = MockRestServiceServer.bindTo(builder).build();
  google.expect(requestTo(GoogleTestProperties.TOKEN_URI))
    .andRespond(withStatus(HttpStatus.BAD_REQUEST).contentType(MediaType.APPLICATION_JSON).body("{\"error\":\"invalid_grant\"}"));
  GoogleOAuthClient oauthClient = new GoogleOAuthClient(GoogleTestProperties.enabled(), builder);
  assertThrows(GoogleAccessRevokedException.class, () -> oauthClient.refreshAccessToken("rafraichissement"));
 }

 @Test
 void unEvenementAbsentEstCreeAvecSonIdentifiant() {
  RestClient.Builder builder = RestClient.builder();
  MockRestServiceServer google = MockRestServiceServer.bindTo(builder).build();
  google.expect(requestTo(EVENTS_URI + "/medislotrdv1"))
    .andExpect(method(HttpMethod.PUT))
    .andExpect(header("Authorization", "Bearer acces"))
    .andRespond(withStatus(HttpStatus.NOT_FOUND));
  google.expect(requestTo(EVENTS_URI))
    .andExpect(method(HttpMethod.POST))
    .andExpect(jsonPath("$.id").value("medislotrdv1"))
    .andRespond(withSuccess());
  new GoogleCalendarClient(GoogleTestProperties.enabled(), builder)
    .upsertEvent("acces", "agenda-medislot", "medislotrdv1", Map.of("summary", "Rendez-vous MediSlot"));
  google.verify();
 }

 @Test
 void unAgendaSupprimeParLePatientEstSignale() {
  RestClient.Builder builder = RestClient.builder();
  MockRestServiceServer google = MockRestServiceServer.bindTo(builder).build();
  google.expect(requestTo(EVENTS_URI + "/medislotrdv1")).andRespond(withStatus(HttpStatus.NOT_FOUND));
  google.expect(requestTo(EVENTS_URI)).andRespond(withStatus(HttpStatus.NOT_FOUND));
  GoogleCalendarClient calendarClient = new GoogleCalendarClient(GoogleTestProperties.enabled(), builder);
  Map<String, Object> event = Map.of("summary", "Rendez-vous MediSlot");
  assertThrows(GoogleCalendarClient.CalendarNotFoundException.class,
    () -> calendarClient.upsertEvent("acces", "agenda-medislot", "medislotrdv1", event));
 }

 @Test
 void supprimerUnEvenementDejaSupprimeNEstPasUneErreur() {
  RestClient.Builder builder = RestClient.builder();
  MockRestServiceServer google = MockRestServiceServer.bindTo(builder).build();
  google.expect(requestTo(EVENTS_URI + "/medislotrdv1"))
    .andExpect(method(HttpMethod.DELETE))
    .andRespond(withStatus(HttpStatus.GONE));
  new GoogleCalendarClient(GoogleTestProperties.enabled(), builder).deleteEvent("acces", "agenda-medislot", "medislotrdv1");
  google.verify();
 }
}
