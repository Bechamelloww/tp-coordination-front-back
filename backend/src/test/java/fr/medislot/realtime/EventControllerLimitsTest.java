package fr.medislot.realtime;
import fr.medislot.application.BookingService;
import fr.medislot.tenant.TenantContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
class EventControllerLimitsTest {
 final BookingService booking=mock(BookingService.class);
 final EventController events=new EventController(booking);
 @BeforeEach void clinic(){TenantContext.set(1L);}
 @AfterEach void clear(){TenantContext.clear();}

 @Test void publicStreamRefusesBeyondItsCap(){
  for(int i=0;i<EventController.MAX_PUBLIC;i++)events.publicEvents();
  assertEquals(503,assertThrows(ResponseStatusException.class,events::publicEvents).getStatusCode().value());
 }
 @Test void aFullClinicDoesNotBlockTheOthers(){
  for(int i=0;i<EventController.MAX_PUBLIC;i++)events.publicEvents();
  TenantContext.set(2L);
  assertNotNull(events.publicEvents());
 }
 @Test void privateStreamIsCappedPerPatient(){
  when(booking.patient("patient@medislot.test")).thenReturn(1L);
  when(booking.patient("autre@medislot.test")).thenReturn(2L);
  for(int i=0;i<EventController.MAX_PER_PATIENT;i++)events.privateEvents(()->"patient@medislot.test");
  assertEquals(429,assertThrows(ResponseStatusException.class,()->events.privateEvents(()->"patient@medislot.test")).getStatusCode().value());
  assertNotNull(events.privateEvents(()->"autre@medislot.test"));
  assertNotNull(events.publicEvents());
 }
}
