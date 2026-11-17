package fr.medislot.api;
import fr.medislot.domain.AppUser;
import fr.medislot.domain.Membership;
import fr.medislot.repository.MembershipRepository;
import fr.medislot.tenant.TenantContext;
import fr.medislot.repository.UserRepository;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
@RestController @RequestMapping("/api")
public class AuthController {
 private final UserRepository users; private final PasswordEncoder encoder; private final MembershipRepository memberships;
 public AuthController(UserRepository users,PasswordEncoder encoder,MembershipRepository memberships){this.users=users;this.encoder=encoder;this.memberships=memberships;}
 public record Identity(Long id,String name,String email,String role){}
 /** RGAA 11.10, 11.11 : chaque contrainte dit la règle en français ; le front affiche le message sous le champ (errors[] du ProblemDetail). */
 public record Registration(
  @Email(message="Adresse e-mail attendue, par exemple nom@exemple.fr.") @NotBlank(message="Saisissez votre adresse e-mail.") @Size(max=254,message="254 caractères au plus.") String email,
  @NotBlank(message="Saisissez un mot de passe de 12 caractères au moins.") @Size(min=12,max=72,message="12 caractères au moins, 72 au plus. Une phrase de plusieurs mots convient.") String password,
  @NotBlank(message="Saisissez votre nom.") @Size(max=120,message="120 caractères au plus.") String name){}
 @GetMapping("/csrf") public java.util.Map<String,String> csrf(CsrfToken token){return java.util.Map.of("token",token.getToken(),"headerName",token.getHeaderName());}
 @GetMapping("/me") public Identity me(java.security.Principal principal){AppUser u=users.findByEmail(principal.getName()).orElseThrow();return new Identity(u.id,u.name,u.email,u.role);}
 @PostMapping("/register") @ResponseStatus(HttpStatus.CREATED)
 public Identity register(@Valid @RequestBody Registration input){
  if(input.password().getBytes(java.nio.charset.StandardCharsets.UTF_8).length>72)throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Le mot de passe doit contenir au maximum 72 octets UTF-8.");
  String email=input.email().strip().toLowerCase(java.util.Locale.ROOT);
  if(users.existsByEmail(email))throw new ResponseStatusException(HttpStatus.CONFLICT,"Cette adresse est déjà utilisée.");
  AppUser u=new AppUser();u.email=email;u.name=input.name().strip();u.password=encoder.encode(input.password());u.role="PATIENT";users.saveAndFlush(u);
  Membership m=new Membership();m.userId=u.id;m.clinicId=TenantContext.require();memberships.save(m);
  return new Identity(u.id,u.name,u.email,u.role);
 }
}
