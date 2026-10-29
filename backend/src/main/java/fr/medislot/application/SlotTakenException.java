package fr.medislot.application;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
/** Créneau pris ou passé. Hérite de ResponseStatusException : les anciens contrôles (409) restent valables. */
public class SlotTakenException extends ResponseStatusException {
 private final Long professionalId;
 public SlotTakenException(Long professionalId){super(HttpStatus.CONFLICT,"Ce créneau n’est plus disponible. Choisissez-en un autre.");this.professionalId=professionalId;}
 public Long professionalId(){return professionalId;}
}
