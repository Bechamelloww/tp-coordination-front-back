package fr.medislot.application;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
/**
 * Règle métier : un seul rendez-vous à venir par praticien et par patient.
 * Pour changer d'heure chez ce praticien, on déplace le rendez-vous existant.
 * Code stable ALREADY_BOOKED dans le ProblemDetail (voir Errors) : le front n'a pas à lire la phrase.
 */
public class AlreadyBookedException extends ResponseStatusException {
 private final Long professionalId;
 public AlreadyBookedException(Long professionalId){super(HttpStatus.CONFLICT,"Vous avez déjà un rendez-vous à venir avec ce praticien. Pour changer d’horaire, déplacez-le depuis « Mes rendez-vous ».");this.professionalId=professionalId;}
 public Long professionalId(){return professionalId;}
}
