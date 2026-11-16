package fr.medislot.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import fr.medislot.calendar.GoogleOAuthClient;
import fr.medislot.calendar.GoogleProperties;
import fr.medislot.domain.AppUser;
import fr.medislot.repository.UserRepository;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;

class GoogleLoginServiceTest {
 private static final String EMAIL = "camille@gmail.com";
 private static final String SUB = "108123456789012345678";

 private final GoogleOAuthClient oauthClient = mock(GoogleOAuthClient.class);
 private final UserRepository users = mock(UserRepository.class);
 private final PasswordEncoder encoder = mock(PasswordEncoder.class);
 private final UserDetailsService userDetails = email -> User.withUsername(email).password("x").roles("PATIENT").build();
 private final GoogleLoginService service = new GoogleLoginService(enabled(), oauthClient, users, userDetails, encoder);
 private final MockHttpSession session = new MockHttpSession();
 private final MockHttpServletRequest request = new MockHttpServletRequest();
 private final MockHttpServletResponse response = new MockHttpServletResponse();

 private static GoogleProperties enabled() {
  return new GoogleProperties("client-id", "secret", "", "http://localhost/api/google/callback", "https://auth.test",
    "https://oauth.test/token", "https://oauth.test/revoke", "https://calendar.test");
 }

 @BeforeEach
 void setUp() {
  request.setSession(session);
  when(oauthClient.loginUrl(anyString(), anyString())).thenReturn("https://auth.test/login");
  when(encoder.encode(anyString())).thenReturn("{bcrypt}aleatoire");
 }

 @AfterEach
 void clearContext() {
  SecurityContextHolder.clearContext();
 }

 private String startAndReturnState() {
  service.start(session);
  return (String) session.getAttribute(GoogleLoginService.SESSION_STATE);
 }

 private void googleReturns(String email, boolean verified, String name) {
  String verifier = (String) session.getAttribute(GoogleLoginService.SESSION_CODE_VERIFIER);
  when(oauthClient.exchangeLoginCode("code", verifier)).thenReturn("jeton-acces");
  when(oauthClient.userInfo("jeton-acces")).thenReturn(new GoogleOAuthClient.GoogleIdentity(SUB, email, verified, name));
 }

 @Test
 void unNouveauCompteGoogleCreeUnPatientEtOuvreLaSession() {
  String state = startAndReturnState();
  googleReturns("Camille@Gmail.com", true, "Camille Dupont");
  when(users.findByEmail(EMAIL)).thenReturn(Optional.empty());
  when(users.saveAndFlush(any())).thenAnswer(call -> call.getArgument(0));
  String sessionIdBefore = session.getId();

  assertEquals(GoogleLoginService.Outcome.CONNECTE, service.complete(request, response, state, "code", null));

  ArgumentCaptor<AppUser> saved = ArgumentCaptor.forClass(AppUser.class);
  verify(users).saveAndFlush(saved.capture());
  assertEquals(EMAIL, saved.getValue().email);
  assertEquals("Camille Dupont", saved.getValue().name);
  assertEquals("PATIENT", saved.getValue().role);
  SecurityContext stored = (SecurityContext) request.getSession()
    .getAttribute(HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY);
  assertNotNull(stored);
  assertEquals(EMAIL, stored.getAuthentication().getName());
  assertNotEquals(sessionIdBefore, request.getSession().getId());
  verify(oauthClient).revoke("jeton-acces");
 }

 @Test
 void unCompteExistantEstRetrouveParSonEmail() {
  String state = startAndReturnState();
  googleReturns(EMAIL, true, "Camille");
  AppUser existing = new AppUser();
  existing.email = EMAIL;
  when(users.findByEmail(EMAIL)).thenReturn(Optional.of(existing));

  assertEquals(GoogleLoginService.Outcome.CONNECTE, service.complete(request, response, state, "code", null));
  verify(users, never()).saveAndFlush(any());
 }

 @Test
 void unEmailNonVerifieParGoogleEstRefuse() {
  String state = startAndReturnState();
  googleReturns(EMAIL, false, "Camille");

  assertEquals(GoogleLoginService.Outcome.REFUSE, service.complete(request, response, state, "code", null));
  verifyNoInteractions(users);
  assertNull(SecurityContextHolder.getContext().getAuthentication());
 }

 @Test
 void unStateDifferentOuRejoueEstRefuseSansAppelerGoogle() {
  String state = startAndReturnState();
  assertEquals(GoogleLoginService.Outcome.ERREUR, service.complete(request, response, "autre", "code", null));
  assertEquals(GoogleLoginService.Outcome.ERREUR, service.complete(request, response, state, "code", null));
  verify(oauthClient, never()).exchangeLoginCode(anyString(), anyString());
 }

 @Test
 void unRefusSurLEcranGoogleRenvoieRefuse() {
  String state = startAndReturnState();
  assertEquals(GoogleLoginService.Outcome.REFUSE, service.complete(request, response, state, null, "access_denied"));
  verify(oauthClient, never()).exchangeLoginCode(anyString(), eq("code"));
 }

 @Test
 void leNomAfficheRetombeSurLaPartieLocaleDeLEmail() {
  assertEquals("camille", GoogleLoginService.displayName(" ", EMAIL));
  assertEquals(120, GoogleLoginService.displayName("x".repeat(300), EMAIL).length());
 }
}
