package fr.medislot.application;
import fr.medislot.domain.*;
import fr.medislot.repository.*;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.instrumentation.annotations.SpanAttribute;
import io.opentelemetry.instrumentation.annotations.WithSpan;
import java.time.Instant;
import java.util.UUID;
import java.util.*;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.web.server.ResponseStatusException;
@Service
public class BookingService {
 private final UserRepository users; private final SlotRepository slots; private final AppointmentRepository appointments;
 private final AuditRepository audit; private final NotificationRepository notifications; private final ApplicationEventPublisher events; private final BookingKeys keys;
 public BookingService(UserRepository u,SlotRepository s,AppointmentRepository a,AuditRepository h,NotificationRepository n,ApplicationEventPublisher e,BookingKeys k){users=u;slots=s;appointments=a;audit=h;notifications=n;events=e;keys=k;}
 public Long patient(String email){return users.findByEmail(email).orElseThrow().id;}
 private ResponseStatusException conflict(){return new ResponseStatusException(HttpStatus.CONFLICT,"Ce créneau n’est plus disponible. Choisissez-en un autre.");}
 private Slot lock(Long id){return slots.lockById(id).orElseThrow(()->new ResponseStatusException(HttpStatus.NOT_FOUND,"Créneau introuvable."));}
 private void check(Slot s){if(!s.startsAt.isAfter(Instant.now())||appointments.existsBySlotIdAndStatus(s.id,"CONFIRMED"))throw new SlotTakenException(s.professionalId);}
 /**
  * Un seul rendez-vous à venir par praticien : refusé s'il en existe déjà un (hors celui qu'on déplace, excludeId).
  * Le verrou du patient, pris après ceux des créneaux (ordre constant, pas d'interblocage), sérialise deux demandes simultanées du même patient.
  */
 private void oneUpcomingPerProfessional(Long patient,Slot s,Long excludeId){
  users.lockById(patient);
  if(appointments.hasUpcomingWith(patient,s.professionalId,Instant.now(),excludeId))throw new AlreadyBookedException(s.professionalId);
 }
 private Appointment owned(Long id,Long patient){var a=appointments.lockById(id).orElseThrow(()->new ResponseStatusException(HttpStatus.NOT_FOUND,"Rendez-vous introuvable."));if(!a.patientId.equals(patient))throw new ResponseStatusException(HttpStatus.NOT_FOUND,"Rendez-vous introuvable.");return a;}
 @Transactional public Appointment book(String email,Long slotId){return book(email,slotId,null,null);}
 @Transactional public Appointment book(String email,Long slotId,String abToken){return book(email,slotId,null,abToken);}
 /**
  * key : clé d'idempotence du client (en-tête Idempotency-Key), ou null.
  * Même patient, même clé, même créneau : le même rendez-vous, sans nouvelle réservation.
  * La clé est lue sous le verrou du créneau : un doublon simultané attend, puis la trouve.
  * Traces : span métier medislot.reservation, avec deux identifiants techniques seulement.
  * email n’est pas annoté : il ne quitte pas le back (donnée personnelle, jamais en attribut).
  */
 @WithSpan("medislot.reservation")
 @Transactional public Appointment book(String email,@SpanAttribute("medislot.slot.id") Long slotId,UUID key,String abToken){
  Long patient=patient(email); Slot s=lock(slotId);
  if(key!=null){
   var known=keys.find(patient,key);
   if(known.isPresent()){
    if(!known.get().slotId().equals(s.id))throw new ResponseStatusException(HttpStatusCode.valueOf(422),"Cette clé d’envoi a déjà servi pour un autre créneau.");
    Span.current().setAttribute("medislot.appointment.id",known.get().appointmentId());
    return appointments.findById(known.get().appointmentId()).orElseThrow();
   }
  }
  check(s);oneUpcomingPerProfessional(patient,s,-1L);
  Appointment a=new Appointment();a.patientId=patient;a.slotId=s.id;a.clinicId=s.clinicId;a.status="CONFIRMED";a.createdAt=Instant.now();appointments.saveAndFlush(a);
  Span.current().setAttribute("medislot.appointment.id",a.id);
  if(key!=null)keys.save(patient,key,s.id,a.id);
  record(a,"CREATED","Votre rendez-vous est confirmé.");events.publishEvent(new AppointmentBooked(patient,a.id,abToken));events.publishEvent(new BookingChanged(patient,List.of(s.professionalId)));events.publishEvent(new AppointmentChanged(a.id));return a;
 }
 @Transactional public Appointment cancel(String email,Long id){
  Appointment a=owned(id,patient(email));Slot s=lock(a.slotId);
  if(a.status.equals("CANCELLED"))return a;
  a.status="CANCELLED";appointments.saveAndFlush(a);record(a,"CANCELLED","Votre rendez-vous a été annulé.");events.publishEvent(new BookingChanged(a.patientId,List.of(s.professionalId)));events.publishEvent(new AppointmentChanged(a.id));return a;
 }
 @Transactional public Appointment reschedule(String email,Long id,Long slotId){
  Appointment a=owned(id,patient(email));if(!a.status.equals("CONFIRMED"))throw conflict();
  if(a.slotId.equals(slotId))return a;
  Long oldId=a.slotId; Slot first=lock(Math.min(oldId,slotId)),second=lock(Math.max(oldId,slotId));
  Slot target=first.id.equals(slotId)?first:second;Slot old=first.id.equals(oldId)?first:second;check(target);oneUpcomingPerProfessional(a.patientId,target,a.id);
  a.slotId=target.id;appointments.saveAndFlush(a);record(a,"RESCHEDULED","Votre rendez-vous a été déplacé.");
  events.publishEvent(new BookingChanged(a.patientId,List.of(old.professionalId,target.professionalId)));events.publishEvent(new AppointmentChanged(a.id));return a;
 }
 private void record(Appointment a,String action,String message){AuditEvent h=new AuditEvent();h.appointmentId=a.id;h.clinicId=a.clinicId;h.actorId=a.patientId;h.action=action;h.createdAt=Instant.now();audit.save(h);Notification n=new Notification();n.patientId=a.patientId;n.clinicId=a.clinicId;n.message=message;n.createdAt=h.createdAt;notifications.save(n);}
}
