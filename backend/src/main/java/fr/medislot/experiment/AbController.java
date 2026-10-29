package fr.medislot.experiment;
import fr.medislot.repository.UserRepository;
import java.security.Principal;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
@RestController @RequestMapping("/api/ab")
public class AbController {
 private final AbService ab; private final UserRepository users;
 public AbController(AbService ab,UserRepository users){this.ab=ab;this.users=users;}
 /** La variante est calculée par le serveur et remise dans un jeton signé ; jamais mise en cache partagé. */
 @GetMapping("/assignment") public ResponseEntity<AbService.Assignment> assignment(Principal p){return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(ab.assign(userId(p)));}
 /** La variante est lue dans le jeton signé (en-tête X-AB-Token) ; seule la première exposition compte. */
 @PostMapping("/exposure") public ResponseEntity<Void> exposure(Principal p,@RequestHeader(name="X-AB-Token",required=false) String token){ab.expose(userId(p),token);return ResponseEntity.noContent().build();}
 /** Agrégats uniquement, sans identifiant patient. */
 @GetMapping("/metrics") public ResponseEntity<AbService.Metrics> metrics(){return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(ab.metrics());}
 private Long userId(Principal p){return users.findByEmail(p.getName()).orElseThrow().id;}
}
