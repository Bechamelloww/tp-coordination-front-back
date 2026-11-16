package fr.medislot.calendar;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Base64;
import org.junit.jupiter.api.Test;

class TokenCipherTest {
 private final TokenCipher tokenCipher = new TokenCipher(GoogleTestProperties.enabled());

 @Test
 void leJetonDechiffreEstIdentiqueAuJetonChiffre() {
  byte[] encrypted = tokenCipher.encrypt("refresh-token-secret", 7L);
  assertEquals("refresh-token-secret", tokenCipher.decrypt(encrypted, 7L));
 }

 @Test
 void leJetonStockeNeContientPasLeJetonEnClair() {
  byte[] encrypted = tokenCipher.encrypt("refresh-token-secret", 7L);
  assertFalse(new String(encrypted, StandardCharsets.ISO_8859_1).contains("refresh-token-secret"));
 }

 @Test
 void deuxChiffrementsDuMemeJetonDifferent() {
  byte[] first = tokenCipher.encrypt("refresh-token-secret", 7L);
  byte[] second = tokenCipher.encrypt("refresh-token-secret", 7L);
  assertFalse(Arrays.equals(first, second));
 }

 @Test
 void unJetonRecolleSurUnAutreCompteEstRefuse() {
  byte[] encrypted = tokenCipher.encrypt("refresh-token-secret", 7L);
  assertThrows(IllegalStateException.class, () -> tokenCipher.decrypt(encrypted, 8L));
 }

 @Test
 void unJetonModifieEstRefuse() {
  byte[] encrypted = tokenCipher.encrypt("refresh-token-secret", 7L);
  encrypted[encrypted.length - 1] ^= 1;
  assertThrows(IllegalStateException.class, () -> tokenCipher.decrypt(encrypted, 7L));
 }

 @Test
 void uneCleAbsenteBloqueLeDemarrage() {
  assertThrows(IllegalStateException.class, () -> new TokenCipher(GoogleTestProperties.withTokenKey("")));
 }

 @Test
 void uneCleTropCourteBloqueLeDemarrage() {
  String shortKey = Base64.getEncoder().encodeToString(new byte[16]);
  assertThrows(IllegalStateException.class, () -> new TokenCipher(GoogleTestProperties.withTokenKey(shortKey)));
 }

 @Test
 void uneCleNonBase64BloqueLeDemarrage() {
  assertThrows(IllegalStateException.class, () -> new TokenCipher(GoogleTestProperties.withTokenKey("pas du base64 !")));
 }

 @Test
 void sansGoogleLaCleNEstPasExigee() {
  TokenCipher disabledCipher = new TokenCipher(GoogleTestProperties.disabled());
  assertThrows(IllegalStateException.class, () -> disabledCipher.encrypt("refresh-token-secret", 7L));
 }
}
