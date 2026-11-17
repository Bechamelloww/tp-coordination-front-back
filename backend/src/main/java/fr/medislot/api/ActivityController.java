package fr.medislot.api;

import fr.medislot.activity.Activity;
import fr.medislot.activity.ActivityLog;
import fr.medislot.repository.ClinicRepository;
import fr.medislot.tenant.TenantContext;
import java.time.LocalDate;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
public class ActivityController {
 private static final int MAX_TEXT = 100;
 private final ActivityLog activityLog;
 private final ClinicRepository clinics;

 public ActivityController(ActivityLog activityLog, ClinicRepository clinics) {
  this.activityLog = activityLog;
  this.clinics = clinics;
 }

 @GetMapping("/api/admin/activity")
 public ActivityLog.Page clinicActivity(
   @RequestParam(required = false) String action,
   @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
   @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
   @RequestParam(required = false) String text,
   @RequestParam(required = false) Long before) {
  return activityLog.search(new ActivityLog.Scope.OneClinic(TenantContext.require()), filter(action, from, to, text, before));
 }

 @GetMapping("/api/master/activity")
 public ActivityLog.Page allActivity(
   @RequestParam(required = false) String clinic,
   @RequestParam(defaultValue = "false") boolean outside,
   @RequestParam(required = false) String action,
   @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
   @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
   @RequestParam(required = false) String text,
   @RequestParam(required = false) Long before) {
  ActivityLog.Scope scope;
  if (outside) {
   scope = new ActivityLog.Scope.OutsideClinics();
  } else if (clinic == null || clinic.isBlank()) {
   scope = new ActivityLog.Scope.Everything();
  } else {
   scope = clinics.findBySlug(clinic.strip()).<ActivityLog.Scope>map(c -> new ActivityLog.Scope.OneClinic(c.id))
     .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Cabinet introuvable."));
  }
  return activityLog.search(scope, filter(action, from, to, text, before));
 }

 private static ActivityLog.Filter filter(String action, LocalDate from, LocalDate to, String text, Long before) {
  if (from != null && to != null && to.isBefore(from)) {
   throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "La date de fin précède la date de début.");
  }
  if (text != null && text.length() > MAX_TEXT) {
   throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Recherche limitée à " + MAX_TEXT + " caractères.");
  }
  return new ActivityLog.Filter(activity(action), from, to, text, before);
 }

 private static Activity activity(String action) {
  if (action == null || action.isBlank()) {
   return null;
  }
  try {
   return Activity.valueOf(action.strip());
  } catch (IllegalArgumentException unknown) {
   throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Type d'action inconnu.");
  }
 }
}
