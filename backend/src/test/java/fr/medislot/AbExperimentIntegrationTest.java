package fr.medislot;
import fr.medislot.application.BookingService;
import fr.medislot.domain.AppUser;
import fr.medislot.experiment.AbService;
import fr.medislot.experiment.AbTokens;
import fr.medislot.repository.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.server.ResponseStatusException;
import java.time.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
class AbExperimentIntegrationTest extends PostgresTestBase {
 @Autowired AbService ab; @Autowired AbTokens tokens; @Autowired BookingService booking; @Autowired UserRepository users; @Autowired SlotRepository slots;
 private AppUser user(String email){AppUser u=new AppUser();u.email=email;u.name="Patient test";u.password="non-utilisé";u.role="PATIENT";return users.save(u);}
 private AbService.VariantMetrics metrics(String variant){return ab.metrics().variants().stream().filter(m->m.variant().equals(variant)).findFirst().orElseThrow();}
 private String variantOf(String token){return tokens.verify(token).orElseThrow().variant();}

 @Test void tenFirstUsersOnlyBalancedExposureAndConversionCountedOnce(){
  // 10 premiers : tous reçoivent un jeton, 5 A et 5 B ; le 11e n'a pas de jeton (A, non compté).
  List<AppUser> enrolled=new ArrayList<>();List<String> tokenList=new ArrayList<>(),variants=new ArrayList<>();
  for(int i=0;i<10;i++){AppUser u=user("ab"+i+"@medislot.test");var a=ab.assign(u.id);assertTrue(a.participating());enrolled.add(u);tokenList.add(a.token());variants.add(variantOf(a.token()));}
  assertEquals(5,Collections.frequency(variants,"A"));
  var eleventh=ab.assign(user("ab10@medislot.test").id);
  assertFalse(eleventh.participating());assertNull(eleventh.token());
  assertEquals(10,ab.metrics().participants());

  // Affectation stable : chaque nouveau jeton porte la même variante.
  AppUser first=enrolled.get(0),second=enrolled.get(1),third=enrolled.get(2);String v=variants.get(0),token=tokenList.get(0);
  for(int i=0;i<3;i++)assertEquals(v,variantOf(ab.assign(first.id).token()));

  // Jeton absent ou appartenant à un autre patient : 400, rien n'est compté.
  assertEquals(400,assertThrows(ResponseStatusException.class,()->ab.expose(first.id,null)).getStatusCode().value());
  assertEquals(400,assertThrows(ResponseStatusException.class,()->ab.expose(first.id,tokenList.get(1))).getStatusCode().value());
  assertEquals(0,metrics(v).exposed());

  // Expositions répétées avec le bon jeton : une seule comptée.
  ab.expose(first.id,token);ab.expose(first.id,token);
  assertEquals(1,metrics(v).exposed());

  // Deux réservations du même patient exposé : une seule conversion.
  List<Long> free=slots.available(3L,Instant.now(),Instant.now().plus(Duration.ofDays(14))).stream().map(s->s.id).toList();
  // Deuxième réservation chez un autre praticien : un seul rendez-vous à venir par praticien.
  Long elsewhere=slots.available(5L,Instant.now(),Instant.now().plus(Duration.ofDays(14))).getFirst().id;
  booking.book(first.email,free.get(0),token);booking.book(first.email,elsewhere,token);
  assertEquals(1,metrics(v).converted());
  assertEquals(1.0,metrics(v).rate());

  // Réservation sans exposition : acceptée, non attribuée.
  String v2=variants.get(1);int before=metrics(v2).converted();
  booking.book(second.email,free.get(2),tokenList.get(1));
  assertEquals(before,metrics(v2).converted());

  // Réservation sans jeton par un patient exposé : acceptée, non attribuée.
  String v3=variants.get(2),token3=tokenList.get(2);ab.expose(third.id,token3);
  int beforeNoToken=metrics(v3).converted();
  booking.book(third.email,free.get(3),null);
  assertEquals(beforeNoToken,metrics(v3).converted());

  // 409 : aucune conversion liée au refus.
  int convertedBefore=metrics(v3).converted();
  assertEquals(409,assertThrows(ResponseStatusException.class,()->booking.book(third.email,free.get(0),token3)).getStatusCode().value());
  assertEquals(convertedBefore,metrics(v3).converted());
 }
}
