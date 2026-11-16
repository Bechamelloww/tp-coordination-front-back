package fr.medislot.calendar;

import fr.medislot.application.AppointmentChanged;
import fr.medislot.tenant.TenantContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.web.client.RestClientResponseException;

@Component
public class CalendarSyncListener {
 private static final Logger log = LoggerFactory.getLogger(CalendarSyncListener.class);
 private final GoogleCalendarService calendarService;

 public CalendarSyncListener(GoogleCalendarService calendarService) {
  this.calendarService = calendarService;
 }

 @Async
 @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
 public void onAppointmentChanged(AppointmentChanged change) {
  // thread asynchrone : le cabinet de la requête d'origine ne suit pas
  TenantContext.set(change.clinicId());
  try {
   calendarService.synchronize(change.appointmentId());
  } catch (RestClientResponseException googleRejected) {
   log.warn("Synchronisation Google Agenda du rendez-vous {} refusée : HTTP {}", change.appointmentId(),
     googleRejected.getStatusCode().value());
  } catch (RuntimeException failure) {
   log.warn("Synchronisation Google Agenda du rendez-vous {} impossible : {}", change.appointmentId(),
     failure.getClass().getSimpleName());
  } finally {
   TenantContext.clear();
  }
 }
}
