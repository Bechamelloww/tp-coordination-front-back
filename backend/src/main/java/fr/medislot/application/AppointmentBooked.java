package fr.medislot.application;
/** Réservation enregistrée. abToken : jeton de variante présenté par le front, ou null. */
public record AppointmentBooked(Long patientId,Long appointmentId,String abToken) {}
