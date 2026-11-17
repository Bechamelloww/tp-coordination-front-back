package fr.medislot.config;
import fr.medislot.domain.*;
import fr.medislot.repository.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import fr.medislot.tenant.TenantContext;
import java.time.*;
@Component @ConditionalOnProperty(name="medislot.demo-seed",havingValue="true")
public class DemoData implements CommandLineRunner {
 private final UserRepository users;private final ProfessionalRepository professionals;private final SlotRepository slots;private final PasswordEncoder encoder;private final JdbcTemplate jdbc;private final TransactionTemplate tx;private final String demoPassword;
 // java:S6437 : le mot de passe des comptes de démo vient de la configuration (DEMO_PASSWORD), jamais du code.
 public DemoData(UserRepository u,ProfessionalRepository p,SlotRepository s,PasswordEncoder e,JdbcTemplate jdbc,PlatformTransactionManager txManager,@Value("${medislot.demo-password:}") String demoPassword){
  if(demoPassword.isBlank())throw new IllegalStateException("medislot.demo-seed=true exige DEMO_PASSWORD.");
  users=u;professionals=p;slots=s;encoder=e;this.jdbc=jdbc;this.tx=new TransactionTemplate(txManager);this.demoPassword=demoPassword;}
 @Override public void run(String...args){
  seedMemberships(seedAccounts());
  seedClinic("cabinet-a",ROWS_A);
  seedClinic("cabinet-b",ROWS_B);
 }
 static final String[][] ACCOUNTS={{"patient@medislot.test","Camille Dupont","PATIENT"},{"autre@medislot.test","Alex Martin","PATIENT"},
  {"admin-a@medislot.test","Claire Rousseau","PATIENT"},{"admin-b@medislot.test","Marc Bernard","PATIENT"},{"master@medislot.test","Équipe MediSlot","MASTER"}};
 /** Jours de créneaux tenus à disposition : la fenêtre affichée par GET /api/professionals/{id}/slots (14 jours). */
 static final int DAYS_AHEAD=14;
 static final int[] HOURS={9,10,11,14,15,16};
 /**
  * Complète, à chaque démarrage, les créneaux des DAYS_AHEAD prochains jours qui manquent.
  * Une base de démo ancienne retrouve ainsi des disponibilités ; les créneaux existants et leurs rendez-vous ne bougent pas.
  */
 private void topUpSlots(Professional p){
  ZoneId paris=ZoneId.of("Europe/Paris");LocalDate today=LocalDate.now(paris);
  var existing=new java.util.HashSet<>(slots.startsFrom(p.id,today.plusDays(1).atStartOfDay(paris).toInstant()));
  for(int day=1;day<=DAYS_AHEAD;day++)for(int hour:HOURS){
   Instant start=today.plusDays(day).atTime(hour,0).atZone(paris).toInstant();
   if(existing.contains(start))continue;
   Slot s=new Slot();s.professionalId=p.id;s.startsAt=start;s.endsAt=start.plusSeconds(1800);slots.save(s);
  }
 }
 static final String[][] ROWS_A={{"Dr Sarah Martin","general","Paris","18 avenue de la République","48.866400","2.370000"},{"Dr Thomas Leroy","cardiology","Paris","42 rue de Rennes","48.851300","2.330500"},{"Dr Amel Benali","dermatology","Lyon","6 rue de la Charité","45.756800","4.831900"},{"Dr Hugo Petit","general","Lyon","21 cours Lafayette","45.763688","4.844596"},{"Dr Léa Moreau","dentistry","Paris","12 boulevard Saint-Germain","48.849564","2.354708"},{"Dr Inès Diallo","general","Bordeaux","8 cours de Verdun","44.845525","-0.577436"},
   {"Dr Valérie Fontaine","cardiology","Lyon","Place de la Croix-Rousse","45.774300","4.831800"},{"Dr Victor Roux","general","Lyon","Place Saint-Jean","45.760900","4.827400"},{"Dr Yasmine Haddad","dentistry","Lyon","Place Jean Jaurès","45.746600","4.841000"},{"Dr Yann Lambert","cardiology","Villeurbanne","Place Lazare-Goujon","45.766700","4.880000"},{"Dr Zoé Garnier","dermatology","Vénissieux","Place Léon-Sublet","45.697500","4.886800"}};
 static final String[][] ROWS_B={{"Dr Camille Perrin","general","Lyon","14 quai Claude-Bernard","45.749700","4.839600"},{"Dr Nathan Berger","cardiology","Lyon","3 rue de Marseille","45.749000","4.842000"},{"Dr Élise Vidal","dermatology","Lyon","27 avenue Berthelot","45.746700","4.842000"}};
 // la session Hibernate garde le cabinet présent à l'ouverture de la transaction : on le pose avant
 private void seedClinic(String slug,String[][] rows){
  TenantContext.set(jdbc.queryForObject("select id from clinic where slug=?",Long.class,slug));
  try{tx.executeWithoutResult(status->{seedProfessionals(rows);for(Professional p:professionals.findAll())topUpSlots(p);});}
  finally{TenantContext.clear();}
 }
 // patient@ est membre des deux cabinets, autre@ seulement de cabinet-a
 java.util.Set<String> seedAccounts(){
  var demo=new java.util.HashSet<String>();
  for(String[] a:ACCOUNTS){
   var existing=users.findByEmail(a[0]);
   if(existing.isEmpty()){AppUser u=new AppUser();u.email=a[0];u.name=a[1];u.role=a[2];u.password=encoder.encode(demoPassword);users.save(u);demo.add(a[0]);}
   else if(encoder.matches(demoPassword,existing.get().password))demo.add(a[0]);
  }
  return demo;
 }
 void seedMemberships(java.util.Set<String> demo){
  for(String[] m:new String[][]{{"patient@medislot.test","cabinet-a","MEMBER"},{"patient@medislot.test","cabinet-b","MEMBER"},{"autre@medislot.test","cabinet-a","MEMBER"},{"admin-a@medislot.test","cabinet-a","ADMIN"},{"admin-b@medislot.test","cabinet-b","ADMIN"}})
   if(demo.contains(m[0]))jdbc.update("insert into membership(user_id,clinic_id,role) select u.id,c.id,? from app_user u,clinic c where u.email=? and c.slug=? on conflict do nothing",m[2],m[0],m[1]);
 }
 private void seedProfessionals(String[][] rows){
  // Positions : mêmes valeurs que la migration V5 (géocodage OpenStreetMap, données fictives).
  // Les cinq derniers (TP géolocalisation) : places publiques de l'agglomération lyonnaise. Depuis la place Bellecour
  // (45.7576, 4.8317), six praticiens sont à moins de 5 km ; Vénissieux (≈ 7,9 km) reste hors du rayon.
  // Leurs noms se classent après les six premiers : la première page de l'annuaire ne change pas.
  // Ajoutés aussi aux bases de démo existantes : chaque ligne absente (même nom, même adresse) est créée.
  for(String[] row:rows){if(professionals.existsByNameAndAddress(row[0],row[3]))continue;Professional p=new Professional();p.name=row[0];p.specialty=row[1];p.city=row[2];p.address=row[3];p.latitude=Double.valueOf(row[4]);p.longitude=Double.valueOf(row[5]);p.bio="Un accompagnement attentif, dans un cabinet accessible. Les informations de ce profil sont fictives et servent à la formation MediSlot.";p.fee=row[1].equals("general")?30:60;professionals.save(p);}
 }
}
