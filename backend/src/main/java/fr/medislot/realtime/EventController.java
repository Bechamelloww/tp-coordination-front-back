package fr.medislot.realtime;
import fr.medislot.application.*;
import java.util.*;
import java.util.concurrent.*;
import java.security.Principal;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import org.springframework.transaction.event.*;
import org.springframework.scheduling.annotation.Scheduled;
@RestController
public class EventController {
 private record Subscriber(SseEmitter emitter,Long patient){}
 private final Set<Subscriber> clients=ConcurrentHashMap.newKeySet();
 private final BookingService booking;
 public EventController(BookingService booking){this.booking=booking;}
 static final int MAX_PUBLIC=500, MAX_PER_PATIENT=5;
 @GetMapping(value="/api/events",produces="text/event-stream") public SseEmitter publicEvents(){return connect(null);}
 @GetMapping(value="/api/notifications/events",produces="text/event-stream") public SseEmitter privateEvents(Principal p){return connect(booking.patient(p.getName()));}
 private synchronized Subscriber admit(SseEmitter emitter,Long patient){
  long same=clients.stream().filter(c->Objects.equals(c.patient,patient)).count();
  if(patient==null&&same>=MAX_PUBLIC)throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,"Trop de connexions en direct. Réessayez plus tard.");
  if(patient!=null&&same>=MAX_PER_PATIENT)throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS,"Trop d'onglets ouverts sur les notifications.");
  Subscriber s=new Subscriber(emitter,patient);clients.add(s);return s;
 }
 private SseEmitter connect(Long patient){SseEmitter emitter=new SseEmitter(120000L);Subscriber s=admit(emitter,patient);emitter.onCompletion(()->clients.remove(s));emitter.onTimeout(()->{clients.remove(s);emitter.complete();});emitter.onError(e->clients.remove(s));send(s,"ready",Map.of("resync",true));return emitter;}
 private void send(Subscriber s,String name,Object data){try{s.emitter.send(SseEmitter.event().name(name).data(data));}catch(Exception ex){clients.remove(s);s.emitter.complete();}}
 @TransactionalEventListener(phase=TransactionPhase.AFTER_COMMIT)
 public void committed(BookingChanged event){for(Subscriber s:clients){if(s.patient==null)send(s,"slots-changed",Map.of("professionalIds",event.professionalIds()));else if(s.patient.equals(event.patientId()))send(s,"notifications-changed",Map.of("refresh",true));}}
 @Scheduled(fixedDelay=20000) public void heartbeat(){for(Subscriber s:clients)send(s,"heartbeat",Map.of("alive",true));}
}
