package fr.medislot.convocation;
import java.security.Principal;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
/**
 * GET /api/appointments/{id}/convocation.pdf : la convocation du patient connecté.
 * Contrat : Content-Type application/pdf ; Content-Disposition inline (affichage) ou attachment avec download=true ;
 * Cache-Control no-store (document personnel, pas de copie en cache) ; X-Content-Type-Options nosniff.
 * Erreurs en application/problem+json : 401, 404, 409, 500 (voir Errors et SecurityConfig).
 */
@RestController @RequestMapping("/api")
public class ConvocationController {
 private final ConvocationService service;
 public ConvocationController(ConvocationService service){this.service=service;}
 @GetMapping("/appointments/{id}/convocation.pdf")
 public ResponseEntity<byte[]> convocation(Principal principal,@PathVariable Long id,@RequestParam(defaultValue="false") boolean download){
  byte[] pdf=service.build(principal.getName(),id);
  ContentDisposition disposition=(download?ContentDisposition.attachment():ContentDisposition.inline()).filename(service.filename(id)).build();
  return ResponseEntity.ok()
   .contentType(MediaType.APPLICATION_PDF)
   .header(HttpHeaders.CONTENT_DISPOSITION,disposition.toString())
   .cacheControl(CacheControl.noStore())
   .header("X-Content-Type-Options","nosniff")
   .body(pdf);
 }
}
