package fr.medislot.convocation;
import fr.medislot.domain.*;
import fr.medislot.repository.*;
import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
/** Droits d'abord, fabrication ensuite ; heure de Paris en hiver comme en été. */
class ConvocationServiceTest {
 UserRepository users=mock(UserRepository.class);AppointmentRepository appointments=mock(AppointmentRepository.class);
 SlotRepository slots=mock(SlotRepository.class);ProfessionalRepository professionals=mock(ProfessionalRepository.class);
 PdfRenderer renderer=mock(PdfRenderer.class);
 ConvocationService service=new ConvocationService(users,appointments,slots,professionals,renderer);
 Appointment appointment;Slot slot;

 @BeforeEach void data(){
  AppUser helene=user(1L,"helene@medislot.test","Hélène Durand");AppUser autre=user(2L,"autre@medislot.test","Alex Martin");
  when(users.findByEmail(helene.email)).thenReturn(Optional.of(helene));when(users.findByEmail(autre.email)).thenReturn(Optional.of(autre));
  appointment=new Appointment();appointment.id=42L;appointment.patientId=1L;appointment.slotId=7L;appointment.status="CONFIRMED";
  slot=new Slot();slot.id=7L;slot.professionalId=3L;slot.startsAt=Instant.parse("2027-01-12T08:30:00Z");
  Professional p=new Professional();p.id=3L;p.name="Dr Sarah Martin";p.specialty="general";p.address="18 avenue de la République";p.city="Paris";
  when(appointments.findById(42L)).thenReturn(Optional.of(appointment));when(slots.findById(7L)).thenReturn(Optional.of(slot));when(professionals.findById(3L)).thenReturn(Optional.of(p));
  when(renderer.render(anyString(),anyString(),any())).thenReturn("%PDF-1.7".getBytes());
 }
 static AppUser user(Long id,String email,String name){AppUser u=new AppUser();u.id=id;u.email=email;u.name=name;return u;}
 int status(Runnable r){return assertThrows(ResponseStatusException.class,r::run).getStatusCode().value();}

 @Test void unRendezVousDUnAutrePatientRepond404SansFabriquerDePdf(){
  assertEquals(404,status(()->service.build("autre@medislot.test",42L)));
  verifyNoInteractions(renderer);
 }
 @Test void unNumeroInconnuRepond404(){
  assertEquals(404,status(()->service.build("helene@medislot.test",999L)));
  verifyNoInteractions(renderer);
 }
 @Test void unRendezVousAnnuleRepond409SansFabriquerDePdf(){
  appointment.status="CANCELLED";
  var e=assertThrows(ResponseStatusException.class,()->service.build("helene@medislot.test",42L));
  assertEquals(409,e.getStatusCode().value());assertEquals(ConvocationService.CANCELLED,e.getReason());
  verifyNoInteractions(renderer);
 }
 @Test void leRenduEnEchecDevientUn500Lisible(){
  when(renderer.render(anyString(),anyString(),any())).thenThrow(new IllegalStateException("RenderingFailed"));
  var e=assertThrows(ResponseStatusException.class,()->service.build("helene@medislot.test",42L));
  assertEquals(500,e.getStatusCode().value());assertEquals(ConvocationService.FAILED,e.getReason());
 }
 @Test void leProprietaireRecoitLePdfAvecLesDonneesMisesEnForme(){
  assertArrayEquals("%PDF-1.7".getBytes(),service.build("helene@medislot.test",42L));
  verify(renderer).render("convocation","c",new ConvocationView("Hélène Durand","Dr Sarah Martin","Médecine générale","mardi 12 janvier 2027","9 h 30","18 avenue de la République","Paris","RDV-000042"));
 }
 @Test void leNomDeFichierNeContientQueLeNumero(){
  assertEquals("convocation-42.pdf",service.filename(42L));
 }
 @Test void heureDeParisEnHiver(){
  // 08:30 UTC = 9 h 30 à Paris (UTC+1). Sans fuseau : « 8 h 30 ».
  assertEquals("9 h 30",ConvocationFormats.time(Instant.parse("2027-01-12T08:30:00Z")));
  assertEquals("mardi 12 janvier 2027",ConvocationFormats.day(Instant.parse("2027-01-12T08:30:00Z")));
 }
 @Test void heureDeParisEnEte(){
  // 07:30 UTC = 9 h 30 à Paris (UTC+2). Sans fuseau : « 7 h 30 ».
  assertEquals("9 h 30",ConvocationFormats.time(Instant.parse("2027-07-12T07:30:00Z")));
  // 23:30 UTC le 11 juillet = 1 h 30 le 12 juillet à Paris : le jour change aussi.
  assertEquals("lundi 12 juillet 2027",ConvocationFormats.day(Instant.parse("2027-07-11T23:30:00Z")));
 }
}
