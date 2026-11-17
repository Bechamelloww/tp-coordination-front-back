package fr.medislot.api;
import fr.medislot.domain.AppUser;
import fr.medislot.domain.Clinic;
import fr.medislot.domain.Membership;
import fr.medislot.repository.*;
import fr.medislot.tenant.TenantContext;
import java.security.Principal;
import java.util.List;
import org.springframework.data.domain.Sort;
import org.springframework.web.bind.annotation.*;
@RestController @RequestMapping("/api")
public class ClinicController {
 private final ClinicRepository clinics; private final MembershipRepository memberships; private final UserRepository users;
 public ClinicController(ClinicRepository c,MembershipRepository m,UserRepository u){clinics=c;memberships=m;users=u;}
 public record ClinicDto(String slug,String name,String city,String color,String role){static ClinicDto from(Clinic c,String role){return new ClinicDto(c.slug,c.name,c.city,c.color,role);}}
 /** Cabinet de la requête (en-tête X-Clinic) : nom et couleur du bandeau, même pour un visiteur anonyme. */
 @GetMapping("/clinic") public ClinicDto current(){return ClinicDto.from(clinics.findById(TenantContext.require()).orElseThrow(),null);}
 /** Tous les cabinets (informations publiques), pour le sélecteur d'un visiteur non connecté. */
 @GetMapping("/clinics") public List<ClinicDto> all(){return clinics.findAll(Sort.by("slug")).stream().map(c->ClinicDto.from(c,null)).toList();}
 /** Cabinets de l'utilisateur connecté, pour le sélecteur. */
 @GetMapping("/me/clinics") public List<ClinicDto> mine(Principal p){
  AppUser u=users.findByEmail(p.getName()).orElseThrow();
  if(AppUser.MASTER.equals(u.role))return clinics.findAll(Sort.by("slug")).stream().map(c->ClinicDto.from(c,Membership.ADMIN)).toList();
  var roles=new java.util.HashMap<String,String>();
  for(Object[] r:memberships.clinicsWithRoleOf(u.id))roles.put(((Clinic)r[0]).slug,(String)r[1]);
  // Gérant : seulement les cabinets qu'il administre. Patient : tous les cabinets, avec son rôle là où il est membre.
  if(roles.containsValue(Membership.ADMIN))return memberships.clinicsWithRoleOf(u.id).stream().filter(r->Membership.ADMIN.equals(r[1])).map(r->ClinicDto.from((Clinic)r[0],Membership.ADMIN)).toList();
  return clinics.findAll(Sort.by("slug")).stream().map(c->ClinicDto.from(c,roles.get(c.slug))).toList();
 }
}
