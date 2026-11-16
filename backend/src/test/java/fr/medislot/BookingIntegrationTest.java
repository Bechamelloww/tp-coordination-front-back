package fr.medislot;
import fr.medislot.application.BookingService;
import fr.medislot.repository.*;
import fr.medislot.tenant.TenantContext;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.server.ResponseStatusException;
import java.time.*;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;
class BookingIntegrationTest extends PostgresTestBase {
 @Autowired BookingService booking; @Autowired SlotRepository slots; @Autowired AuditRepository audit;
 @Test void concurrentBookingsProduceOneWinner() throws Exception {
  Long slot=slots.available(1L,Instant.now(),Instant.now().plus(Duration.ofDays(14))).getFirst().id;
  try(var pool=Executors.newFixedThreadPool(2)){
   var start=new CountDownLatch(1);
   Long clinic=TenantContext.get();
   Callable<Integer> task=()->{TenantContext.set(clinic);start.await();try{booking.book("patient@medislot.test",slot);return 201;}catch(ResponseStatusException e){return e.getStatusCode().value();}finally{TenantContext.clear();}};
   var a=pool.submit(task);var b=pool.submit(task);start.countDown();var codes=new java.util.ArrayList<>(java.util.List.of(a.get(15,TimeUnit.SECONDS),b.get(15,TimeUnit.SECONDS)));codes.sort(Integer::compareTo);assertEquals(java.util.List.of(201,409),codes);
  }
 }
 @Test void anotherPatientCannotCancelAndCancellationKeepsHistory(){
  Long slot=slots.available(2L,Instant.now(),Instant.now().plus(Duration.ofDays(14))).getFirst().id;
  var a=booking.book("patient@medislot.test",slot);
  assertEquals(404,assertThrows(ResponseStatusException.class,()->booking.cancel("autre@medislot.test",a.id)).getStatusCode().value());
  booking.cancel("patient@medislot.test",a.id);
  assertEquals(2,audit.findByAppointmentIdOrderByCreatedAtAsc(a.id).size());
  assertTrue(slots.available(2L,Instant.now(),Instant.now().plus(Duration.ofDays(14))).stream().anyMatch(s->s.id.equals(slot)));
 }
 @Test void oneUpcomingAppointmentPerProfessional(){
  // Vraie requête JPQL contre PostgreSQL : deuxième créneau chez le même praticien refusé (ALREADY_BOOKED), annulation = de nouveau libre.
  var free=slots.available(6L,Instant.now(),Instant.now().plus(Duration.ofDays(14)));
  var first=booking.book("autre@medislot.test",free.get(0).id);
  var refused=assertThrows(fr.medislot.application.AlreadyBookedException.class,()->booking.book("autre@medislot.test",free.get(1).id));
  assertEquals(409,refused.getStatusCode().value());assertEquals(6L,refused.professionalId());
  // Déplacer chez le même praticien reste possible.
  assertEquals(free.get(1).id,booking.reschedule("autre@medislot.test",first.id,free.get(1).id).slotId);
  booking.cancel("autre@medislot.test",first.id);
  assertNotNull(booking.book("autre@medislot.test",free.get(2).id).id);
 }
 @Test void sameIdempotencyKeyReturnsSameAppointment(){
  var free=slots.available(4L,Instant.now(),Instant.now().plus(Duration.ofDays(14)));
  java.util.UUID key=java.util.UUID.randomUUID();
  var first=booking.book("patient@medislot.test",free.get(0).id,key,null);
  var replay=booking.book("patient@medislot.test",free.get(0).id,key,null);
  assertEquals(first.id,replay.id);
  assertEquals(1,audit.findByAppointmentIdOrderByCreatedAtAsc(first.id).size());
  // Même clé pour un autre créneau : 422, rien de réservé.
  assertEquals(422,assertThrows(ResponseStatusException.class,()->booking.book("patient@medislot.test",free.get(1).id,key,null)).getStatusCode().value());
  assertTrue(slots.available(4L,Instant.now(),Instant.now().plus(Duration.ofDays(14))).stream().anyMatch(x->x.id.equals(free.get(1).id)));
  // La clé appartient au patient : la même clé chez un autre patient ne rend pas ce rendez-vous.
  var taken=assertThrows(fr.medislot.application.SlotTakenException.class,()->booking.book("autre@medislot.test",free.get(0).id,key,null));
  assertEquals(409,taken.getStatusCode().value());assertEquals(4L,taken.professionalId());
 }
}
