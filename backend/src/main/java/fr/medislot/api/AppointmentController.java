package fr.medislot.api;
import fr.medislot.application.BookingService;
import fr.medislot.domain.*;
import fr.medislot.repository.*;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.security.Principal;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.*;
import org.springframework.web.server.ResponseStatusException;
@RestController @RequestMapping("/api")
public class AppointmentController {
 private final BookingService service; private final AppointmentRepository appointments; private final SlotRepository slots; private final ProfessionalRepository professionals; private final AuditRepository audit; private final NotificationRepository notifications; private final MembershipRepository memberships; private final fr.medislot.audit.AdminLog log;
 public AppointmentController(BookingService s,AppointmentRepository a,SlotRepository t,ProfessionalRepository p,AuditRepository h,NotificationRepository n,MembershipRepository m,fr.medislot.audit.AdminLog l){service=s;appointments=a;slots=t;professionals=p;audit=h;notifications=n;memberships=m;log=l;}
 public enum ReasonCode{FIRST_VISIT,FOLLOW_UP,PRESCRIPTION,RESULTS}
 public enum ForWhom{SELF,RELATIVE}
 /**
  * Champs du formulaire facultatifs pendant la fenêtre de compatibilité (étendre, puis contracter) :
  * un ancien front qui n'envoie que slotId reste servi. Mêmes règles que offline/bookingRules.ts.
  * Ils sont validés mais pas conservés : le rendez-vous n'en a pas besoin (minimisation).
  */
 public record BookingRequest(@NotNull @Positive Long slotId,ReasonCode reasonCode,ForWhom forWhom,
  @Size(max=120,message="120 caractères au plus.") String relativeName,
  @Pattern(regexp="^0[1-9][0-9]{8}$",message="Numéro attendu : 10 chiffres, commençant par 0.") String phone){
  @AssertTrue(message="Indiquez le nom du proche.")
  public boolean isRelativeNamed(){return forWhom!=ForWhom.RELATIVE||(relativeName!=null&&!relativeName.isBlank());}
 }
 public record AppointmentDto(Long id,Long slotId,Long professionalId,String professionalName,String specialty,String address,String city,Instant startsAt,String status){}
 public record HistoryDto(String action,Instant createdAt){}
 public record NotificationDto(Long id,String message,Instant createdAt,Instant readAt){}
 public record MarkReadRequest(@NotNull @Size(max=30) java.util.List<@NotNull @Positive Long> ids){}
 private AppointmentDto dto(Appointment a){Slot s=slots.findById(a.slotId).orElseThrow();Professional p=professionals.findById(s.professionalId).orElseThrow();return new AppointmentDto(a.id,s.id,p.id,p.name,p.specialty,p.address,p.city,s.startsAt,a.status);}
 @GetMapping("/appointments") public java.util.List<AppointmentDto> list(Principal p){return appointments.findByPatientIdOrderByCreatedAtDesc(service.patient(p.getName())).stream().map(this::dto).toList();}
 @PostMapping("/appointments") public ResponseEntity<AppointmentDto> book(Principal p,@Valid @RequestBody BookingRequest r,@RequestHeader(name="X-AB-Token",required=false) String abToken,@RequestHeader(name="Idempotency-Key",required=false) java.util.UUID key){var a=service.book(p.getName(),r.slotId(),key,abToken);
  if(memberships.join(a.patientId,a.clinicId)==1)log.record(a.clinicId,p.getName(),fr.medislot.audit.AdminLog.MEMBER_JOINED,p.getName()+" a rejoint le cabinet en prenant rendez-vous");
  return ResponseEntity.created(java.net.URI.create("/api/appointments/"+a.id)).body(dto(a));}
 @GetMapping("/appointments/{id}") public AppointmentDto one(Principal p,@PathVariable Long id){return dto(owned(p,id));}
 private Appointment owned(Principal p,Long id){return appointments.findById(id).filter(a->a.patientId.equals(service.patient(p.getName()))).orElseThrow(()->new ResponseStatusException(HttpStatus.NOT_FOUND,"Rendez-vous introuvable."));}
 @PostMapping("/appointments/{id}/cancel") public AppointmentDto cancel(Principal p,@PathVariable Long id){return dto(service.cancel(p.getName(),id));}
 @PatchMapping("/appointments/{id}") public AppointmentDto move(Principal p,@PathVariable Long id,@Valid @RequestBody BookingRequest r){return dto(service.reschedule(p.getName(),id,r.slotId()));}
 @GetMapping("/appointments/{id}/history") public java.util.List<HistoryDto> history(Principal p,@PathVariable Long id){owned(p,id);return audit.findByAppointmentIdOrderByCreatedAtAsc(id).stream().map(h->new HistoryDto(h.action,h.createdAt)).toList();}
 @GetMapping("/notifications") public java.util.List<NotificationDto> notifications(Principal p){return notifications.findTop30ByPatientIdOrderByCreatedAtDesc(service.patient(p.getName())).stream().map(n->new NotificationDto(n.id,n.message,n.createdAt,n.readAt)).toList();}
 @PostMapping("/notifications/read") @org.springframework.transaction.annotation.Transactional public ResponseEntity<Void> markRead(Principal p,@Valid @RequestBody MarkReadRequest r){if(!r.ids().isEmpty()){Instant now=Instant.now();var unread=notifications.findByPatientIdAndIdInAndReadAtIsNull(service.patient(p.getName()),r.ids());unread.forEach(n->n.readAt=now);notifications.saveAll(unread);}return ResponseEntity.noContent().build();}
}
