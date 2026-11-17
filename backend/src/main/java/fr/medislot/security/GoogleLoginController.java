package fr.medislot.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import fr.medislot.activity.Activity;
import fr.medislot.activity.ActivityLog;
import fr.medislot.domain.Clinic;
import fr.medislot.domain.Membership;
import fr.medislot.repository.ClinicRepository;
import fr.medislot.repository.MembershipRepository;
import fr.medislot.repository.UserRepository;
import fr.medislot.tenant.TenantFilter;
import jakarta.servlet.http.HttpSession;
import java.net.URI;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth/google")
public class GoogleLoginController {
 private static final Logger log = LoggerFactory.getLogger(GoogleLoginController.class);
 private static final String SESSION_CLINIC = "medislot.login.clinic";
 private final GoogleLoginService loginService;
 private final ClinicRepository clinics;
 private final MembershipRepository memberships;
 private final UserRepository users;
 private final ActivityLog activityLog;

 public GoogleLoginController(GoogleLoginService loginService, ClinicRepository clinics, MembershipRepository memberships,
   UserRepository users, ActivityLog activityLog) {
  this.loginService = loginService;
  this.clinics = clinics;
  this.memberships = memberships;
  this.users = users;
  this.activityLog = activityLog;
 }

 /** Le front n'affiche le bouton « Continuer avec Google » que si la connexion est configurée. */
 @GetMapping("/enabled")
 public Map<String, Boolean> enabled() {
  return Map.of("enabled", loginService.enabled());
 }

 /** POST protégé par le jeton CSRF : aucun site tiers ne peut lancer une connexion. */
 @PostMapping("/start")
 public ResponseEntity<Map<String, String>> start(HttpSession session,
   @RequestHeader(name = TenantFilter.HEADER, required = false) String clinic) {
  // Google renvoie le navigateur sans en-tête : on garde le cabinet d'origine en session
  if (clinic != null) {
   session.setAttribute(SESSION_CLINIC, clinic);
  }
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
  String slug = null;
  if (outcome == GoogleLoginService.Outcome.CONNECTE) {
   activityLog.recordByEmail(null, SecurityContextHolder.getContext().getAuthentication().getName(), Activity.LOGIN, "Google");
   slug = joinClinic(request);
  }
  return ResponseEntity.status(HttpStatus.FOUND)
    .location(URI.create((slug == null ? "/" : "/c/" + slug + "/") + "?connexion=" + outcome.queryValue()))
    .cacheControl(CacheControl.noStore())
    .header("Referrer-Policy", "no-referrer")
    .build();
 }

 /**
  * Un compte créé par Google n'appartient encore à aucun cabinet : il rejoint celui d'où il vient (comme à l'inscription).
  * Un compte existant garde ses cabinets. Rend le slug à rouvrir, ou null s'il n'est pas membre de celui-ci.
  */
 private String joinClinic(HttpServletRequest request) {
  HttpSession session = request.getSession(false);
  String slug = session == null ? null : (String) session.getAttribute(SESSION_CLINIC);
  Clinic clinic = slug == null ? null : clinics.findBySlug(slug).orElse(null);
  var user = users.findByEmail(SecurityContextHolder.getContext().getAuthentication().getName()).orElseThrow();
  if (clinic != null && memberships.clinicsOf(user.id).isEmpty()) {
   Membership membership = new Membership();
   membership.userId = user.id;
   membership.clinicId = clinic.id;
   memberships.save(membership);
   activityLog.record(clinic.id, user.id, Activity.ACCOUNT_CREATED, "Google");
  }
  return clinic != null && memberships.existsByUserIdAndClinicId(user.id, clinic.id) ? clinic.slug : null;
 }
}
