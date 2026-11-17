package fr.medislot;

import fr.medislot.domain.AppUser;
import fr.medislot.domain.Clinic;
import fr.medislot.domain.Membership;
import fr.medislot.repository.ClinicRepository;
import fr.medislot.repository.MembershipRepository;
import fr.medislot.repository.UserRepository;
import fr.medislot.tenant.TenantContext;
import fr.medislot.tenant.TenantFilter;
import jakarta.servlet.ServletException;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.context.SecurityContextHolder;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class TenantFilterTest {
 ClinicRepository clinics = mock(ClinicRepository.class);
 MembershipRepository memberships = mock(MembershipRepository.class);
 UserRepository users = mock(UserRepository.class);
 TenantFilter filter = new TenantFilter(clinics, memberships, users);
 MockHttpServletResponse res = new MockHttpServletResponse();
 AtomicReference<Long> seenInChain = new AtomicReference<>();
 AtomicReference<Boolean> adminInChain = new AtomicReference<>();

 @BeforeEach void clinicsAndUser() {
  Clinic a = clinic(1L, "cabinet-a");
  when(clinics.findBySlug("cabinet-a")).thenReturn(Optional.of(a));
  when(clinics.findBySlug("cabinet-b")).thenReturn(Optional.of(clinic(2L, "cabinet-b")));
  when(clinics.findBySlug("inconnu")).thenReturn(Optional.empty());
  AppUser u = new AppUser();
  u.id = 10L;
  when(users.findByEmail("camille@medislot.test")).thenReturn(Optional.of(u));
  when(memberships.findByUserIdAndClinicId(10L, 1L)).thenReturn(Optional.of(membership(10L, 1L, Membership.MEMBER)));
  AppUser admin = new AppUser();
  admin.id = 20L;
  when(users.findByEmail("admin@medislot.test")).thenReturn(Optional.of(admin));
  when(memberships.findByUserIdAndClinicId(20L, 1L)).thenReturn(Optional.of(membership(20L, 1L, Membership.ADMIN)));
  when(memberships.existsByUserIdAndRole(20L, Membership.ADMIN)).thenReturn(true);
  AppUser master = new AppUser();
  master.id = 30L;
  master.role = AppUser.MASTER;
  when(users.findByEmail("master@medislot.test")).thenReturn(Optional.of(master));
 }

 private Membership membership(Long user, Long clinic, String role) {
  Membership m = new Membership();
  m.userId = user;
  m.clinicId = clinic;
  m.role = role;
  return m;
 }

 @AfterEach void cleanUp() {
  SecurityContextHolder.clearContext();
  TenantContext.clear();
 }

 private Clinic clinic(Long id, String slug) {
  Clinic c = new Clinic();
  c.id = id;
  c.slug = slug;
  return c;
 }

 private MockHttpServletRequest request(String path, String slug) {
  MockHttpServletRequest req = new MockHttpServletRequest("GET", path);
  req.setRequestURI(path);
  if (slug != null) req.addHeader(TenantFilter.HEADER, slug);
  return req;
 }

 private void run(MockHttpServletRequest req) throws Exception {
  filter.doFilter(req, res, new MockFilterChain() {
   @Override public void doFilter(jakarta.servlet.ServletRequest rq, jakarta.servlet.ServletResponse rs) throws java.io.IOException, ServletException {
    seenInChain.set(TenantContext.get());
    adminInChain.set(SecurityContextHolder.getContext().getAuthentication() != null && SecurityContextHolder.getContext().getAuthentication().getAuthorities().stream().anyMatch(a -> a.getAuthority().equals(TenantFilter.CLINIC_ADMIN)));
    super.doFilter(rq, rs);
   }
  });
 }

 private void signIn(String email) {
  SecurityContextHolder.getContext().setAuthentication(UsernamePasswordAuthenticationToken.authenticated(email, "x", AuthorityUtils.createAuthorityList("ROLE_PATIENT")));
 }

 @Test void sansEnTeteLaReponseEst400() throws Exception {
  run(request("/api/professionals", null));
  assertEquals(400, res.getStatus());
  assertNull(seenInChain.get());
 }

 @Test void unSlugMalFormeEst400() throws Exception {
  run(request("/api/professionals", "Cabinet_A; drop"));
  assertEquals(400, res.getStatus());
 }

 @Test void unCabinetInconnuEst404() throws Exception {
  run(request("/api/professionals", "inconnu"));
  assertEquals(404, res.getStatus());
 }

 @Test void unAnonymePasseSurLAnnuaire() throws Exception {
  run(request("/api/professionals", "cabinet-a"));
  assertEquals(200, res.getStatus());
  assertEquals(1L, seenInChain.get());
 }

 @Test void unAnonymeSansDroitParticulierMaisAvecJetonAnonymePasse() throws Exception {
  SecurityContextHolder.getContext().setAuthentication(new AnonymousAuthenticationToken("k", "anonymousUser", AuthorityUtils.createAuthorityList("ROLE_ANONYMOUS")));
  run(request("/api/professionals", "cabinet-b"));
  assertEquals(2L, seenInChain.get());
 }

 @Test void unMembreAccedeASonCabinet() throws Exception {
  signIn("camille@medislot.test");
  run(request("/api/appointments", "cabinet-a"));
  assertEquals(200, res.getStatus());
  assertEquals(1L, seenInChain.get());
 }

 @Test void unPatientOuvreAussiUnCabinetDontIlNEstPasMembre() throws Exception {
  signIn("camille@medislot.test");
  run(request("/api/professionals", "cabinet-b"));
  assertEquals(200, res.getStatus());
  assertEquals(2L, seenInChain.get());
  assertFalse(adminInChain.get());
 }

 @Test void unCompteInconnuAUn404() throws Exception {
  signIn("fantome@medislot.test");
  run(request("/api/appointments", "cabinet-a"));
  assertEquals(404, res.getStatus());
  assertNull(seenInChain.get());
 }

 @Test void leCabinetPeutVenirDuParametrePourLesFluxSse() throws Exception {
  MockHttpServletRequest req = request("/api/events", null);
  req.setParameter(TenantFilter.PARAM, "cabinet-a");
  run(req);
  assertEquals(1L, seenInChain.get());
 }

 @Test void leContexteEstRetireApresLaRequete() throws Exception {
  run(request("/api/professionals", "cabinet-a"));
  assertNull(TenantContext.get());
 }

 @Test void leContexteEstRetireMemeSiLaSuiteDeLaChaineEchoue() {
  assertThrows(ServletException.class, () -> filter.doFilter(request("/api/professionals", "cabinet-a"), res, (rq, rs) -> {
   assertEquals(1L, TenantContext.get());
   throw new ServletException("échec");
  }));
  assertNull(TenantContext.get());
 }

 @Test void lesAppelsSansCabinetNeSontPasFiltres() throws Exception {
  for (String path : new String[]{"/api/csrf", "/api/clinics", "/api/directory", "/api/me/appointments", "/api/login", "/api/me", "/api/me/clinics", "/api/master/clinics", "/api/ab/assignment", "/api/google/callback", "/api/auth/google/start", "/actuator/health"}) {
   run(request(path, null));
   assertEquals(200, res.getStatus(), path);
  }
 }
 @Test void unMembreSimpleNARecoitPasLeRoleAdmin() throws Exception {
  signIn("camille@medislot.test");
  run(request("/api/appointments", "cabinet-a"));
  assertFalse(adminInChain.get());
 }

 @Test void lAdminDuCabinetRecoitLeRolePourCetteRequeteSeulement() throws Exception {
  signIn("admin@medislot.test");
  run(request("/api/admin/overview", "cabinet-a"));
  assertTrue(adminInChain.get());
  assertTrue(SecurityContextHolder.getContext().getAuthentication().getAuthorities().stream().noneMatch(a -> a.getAuthority().equals(TenantFilter.CLINIC_ADMIN)));
 }

 @Test void lAdminDUnCabinetNEntrePasDansUnAutre() throws Exception {
  signIn("admin@medislot.test");
  run(request("/api/admin/overview", "cabinet-b"));
  assertEquals(404, res.getStatus());
 }

 @Test void unMasterEntreDansTousLesCabinetsEnAdmin() throws Exception {
  signIn("master@medislot.test");
  run(request("/api/admin/overview", "cabinet-b"));
  assertEquals(2L, seenInChain.get());
  assertTrue(adminInChain.get());
 }
}
