package fr.medislot.experiment;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Jeton de variante au format JWT HS256 : en-tête.charge.signature, en base64url.
 * Charge : {"sub":"<id patient>","experiment":"…","variant":"A|B","iat":…,"exp":…}.
 * Le front lit la variante dans la charge ; seul le serveur, qui détient le secret, peut la signer.
 */
@Component
public class AbTokens {
 public record Claims(Long userId,String experiment,String variant){}
 private static final Base64.Encoder ENC=Base64.getUrlEncoder().withoutPadding();
 private static final Base64.Decoder DEC=Base64.getUrlDecoder();
 // En-tête fixe : tout autre algorithme (dont "none") est refusé.
 private static final String HEADER=ENC.encodeToString("{\"alg\":\"HS256\",\"typ\":\"JWT\"}".getBytes(StandardCharsets.UTF_8));
 private static final Pattern SUB=Pattern.compile("\"sub\":\"(\\d+)\""),EXPERIMENT=Pattern.compile("\"experiment\":\"([a-z0-9_]+)\""),VARIANT=Pattern.compile("\"variant\":\"([AB])\""),EXP=Pattern.compile("\"exp\":(-?\\d+)");
 private final byte[] secret; private final Duration ttl;

 public AbTokens(@Value("${medislot.ab.token-secret}") String secret,@Value("${medislot.ab.token-ttl:PT24H}") Duration ttl){
  this.secret=secret.getBytes(StandardCharsets.UTF_8);this.ttl=ttl;
  if(this.secret.length<32)throw new IllegalStateException("medislot.ab.token-secret doit contenir au moins 32 octets.");
 }

 public String issue(Long userId,String experiment,String variant){
  long now=Instant.now().getEpochSecond();
  String payload="{\"sub\":\"%d\",\"experiment\":\"%s\",\"variant\":\"%s\",\"iat\":%d,\"exp\":%d}".formatted(userId,experiment,variant,now,now+ttl.toSeconds());
  String signed=HEADER+"."+ENC.encodeToString(payload.getBytes(StandardCharsets.UTF_8));
  return signed+"."+ENC.encodeToString(hmac(signed));
 }

 /** Vide si le jeton est absent, mal formé, falsifié ou expiré. La charge n'est lue qu'après la signature. */
 public Optional<Claims> verify(String token){
  if(token==null||token.length()>1024)return Optional.empty();
  String[] parts=token.split("\\.",-1);
  if(parts.length!=3||!parts[0].equals(HEADER))return Optional.empty();
  try{
   if(!MessageDigest.isEqual(hmac(parts[0]+"."+parts[1]),DEC.decode(parts[2])))return Optional.empty();
   String payload=new String(DEC.decode(parts[1]),StandardCharsets.UTF_8);
   Matcher sub=SUB.matcher(payload),experiment=EXPERIMENT.matcher(payload),variant=VARIANT.matcher(payload),exp=EXP.matcher(payload);
   if(!sub.find()||!experiment.find()||!variant.find()||!exp.find())return Optional.empty();
   if(Long.parseLong(exp.group(1))<=Instant.now().getEpochSecond())return Optional.empty();
   return Optional.of(new Claims(Long.valueOf(sub.group(1)),experiment.group(1),variant.group(1)));
  }catch(IllegalArgumentException e){return Optional.empty();}
 }

 private byte[] hmac(String data){
  try{
   Mac mac=Mac.getInstance("HmacSHA256");mac.init(new SecretKeySpec(secret,"HmacSHA256"));
   return mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
  }catch(NoSuchAlgorithmException|InvalidKeyException e){throw new IllegalStateException("HmacSHA256 indisponible",e);}
 }
}
