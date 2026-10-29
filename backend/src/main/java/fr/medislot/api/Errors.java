package fr.medislot.api;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.*;
import org.springframework.web.server.ResponseStatusException;
@RestControllerAdvice
public class Errors {
 /** Créneau pris : un code stable, pour que le front n'ait pas à lire une phrase. RFC 9457. */
 @ExceptionHandler(fr.medislot.application.SlotTakenException.class) public ResponseEntity<ProblemDetail> slotTaken(fr.medislot.application.SlotTakenException e){var p=ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT,e.getReason());p.setTitle("Créneau déjà pris");p.setProperty("code","SLOT_TAKEN");p.setProperty("professionalId",e.professionalId());return ResponseEntity.status(HttpStatus.CONFLICT).body(p);}
 @ExceptionHandler(ResponseStatusException.class) public ResponseEntity<ProblemDetail> status(ResponseStatusException e){var p=ProblemDetail.forStatusAndDetail(e.getStatusCode(),e.getReason()==null?"Demande refusée":e.getReason());return ResponseEntity.status(e.getStatusCode()).body(p);}
 @ExceptionHandler(MethodArgumentNotValidException.class) public ResponseEntity<ProblemDetail> validation(MethodArgumentNotValidException e){var p=ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST,"Vérifiez les données saisies.");p.setProperty("errors",e.getBindingResult().getFieldErrors().stream().map(f->java.util.Map.of("field",f.getField(),"message",String.valueOf(f.getDefaultMessage()))).toList());return ResponseEntity.badRequest().body(p);}
 @ExceptionHandler(DataIntegrityViolationException.class) public ResponseEntity<ProblemDetail> conflict(){return ResponseEntity.status(409).body(ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT,"La demande entre en conflit avec les données actuelles."));}
}
