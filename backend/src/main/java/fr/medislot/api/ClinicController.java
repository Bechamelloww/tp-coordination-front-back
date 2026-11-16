package fr.medislot.api;
import fr.medislot.domain.Clinic;
import fr.medislot.repository.*;
import fr.medislot.tenant.TenantContext;
import java.security.Principal;
import java.util.List;
import org.springframework.web.bind.annotation.*;
@RestController @RequestMapping("/api")
public class ClinicController {
 private final ClinicRepository clinics; private final MembershipRepository memberships; private final UserRepository users;
 public ClinicController(ClinicRepository c,MembershipRepository m,UserRepository u){clinics=c;memberships=m;users=u;}
 public record ClinicDto(String slug,String name,String city,String color){static ClinicDto from(Clinic c){return new ClinicDto(c.slug,c.name,c.city,c.color);}}
 /** Cabinet de la requête (en-tête X-Clinic) : nom et couleur du bandeau, même pour un visiteur anonyme. */
 @GetMapping("/clinic") public ClinicDto current(){return ClinicDto.from(clinics.findById(TenantContext.require()).orElseThrow());}
 /** Cabinets de l'utilisateur connecté, pour le sélecteur. */
 @GetMapping("/me/clinics") public List<ClinicDto> mine(Principal p){return memberships.clinicsOf(users.findByEmail(p.getName()).orElseThrow().id).stream().map(ClinicDto::from).toList();}
}
