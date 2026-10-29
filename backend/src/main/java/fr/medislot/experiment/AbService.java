package fr.medislot.experiment;
import fr.medislot.application.AppointmentBooked;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.web.server.ResponseStatusException;

/**
 * Test A/B du libellé du bouton de confirmation.
 * Unité : le patient connecté. Seuls les N premiers patients affectés participent ;
 * les suivants voient A et ne sont pas comptés.
 * La variante circule dans un jeton signé (AbTokens) : exposition et conversion ne sont
 * comptées que sur présentation d'un jeton valide appartenant au patient connecté.
 */
@Service
public class AbService {
 // v2 : le libellé A devient « Valider » (bouton du formulaire de réservation). Nouvelle version = nouvelle clé.
 public static final String EXP="confirm_label_v2";
 private final JdbcTemplate jdbc; private final AbTokens tokens; private final boolean enabled; private final int maxParticipants;
 public AbService(JdbcTemplate jdbc,AbTokens tokens,@Value("${medislot.ab.enabled:true}") boolean enabled,@Value("${medislot.ab.max-participants:10}") int maxParticipants){this.jdbc=jdbc;this.tokens=tokens;this.enabled=enabled;this.maxParticipants=maxParticipants;}

 /** token : null hors expérience (test coupé ou limite atteinte) ; le front affiche alors A. */
 public record Assignment(String experiment,boolean enabled,boolean participating,String token){}
 public record VariantMetrics(String variant,int assigned,int exposed,int converted,Double rate){}
 public record Metrics(String experiment,boolean enabled,int maxParticipants,int participants,List<VariantMetrics> variants){}

 @Transactional
 public Assignment assign(Long userId){
  if(!enabled)return new Assignment(EXP,false,false,null);
  Optional<String> known=variantOf(userId);
  if(known.isPresent())return participant(userId,known.get());
  // Verrou sur l'expérience : deux inscriptions simultanées ne peuvent pas dépasser la limite.
  jdbc.queryForObject("select experiment_key from ab_experiment where experiment_key=? for update",String.class,EXP);
  known=variantOf(userId);
  if(known.isPresent())return participant(userId,known.get());
  int rank=jdbc.queryForObject("select count(*) from ab_participant where experiment=?",Integer.class,EXP);
  if(rank>=maxParticipants)return new Assignment(EXP,true,false,null);
  // Tirage par paires : le premier de chaque paire est tiré par hachage, le second reçoit l'autre version.
  // Avec 10 participants, on obtient exactement 5 A et 5 B.
  String variant=rank%2==0?hashVariant(userId):opposite(variantAtRank(rank-1));
  jdbc.update("insert into ab_participant(experiment,user_id,enrollment_rank,variant,assigned_at) values (?,?,?,?,?)",EXP,userId,rank,variant,Timestamp.from(Instant.now()));
  return participant(userId,variant);
 }

 /** Première exposition seulement : une seule mise à jour conditionnelle, exposed_at n'est jamais remplacé. */
 public void expose(Long userId,String token){
  if(!enabled)return;
  var claims=valid(token,userId).orElseThrow(()->new ResponseStatusException(HttpStatus.BAD_REQUEST,"Jeton A/B absent ou invalide."));
  jdbc.update("update ab_participant set exposed_at=? where experiment=? and user_id=? and variant=? and exposed_at is null",Timestamp.from(Instant.now()),EXP,userId,claims.variant());
 }

 /**
  * Conversion écrite dans la transaction de la réservation : un 409 annule tout, rien n'est compté.
  * Sans jeton valide, la réservation reste acceptée mais n'est pas attribuée.
  */
 @TransactionalEventListener(phase=TransactionPhase.BEFORE_COMMIT)
 public void booked(AppointmentBooked event){
  if(!enabled)return;
  valid(event.abToken(),event.patientId()).ifPresent(claims->jdbc.update(
   "update ab_participant set converted_at=?,appointment_id=? where experiment=? and user_id=? and variant=? and exposed_at is not null and converted_at is null",
   Timestamp.from(Instant.now()),event.appointmentId(),EXP,event.patientId(),claims.variant()));
 }

 public Metrics metrics(){
  Map<String,VariantMetrics> byVariant=new LinkedHashMap<>();
  for(String v:List.of("A","B"))byVariant.put(v,new VariantMetrics(v,0,0,0,null));
  jdbc.query("select variant,count(*) assigned,count(exposed_at) exposed,count(converted_at) converted from ab_participant where experiment=? group by variant",
   (rs,i)->{int exposed=rs.getInt("exposed"),converted=rs.getInt("converted");return new VariantMetrics(rs.getString("variant"),rs.getInt("assigned"),exposed,converted,exposed>0?(double)converted/exposed:null);},EXP)
   .forEach(m->byVariant.put(m.variant(),m));
  int participants=byVariant.values().stream().mapToInt(VariantMetrics::assigned).sum();
  return new Metrics(EXP,enabled,maxParticipants,participants,List.copyOf(byVariant.values()));
 }

 /** Jeton signé par le serveur, pour cette expérience et pour ce patient (un jeton copié d'un autre compte est refusé). */
 private Optional<AbTokens.Claims> valid(String token,Long userId){
  return tokens.verify(token).filter(c->EXP.equals(c.experiment())&&c.userId().equals(userId));
 }
 private Assignment participant(Long userId,String variant){return new Assignment(EXP,true,true,tokens.issue(userId,EXP,variant));}

 static String hashVariant(Long userId){
  try{
   byte[] h=MessageDigest.getInstance("SHA-256").digest((EXP+":"+userId).getBytes(StandardCharsets.UTF_8));
   return (h[0]&0xFF)<128?"A":"B";
  }catch(NoSuchAlgorithmException e){throw new IllegalStateException("SHA-256 indisponible",e);}
 }
 private static String opposite(String v){return "A".equals(v)?"B":"A";}
 private String variantAtRank(int rank){return jdbc.queryForObject("select variant from ab_participant where experiment=? and enrollment_rank=?",String.class,EXP,rank);}
 private Optional<String> variantOf(Long userId){return jdbc.query("select variant from ab_participant where experiment=? and user_id=?",(rs,i)->rs.getString(1),EXP,userId).stream().findFirst();}
}
