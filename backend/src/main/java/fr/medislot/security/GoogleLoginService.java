package fr.medislot.security;

import fr.medislot.calendar.GoogleCalendarService;
import fr.medislot.calendar.GoogleOAuthClient;
import fr.medislot.calendar.GoogleProperties;
import fr.medislot.domain.AppUser;
import fr.medislot.repository.UserRepository;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.Locale;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

/**
 * Connexion « Continuer avec Google » (OpenID Connect, code d'autorisation + PKCE S256 + state à usage unique).
 * <p>
 * Le compte MediSlot est retrouvé par l'adresse e-mail, à condition que Google l'ait vérifiée ; sinon un compte patient
 * est créé, avec un mot de passe aléatoire inutilisable. Aucun jeton Google n'est conservé : le jeton d'accès sert à lire
 * l'identité, puis il est révoqué. La session change d'identifiant à la connexion (fixation de session).
 */
@Service
public class GoogleLoginService {
 static final String SESSION_STATE = "google.login.state";
 static final String SESSION_CODE_VERIFIER = "google.login.codeVerifier";
 private static final int RANDOM_LENGTH_BYTES = 32;
 private static final int NAME_MAX = 120;
 private static final Logger log = LoggerFactory.getLogger(GoogleLoginService.class);
 private static final Base64.Encoder URL_ENCODER = Base64.getUrlEncoder().withoutPadding();

 public enum Outcome {
  CONNECTE("google"), REFUSE("refuse"), ERREUR("erreur");

  private final String queryValue;

  Outcome(String queryValue) {
   this.queryValue = queryValue;
  }

  public String queryValue() {
   return queryValue;
  }
 }

 private final GoogleProperties properties;
 private final GoogleOAuthClient oauthClient;
 private final UserRepository users;
 private final UserDetailsService userDetails;
 private final PasswordEncoder encoder;
 private final SecurityContextRepository contextRepository = new HttpSessionSecurityContextRepository();
 private final SecureRandom random = new SecureRandom();

 public GoogleLoginService(GoogleProperties properties, GoogleOAuthClient oauthClient, UserRepository users,
   UserDetailsService userDetails, PasswordEncoder encoder) {
  this.properties = properties;
  this.oauthClient = oauthClient;
  this.users = users;
  this.userDetails = userDetails;
  this.encoder = encoder;
 }

 public boolean enabled() {
  return properties.enabled();
 }

 public String start(HttpSession session) {
  if (!properties.enabled()) {
   throw new ResponseStatusException(HttpStatus.NOT_FOUND, "La connexion avec Google n'est pas activée.");
  }
  String state = randomUrlSafe();
  String codeVerifier = randomUrlSafe();
  session.setAttribute(SESSION_STATE, state);
  session.setAttribute(SESSION_CODE_VERIFIER, codeVerifier);
  return oauthClient.loginUrl(state, GoogleCalendarService.codeChallenge(codeVerifier));
 }

 public Outcome complete(HttpServletRequest request, HttpServletResponse response, String returnedState,
   String authorizationCode, String googleError) {
  HttpSession session = request.getSession(false);
  String expectedState = session == null ? null : (String) session.getAttribute(SESSION_STATE);
  String codeVerifier = session == null ? null : (String) session.getAttribute(SESSION_CODE_VERIFIER);
  if (session != null) {
   session.removeAttribute(SESSION_STATE);
   session.removeAttribute(SESSION_CODE_VERIFIER);
  }
  if (!properties.enabled() || !sameState(expectedState, returnedState) || codeVerifier == null) {
   return Outcome.ERREUR;
  }
  if (googleError != null) {
   return Outcome.REFUSE;
  }
  if (authorizationCode == null || authorizationCode.isBlank()) {
   return Outcome.ERREUR;
  }
  String accessToken = oauthClient.exchangeLoginCode(authorizationCode, codeVerifier);
  if (accessToken == null) {
   return Outcome.ERREUR;
  }
  GoogleOAuthClient.GoogleIdentity identity;
  try {
   identity = oauthClient.userInfo(accessToken);
  } finally {
   revokeQuietly(accessToken);
  }
  if (identity.email() == null || identity.email().isBlank() || !identity.emailVerified()) {
   return Outcome.REFUSE;
  }
  AppUser user = findOrCreate(identity);
  signIn(user.email, request, response);
  return Outcome.CONNECTE;
 }

 private AppUser findOrCreate(GoogleOAuthClient.GoogleIdentity identity) {
  String email = identity.email().strip().toLowerCase(Locale.ROOT);
  return users.findByEmail(email).orElseGet(() -> {
   AppUser created = new AppUser();
   created.email = email;
   created.name = displayName(identity.name(), email);
   created.role = "PATIENT";
   created.password = encoder.encode(randomUrlSafe());
   try {
    return users.saveAndFlush(created);
   } catch (DataIntegrityViolationException createdConcurrently) {
    return users.findByEmail(email).orElseThrow();
   }
  });
 }

 private void signIn(String email, HttpServletRequest request, HttpServletResponse response) {
  UserDetails principal = userDetails.loadUserByUsername(email);
  request.changeSessionId();
  SecurityContext context = SecurityContextHolder.createEmptyContext();
  context.setAuthentication(
    UsernamePasswordAuthenticationToken.authenticated(principal, null, principal.getAuthorities()));
  SecurityContextHolder.setContext(context);
  contextRepository.saveContext(context, request, response);
 }

 private void revokeQuietly(String token) {
  try {
   oauthClient.revoke(token);
  } catch (RuntimeException failure) {
   log.warn("Révocation du jeton de connexion Google impossible : {}", failure.getClass().getSimpleName());
  }
 }

 static String displayName(String googleName, String email) {
  String name = googleName == null || googleName.isBlank() ? email.substring(0, email.indexOf('@')) : googleName.strip();
  return name.length() > NAME_MAX ? name.substring(0, NAME_MAX) : name;
 }

 private String randomUrlSafe() {
  byte[] bytes = new byte[RANDOM_LENGTH_BYTES];
  random.nextBytes(bytes);
  return URL_ENCODER.encodeToString(bytes);
 }

 private static boolean sameState(String expectedState, String returnedState) {
  if (expectedState == null || returnedState == null) {
   return false;
  }
  return MessageDigest.isEqual(expectedState.getBytes(StandardCharsets.UTF_8), returnedState.getBytes(StandardCharsets.UTF_8));
 }
}
