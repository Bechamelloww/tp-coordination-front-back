package fr.medislot.tenant;

import fr.medislot.domain.AppUser;
import fr.medislot.domain.Clinic;
import fr.medislot.domain.Membership;
import fr.medislot.repository.ClinicRepository;
import fr.medislot.repository.MembershipRepository;
import fr.medislot.repository.UserRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Lit le cabinet demandé (en-tête X-Clinic, ou paramètre clinic pour les flux SSE qui ne peuvent pas poser d'en-tête),
 * vérifie que l'utilisateur connecté en est membre, puis le pose dans TenantContext le temps de la requête.
 * Pas un @Component : il est inséré dans la chaîne Spring Security (voir SecurityConfig).
 */
public class TenantFilter extends OncePerRequestFilter {
 public static final String HEADER = "X-Clinic";
 public static final String CLINIC_ADMIN = "ROLE_CLINIC_ADMIN";
 public static final String PARAM = "clinic";
 static final Pattern SLUG = Pattern.compile("^[a-z0-9]+(-[a-z0-9]+)*$");
 // Appels qui ne dépendent pas d'un cabinet : session, comptes, expériences A/B, retour OAuth Google.
 static final java.util.List<String> NO_CLINIC = java.util.List.of("/api/csrf", "/api/clinics", "/api/directory", "/api/login", "/api/logout", "/api/me", "/api/master/", "/api/ab/", "/api/google/", "/api/auth/google/");

 private final ClinicRepository clinics;
 private final MembershipRepository memberships;
 private final UserRepository users;

 public TenantFilter(ClinicRepository clinics, MembershipRepository memberships, UserRepository users) {
  this.clinics = clinics;
  this.memberships = memberships;
  this.users = users;
 }

 @Override protected boolean shouldNotFilter(HttpServletRequest req) {
  String path = req.getRequestURI();
  return !path.startsWith("/api/") || NO_CLINIC.stream().anyMatch(path::startsWith);
 }

 @Override protected void doFilterInternal(HttpServletRequest req, HttpServletResponse res, FilterChain chain) throws ServletException, IOException {
  String slug = Optional.ofNullable(req.getHeader(HEADER)).orElse(req.getParameter(PARAM));
  if (slug == null || !SLUG.matcher(slug).matches()) {
   problem(res, 400, "Cabinet manquant ou invalide.");
   return;
  }
  Optional<Clinic> clinic = clinics.findBySlug(slug);
  Access access = clinic.map(this::access).orElse(Access.NONE);
  if (access == Access.NONE) {
   problem(res, 404, "Cabinet introuvable.");
   return;
  }
  SecurityContext before = SecurityContextHolder.getContext();
  try {
   TenantContext.set(clinic.get().id);
   if (access == Access.ADMIN) grantClinicAdmin(before.getAuthentication());
   chain.doFilter(req, res);
  } finally {
   TenantContext.clear();
   SecurityContextHolder.setContext(before);
  }
 }

 enum Access { NONE, PUBLIC, MEMBER, ADMIN }

 private Access access(Clinic clinic) {
  Authentication auth = SecurityContextHolder.getContext().getAuthentication();
  if (auth == null || auth instanceof AnonymousAuthenticationToken) return Access.PUBLIC;
  Optional<AppUser> user = users.findByEmail(auth.getName());
  if (user.isEmpty()) return Access.NONE;
  if (AppUser.MASTER.equals(user.get().role)) return Access.ADMIN;
  Optional<Membership> membership = memberships.findByUserIdAndClinicId(user.get().id, clinic.id);
  if (membership.isPresent() && Membership.ADMIN.equals(membership.get().role)) return Access.ADMIN;
  // Gérant : limité aux cabinets qu'il administre (404 ailleurs, comme un cabinet inconnu).
  if (memberships.existsByUserIdAndRole(user.get().id, Membership.ADMIN)) return Access.NONE;
  // Patient : voit tous les cabinets ; il devient membre de celui où il réserve.
  return membership.isPresent() ? Access.MEMBER : Access.PUBLIC;
 }

 private void grantClinicAdmin(Authentication auth) {
  List<GrantedAuthority> authorities = new ArrayList<>(auth.getAuthorities());
  authorities.add(new SimpleGrantedAuthority(CLINIC_ADMIN));
  UsernamePasswordAuthenticationToken admin = UsernamePasswordAuthenticationToken.authenticated(auth.getPrincipal(), auth.getCredentials(), authorities);
  admin.setDetails(auth.getDetails());
  SecurityContext context = SecurityContextHolder.createEmptyContext();
  context.setAuthentication(admin);
  SecurityContextHolder.setContext(context);
 }

 private void problem(HttpServletResponse res, int status, String message) throws IOException {
  res.setStatus(status);
  res.setContentType("application/problem+json");
  res.getWriter().write("{\"title\":\"" + message + "\",\"status\":" + status + "}");
 }
}
