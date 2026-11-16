package fr.medislot.calendar;

import fr.medislot.convocation.ConvocationFormats;
import fr.medislot.domain.Appointment;
import fr.medislot.domain.Professional;
import fr.medislot.domain.Slot;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public final class CalendarEventView {
 public static final String TIME_ZONE = ConvocationFormats.PARIS.getId();
 public static final String EVENT_TITLE = "Rendez-vous MediSlot";
 private static final String EVENT_ID_PREFIX = "medislotrdv";

 private CalendarEventView() {
 }

 public static String eventId(Long appointmentId) {
  return EVENT_ID_PREFIX + appointmentId;
 }

 public static Map<String, Object> of(Appointment appointment, Slot slot, Professional professional) {
  Map<String, Object> event = new LinkedHashMap<>();
  event.put("summary", EVENT_TITLE);
  event.put("location", location(professional));
  event.put("description", description(appointment, professional));
  event.put("start", Map.of("dateTime", slot.startsAt.toString(), "timeZone", TIME_ZONE));
  event.put("end", Map.of("dateTime", slot.endsAt.toString(), "timeZone", TIME_ZONE));
  event.put("status", "confirmed");
  event.put("visibility", "private");
  event.put("reminders", Map.of("useDefault", true));
  return event;
 }

 private static String location(Professional professional) {
  return Stream.of(professional.address, professional.city)
    .filter(Objects::nonNull)
    .filter(part -> !part.isBlank())
    .collect(Collectors.joining(", "));
 }

 private static String description(Appointment appointment, Professional professional) {
  return "Praticien : " + professional.name + "\n"
    + "Référence : " + ConvocationFormats.reference(appointment.id) + "\n\n"
    + "Pour déplacer ou annuler ce rendez-vous, passez par MediSlot.";
 }
}
