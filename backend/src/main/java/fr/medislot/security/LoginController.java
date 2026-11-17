package fr.medislot.security;

import fr.medislot.repository.UserRepository;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.Map;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.mail.MailException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.security.web.csrf.HttpSessionCsrfTokenRepository;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/login")
public class LoginController {
 public record CodeRequest(String code, Boolean remember) {}

 private final DaoAuthenticationProvider passwords;
 private final UserDetailsService userDetails;
 private final LoginAttempts attempts;
 private final LoginCodes codes;
 private final TrustedDevices devices;
 private final UserRepository users;
 private final SecurityContextRepository contextRepository = new HttpSessionSecurityContextRepository();
 private final HttpSessionCsrfTokenRepository csrfTokens = new HttpSessionCsrfTokenRepository();

 public LoginController(UserDetailsService userDetails, PasswordEncoder encoder, LoginAttempts attempts, LoginCodes codes,
   TrustedDevices devices, UserRepository users) {
  this.passwords = new DaoAuthenticationProvider(userDetails);
  this.passwords.setPasswordEncoder(encoder);
  this.userDetails = userDetails;
  this.attempts = attempts;
  this.codes = codes;
  this.devices = devices;
  this.users = users;
 }

 @PostMapping
 public ResponseEntity<?> password(HttpServletRequest request, HttpServletResponse response,
   @RequestParam(name = "username", required = false) String username,
   @RequestParam(name = "password", required = false) String password) {
  if (attempts.blocked(username)) {
   return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
     .header("Retry-After", String.valueOf(LoginAttempts.WINDOW.toSeconds()))
     .body(problem(HttpStatus.TOO_MANY_REQUESTS, "Trop de tentatives. Réessayez dans 15 minutes.", null));
  }
  Authentication authenticated;
  try {
   authenticated = passwords.authenticate(UsernamePasswordAuthenticationToken.unauthenticated(username, password));
  } catch (AuthenticationException rejected) {
   attempts.failed(username);
   return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(problem(HttpStatus.UNAUTHORIZED, "Identifiants incorrects", null));
  }
  attempts.succeeded(authenticated.getName());
  if (devices.trusted(userId(authenticated.getName()), request)) {
   signIn(authenticated.getName(), request, response);
   return ResponseEntity.noContent().build();
  }
  try {
   codes.send(request.getSession(), authenticated.getName());
  } catch (MailException unavailable) {
   return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
     .body(problem(HttpStatus.SERVICE_UNAVAILABLE, "Le code de connexion n'a pas pu être envoyé. Réessayez dans quelques minutes.", null));
  }
  return ResponseEntity.status(HttpStatus.ACCEPTED).body(Map.of("etape", "code", "destination", LoginCodes.masked(authenticated.getName())));
 }

 @PostMapping("/code")
 public ResponseEntity<?> code(HttpServletRequest request, HttpServletResponse response, @RequestBody CodeRequest body) {
  String code = body.code() == null ? null : body.code().strip();
  LoginCodes.Result result = codes.verify(request.getSession(false), code);
  return switch (result.outcome()) {
   case VALID -> {
    signIn(result.email(), request, response);
    var ok = ResponseEntity.noContent();
    if (Boolean.TRUE.equals(body.remember())) ok.header(HttpHeaders.SET_COOKIE, devices.remember(userId(result.email())).toString());
    yield ok.build();
   }
   case WRONG -> unauthorized("Code incorrect.", "CODE_INCORRECT");
   case TOO_MANY_TRIES -> unauthorized("Trop d'essais. Reconnectez-vous pour recevoir un nouveau code.", "CODE_EPUISE");
   case EXPIRED -> unauthorized("Le code a expiré. Reconnectez-vous pour recevoir un nouveau code.", "CODE_EXPIRE");
  };
 }

 private long userId(String email) {
  return users.findByEmail(email).orElseThrow().id;
 }

 private void signIn(String email, HttpServletRequest request, HttpServletResponse response) {
  UserDetails principal = userDetails.loadUserByUsername(email);
  request.changeSessionId();
  csrfTokens.saveToken(null, request, response);
  SecurityContext context = SecurityContextHolder.createEmptyContext();
  context.setAuthentication(UsernamePasswordAuthenticationToken.authenticated(principal, null, principal.getAuthorities()));
  SecurityContextHolder.setContext(context);
  contextRepository.saveContext(context, request, response);
 }

 private static ResponseEntity<ProblemDetail> unauthorized(String title, String code) {
  return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(problem(HttpStatus.UNAUTHORIZED, title, code));
 }

 private static ProblemDetail problem(HttpStatus status, String title, String code) {
  ProblemDetail problem = ProblemDetail.forStatus(status);
  problem.setTitle(title);
  if (code != null) problem.setProperty("code", code);
  return problem;
 }
}
