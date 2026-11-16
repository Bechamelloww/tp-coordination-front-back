package fr.medislot;
import fr.medislot.experiment.AbTokens;
import org.junit.jupiter.api.Test;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import static org.junit.jupiter.api.Assertions.*;
class AbTokensTest {
 static final String SECRET="secret-de-test-avec-au-moins-32-octets";
 final AbTokens tokens=new AbTokens(SECRET,Duration.ofHours(1));
 static String b64(String s){return Base64.getUrlEncoder().withoutPadding().encodeToString(s.getBytes(StandardCharsets.UTF_8));}

 @Test void readsVariantFromValidToken(){
  var claims=tokens.verify(tokens.issue(7L,"confirm_label_v1","B")).orElseThrow();
  assertEquals(7L,claims.userId());assertEquals("confirm_label_v1",claims.experiment());assertEquals("B",claims.variant());
 }
 @Test void rejectsVariantChangedByTheClient(){
  String[] p=tokens.issue(7L,"confirm_label_v1","A").split("\\.");
  String forged=p[0]+"."+b64(new String(Base64.getUrlDecoder().decode(p[1]),StandardCharsets.UTF_8).replace("\"A\"","\"B\""))+"."+p[2];
  assertTrue(tokens.verify(forged).isEmpty());
 }
 @Test void rejectsTokenSignedWithAnotherSecret(){
  var other=new AbTokens("un-autre-secret-de-test-de-32-octets!!",Duration.ofHours(1));
  assertTrue(tokens.verify(other.issue(7L,"confirm_label_v1","B")).isEmpty());
 }
 @Test void rejectsExpiredToken(){
  var expired=new AbTokens(SECRET,Duration.ofSeconds(-1));
  assertTrue(tokens.verify(expired.issue(7L,"confirm_label_v1","B")).isEmpty());
 }
 @Test void rejectsUnsignedOrMalformedToken(){
  String payload=b64("{\"sub\":\"7\",\"experiment\":\"confirm_label_v1\",\"variant\":\"B\",\"iat\":0,\"exp\":9999999999}");
  assertTrue(tokens.verify(b64("{\"alg\":\"none\",\"typ\":\"JWT\"}")+"."+payload+".").isEmpty());
  assertTrue(tokens.verify(null).isEmpty());
  assertTrue(tokens.verify("pas-un-jeton").isEmpty());
  assertTrue(tokens.verify("a.b.c!").isEmpty());
 }
 @Test void drawsARandomSecretWhenNoneIsConfigured(){
  AbTokens first=new AbTokens("",Duration.ofHours(1)),second=new AbTokens("",Duration.ofHours(1));
  String token=first.issue(7L,"confirm_label_v1","B");
  assertTrue(first.verify(token).isPresent());
  assertTrue(second.verify(token).isEmpty());
 }
 @Test void refusesShortSecret(){assertThrows(IllegalStateException.class,()->new AbTokens("trop-court",Duration.ofHours(1)));}
}
