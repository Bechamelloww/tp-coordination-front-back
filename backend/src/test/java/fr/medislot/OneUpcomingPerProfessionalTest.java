package fr.medislot;
import fr.medislot.application.AlreadyBookedException;
import fr.medislot.application.BookingService;
import fr.medislot.domain.*;
import fr.medislot.repository.*;
import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
/** Règle : un seul rendez-vous à venir par praticien et par patient ; les autres praticiens restent libres. */
class OneUpcomingPerProfessionalTest {
 UserRepository users=mock(UserRepository.class);SlotRepository slots=mock(SlotRepository.class);AppointmentRepository appointments=mock(AppointmentRepository.class);
 BookingService service=new BookingService(users,slots,appointments,mock(AuditRepository.class),mock(NotificationRepository.class),mock(ApplicationEventPublisher.class),mock(BookingKeys.class),mock(fr.medislot.activity.ActivityLog.class));
 static final String EMAIL="patient@test";

 @BeforeEach void data(){
  AppUser u=new AppUser();u.id=1L;u.email=EMAIL;when(users.findByEmail(EMAIL)).thenReturn(Optional.of(u));
  slot(10L,3L,1);slot(11L,3L,2);slot(20L,4L,1);
  when(appointments.saveAndFlush(any())).thenAnswer(i->{Appointment a=i.getArgument(0);if(a.id==null)a.id=99L;return a;});
 }
 void slot(Long id,Long professionalId,int days){Slot s=new Slot();s.id=id;s.professionalId=professionalId;s.startsAt=Instant.now().plusSeconds(86_400L*days);s.endsAt=s.startsAt.plusSeconds(1800);when(slots.lockById(id)).thenReturn(Optional.of(s));}

 @Test void unDeuxiemeRendezVousChezLeMemePraticienEstRefuse(){
  when(appointments.hasUpcomingWith(eq(1L),eq(3L),any(),eq(-1L))).thenReturn(true);
  var e=assertThrows(AlreadyBookedException.class,()->service.book(EMAIL,11L));
  assertEquals(409,e.getStatusCode().value());assertEquals(3L,e.professionalId());
  verify(appointments,never()).saveAndFlush(any());
 }
 @Test void unAutrePraticienResteLibre(){
  when(appointments.hasUpcomingWith(eq(1L),eq(3L),any(),anyLong())).thenReturn(true);
  assertEquals(99L,service.book(EMAIL,20L).id);
  verify(appointments).hasUpcomingWith(eq(1L),eq(4L),any(),eq(-1L));
  verify(users).lockById(1L);
 }
 @Test void deplacerChezLeMemePraticienResteAutorise(){
  Appointment a=appointment(5L,10L);
  // Le seul rendez-vous à venir chez ce praticien est celui qu'on déplace : il est exclu du contrôle.
  when(appointments.hasUpcomingWith(eq(1L),eq(3L),any(),eq(5L))).thenReturn(false);
  assertEquals(11L,service.reschedule(EMAIL,5L,11L).slotId);
 }
 @Test void deplacerVersUnPraticienDejaReserveEstRefuse(){
  appointment(5L,10L);
  when(appointments.hasUpcomingWith(eq(1L),eq(4L),any(),eq(5L))).thenReturn(true);
  assertThrows(AlreadyBookedException.class,()->service.reschedule(EMAIL,5L,20L));
  verify(appointments,never()).saveAndFlush(any());
 }
 Appointment appointment(Long id,Long slotId){Appointment a=new Appointment();a.id=id;a.patientId=1L;a.slotId=slotId;a.status="CONFIRMED";when(appointments.lockById(id)).thenReturn(Optional.of(a));return a;}
}
