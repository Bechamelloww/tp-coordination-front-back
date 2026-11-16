package fr.medislot.calendar;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Base64;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.stereotype.Component;

@Component
public class TokenCipher {
 private static final String TRANSFORMATION = "AES/GCM/NoPadding";
 private static final int KEY_LENGTH_BYTES = 32;
 private static final int NONCE_LENGTH_BYTES = 12;
 private static final int TAG_LENGTH_BITS = 128;
 private static final byte[] CONTEXT = "medislot-google-refresh-token".getBytes(StandardCharsets.UTF_8);

 private final SecureRandom random = new SecureRandom();
 private final SecretKeySpec secretKey;

 public TokenCipher(GoogleProperties properties) {
  this.secretKey = properties.enabled() ? parseKey(properties.tokenKey()) : null;
 }

 private static SecretKeySpec parseKey(String encodedKey) {
  if (encodedKey == null || encodedKey.isBlank()) {
   throw new IllegalStateException("GOOGLE_TOKEN_KEY est obligatoire quand GOOGLE_CLIENT_ID est défini.");
  }
  byte[] rawKey;
  try {
   rawKey = Base64.getDecoder().decode(encodedKey.trim());
  } catch (IllegalArgumentException invalidBase64) {
   throw new IllegalStateException("GOOGLE_TOKEN_KEY doit être encodée en base64.");
  }
  if (rawKey.length != KEY_LENGTH_BYTES) {
   throw new IllegalStateException("GOOGLE_TOKEN_KEY doit contenir exactement 32 octets.");
  }
  return new SecretKeySpec(rawKey, "AES");
 }

 public byte[] encrypt(String plainToken, long userId) {
  byte[] nonce = new byte[NONCE_LENGTH_BYTES];
  random.nextBytes(nonce);
  try {
   Cipher cipher = Cipher.getInstance(TRANSFORMATION);
   cipher.init(Cipher.ENCRYPT_MODE, requireKey(), new GCMParameterSpec(TAG_LENGTH_BITS, nonce));
   cipher.updateAAD(associatedData(userId));
   byte[] cipherText = cipher.doFinal(plainToken.getBytes(StandardCharsets.UTF_8));
   return ByteBuffer.allocate(nonce.length + cipherText.length).put(nonce).put(cipherText).array();
  } catch (GeneralSecurityException failure) {
   throw new IllegalStateException("Chiffrement du jeton impossible.", failure);
  }
 }

 public String decrypt(byte[] storedToken, long userId) {
  if (storedToken == null || storedToken.length <= NONCE_LENGTH_BYTES) {
   throw new IllegalStateException("Jeton chiffré invalide.");
  }
  try {
   Cipher cipher = Cipher.getInstance(TRANSFORMATION);
   cipher.init(Cipher.DECRYPT_MODE, requireKey(), new GCMParameterSpec(TAG_LENGTH_BITS, storedToken, 0, NONCE_LENGTH_BYTES));
   cipher.updateAAD(associatedData(userId));
   byte[] plainToken = cipher.doFinal(storedToken, NONCE_LENGTH_BYTES, storedToken.length - NONCE_LENGTH_BYTES);
   return new String(plainToken, StandardCharsets.UTF_8);
  } catch (GeneralSecurityException failure) {
   throw new IllegalStateException("Déchiffrement du jeton impossible.", failure);
  }
 }

 private SecretKeySpec requireKey() {
  if (secretKey == null) {
   throw new IllegalStateException("Google Agenda n'est pas configuré.");
  }
  return secretKey;
 }

 private static byte[] associatedData(long userId) {
  return ByteBuffer.allocate(CONTEXT.length + Long.BYTES).put(CONTEXT).putLong(userId).array();
 }
}
