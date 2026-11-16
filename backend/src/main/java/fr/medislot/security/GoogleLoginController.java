package fr.medislot.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import java.net.URI;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth/google")
public class GoogleLoginController {
 private static final Logger log = LoggerFactory.getLogger(GoogleLoginController.class);
 private final GoogleLoginService loginService;

 public GoogleLoginController(GoogleLoginService loginService) {
  this.loginService = loginService;
 }

 /** Le front n'affiche le bouton « Continuer avec Google » que si la connexion est configurée. */
 @GetMapping("/enabled")
 public Map<String, Boolean> enabled() {
  return Map.of("enabled", loginService.enabled());
 }

 /** POST protégé par le jeton CSRF : aucun site tiers ne peut lancer une connexion. */
 @PostMapping("/start")
 public ResponseEntity<Map<String, String>> start(HttpSession session) {
  return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(Map.of("url", loginService.start(session)));
 }

 @GetMapping("/callback")
 public ResponseEntity<Void> callback(HttpServletRequest request, HttpServletResponse response,
   @RequestParam(name = "state", required = false) String state,
   @RequestParam(name = "code", required = false) String code,
   @RequestParam(name = "error", required = false) String error) {
  GoogleLoginService.Outcome outcome;
  try {
   outcome = loginService.complete(request, response, state, code, error);
  } catch (RuntimeException failure) {
   log.warn("Connexion Google impossible : {}", failure.getClass().getSimpleName());
   outcome = GoogleLoginService.Outcome.ERREUR;
  }
  return ResponseEntity.status(HttpStatus.FOUND)
    .location(URI.create("/?connexion=" + outcome.queryValue()))
    .cacheControl(CacheControl.noStore())
    .header("Referrer-Policy", "no-referrer")
    .build();
 }
}
