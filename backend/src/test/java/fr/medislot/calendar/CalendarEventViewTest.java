package fr.medislot.calendar;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import fr.medislot.domain.Appointment;
import fr.medislot.domain.Professional;
import fr.medislot.domain.Slot;
import java.time.Instant;
import java.util.Map;
import org.junit.jupiter.api.Test;

class CalendarEventViewTest {

 private static Map<String, Object> sampleEvent() {
  Appointment appointment = new Appointment();
  appointment.id = 42L;
  appointment.status = "CONFIRMED";
  Slot slot = new Slot();
  slot.startsAt = Instant.parse("2027-01-12T08:30:00Z");
  slot.endsAt = Instant.parse("2027-01-12T09:00:00Z");
  Professional professional = new Professional();
  professional.name = "Dr Sarah Martin";
  professional.specialty = "cardiology";
  professional.address = "18 avenue de la République";
  professional.city = "Paris";
  return CalendarEventView.of(appointment, slot, professional);
 }

 @Test
 void leTitreNeRevelePasLaSpecialite() {
  Map<String, Object> event = sampleEvent();
  assertEquals("Rendez-vous MediSlot", event.get("summary"));
  assertFalse(event.toString().toLowerCase().contains("cardio"));
 }

 @Test
 void laDescriptionDonneLePraticienEtLaReference() {
  String description = (String) sampleEvent().get("description");
  assertTrue(description.contains("Dr Sarah Martin"));
  assertTrue(description.contains("RDV-000042"));
 }

 @Test
 void lHoraireEstCeluiDuCreneauEnHeureDeParis() {
  Map<String, Object> event = sampleEvent();
  assertEquals(Map.of("dateTime", "2027-01-12T08:30:00Z", "timeZone", "Europe/Paris"), event.get("start"));
  assertEquals(Map.of("dateTime", "2027-01-12T09:00:00Z", "timeZone", "Europe/Paris"), event.get("end"));
 }

 @Test
 void lEvenementEstPriveEtSitueAuCabinet() {
  Map<String, Object> event = sampleEvent();
  assertEquals("private", event.get("visibility"));
  assertEquals("18 avenue de la République, Paris", event.get("location"));
 }

 @Test
 void lIdentifiantRespecteLAlphabetImposeParGoogle() {
  String eventId = CalendarEventView.eventId(123L);
  assertEquals("medislotrdv123", eventId);
  assertTrue(eventId.matches("[0-9a-v]{5,1024}"));
 }
}
