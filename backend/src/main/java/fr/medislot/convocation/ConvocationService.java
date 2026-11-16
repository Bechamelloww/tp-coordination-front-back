package fr.medislot.convocation;
import fr.medislot.domain.*;
import fr.medislot.repository.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
/**
 * Convocation d'un rendez-vous. Ordre imposé : on refuse d'abord (404, 409), on fabrique ensuite.
 * Aucun PDF n'est produit pour un rendez-vous qui n'appartient pas au patient connecté.
 */
@Service
public class ConvocationService {
 private static final Logger log=LoggerFactory.getLogger(ConvocationService.class);
 /** Même message pour « n'existe pas » et « appartient à un autre » : un 403 révélerait l'existence du numéro. */
 static final String NOT_FOUND="Rendez-vous introuvable.";
 static final String CANCELLED="Ce rendez-vous est annulé : il n’a plus de convocation.";
 static final String FAILED="La convocation n’a pas pu être préparée. Réessayez dans quelques minutes.";
 private final UserRepository users;private final AppointmentRepository appointments;private final SlotRepository slots;private final ProfessionalRepository professionals;private final PdfRenderer renderer;
 public ConvocationService(UserRepository u,AppointmentRepository a,SlotRepository s,ProfessionalRepository p,PdfRenderer r){users=u;appointments=a;slots=s;professionals=p;renderer=r;}
 @Transactional(readOnly=true)
 public byte[] build(String email,Long id){
  // 1. Le patient connecté.
  AppUser patient=users.findByEmail(email).orElseThrow(()->new ResponseStatusException(HttpStatus.UNAUTHORIZED,"Connexion requise."));
  // 2-3. Le rendez-vous, seulement s'il est à lui ; sinon 404, comme s'il n'existait pas.
  Appointment a=appointments.findById(id).filter(x->x.patientId.equals(patient.id)).orElseThrow(()->new ResponseStatusException(HttpStatus.NOT_FOUND,NOT_FOUND));
  // 4. Un rendez-vous annulé n'a plus de convocation : 409, la ressource existe mais son état l'interdit.
  if(!"CONFIRMED".equals(a.status))throw new ResponseStatusException(HttpStatus.CONFLICT,CANCELLED);
  // 5. Les données à imprimer, déjà mises en forme.
  Slot s=slots.findById(a.slotId).orElseThrow();Professional p=professionals.findById(s.professionalId).orElseThrow();
  ConvocationView view=view(patient,a,s,p);
  // 6. Le rendu ; un échec devient un 500 lisible, le détail technique reste dans les journaux.
  try{return renderer.render("convocation","c",view);}
  catch(RuntimeException e){log.error("Rendu de la convocation {} impossible",id,e);throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR,FAILED,e);}
 }
 static ConvocationView view(AppUser patient,Appointment a,Slot s,Professional p){
  return new ConvocationView(patient.name,p.name,ConvocationFormats.specialty(p.specialty),ConvocationFormats.day(s.startsAt),ConvocationFormats.time(s.startsAt),p.address,p.city,ConvocationFormats.reference(a.id));
 }
 /** Nom proposé au téléchargement : le numéro seul. Ni nom, ni spécialité, ni date dans un nom de fichier qui traîne. */
 public String filename(Long id){return "convocation-"+id+".pdf";}
}
