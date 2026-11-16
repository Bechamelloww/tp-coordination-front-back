package fr.medislot.calendar;

public class GoogleAccessRevokedException extends RuntimeException {
 public GoogleAccessRevokedException() {
  super("L'accès Google Agenda a été retiré.");
 }
}
