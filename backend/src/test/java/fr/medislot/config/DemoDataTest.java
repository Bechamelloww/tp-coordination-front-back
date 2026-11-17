package fr.medislot.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import fr.medislot.domain.AppUser;
import fr.medislot.repository.ProfessionalRepository;
import fr.medislot.repository.SlotRepository;
import fr.medislot.repository.UserRepository;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.transaction.PlatformTransactionManager;

class DemoDataTest {
 static final String DEMO = "mot-de-passe-de-demo";
 final UserRepository users = mock(UserRepository.class);
 final JdbcTemplate jdbc = mock(JdbcTemplate.class);
 final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder(4);
 final DemoData seed = new DemoData(users, mock(ProfessionalRepository.class), mock(SlotRepository.class), encoder, jdbc,
   mock(PlatformTransactionManager.class), DEMO);

 AppUser account(String email, String password) {
  AppUser u = new AppUser();
  u.email = email;
  u.password = encoder.encode(password);
  return u;
 }

 @Test void anAddressRegisteredBySomeoneElseGetsNoDemoRole() {
  when(users.findByEmail(anyString())).thenReturn(Optional.empty());
  when(users.findByEmail("admin-a@medislot.test")).thenReturn(Optional.of(account("admin-a@medislot.test", "mot-de-passe-du-squatteur")));

  Set<String> demo = seed.seedAccounts();
  seed.seedMemberships(demo);

  verify(users, never()).save(org.mockito.ArgumentMatchers.argThat(u -> u.email.equals("admin-a@medislot.test")));
  verify(jdbc, never()).update(anyString(), eq("ADMIN"), eq("admin-a@medislot.test"), eq("cabinet-a"));
  verify(jdbc).update(anyString(), eq("ADMIN"), eq("admin-b@medislot.test"), eq("cabinet-b"));
 }

 @Test void anExistingDemoAccountKeepsItsRoles() {
  when(users.findByEmail(anyString())).thenReturn(Optional.empty());
  when(users.findByEmail("admin-a@medislot.test")).thenReturn(Optional.of(account("admin-a@medislot.test", DEMO)));

  Set<String> demo = seed.seedAccounts();
  seed.seedMemberships(demo);

  assertEquals(5, demo.size());
  verify(jdbc).update(anyString(), eq("ADMIN"), eq("admin-a@medislot.test"), eq("cabinet-a"));
  verify(users, never()).save(org.mockito.ArgumentMatchers.argThat(u -> u.email.equals("admin-a@medislot.test")));
  verify(users, org.mockito.Mockito.times(4)).save(any());
 }
}
