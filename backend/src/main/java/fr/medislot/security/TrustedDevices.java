package fr.medislot.security;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.Base64;
import java.util.HexFormat;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseCookie;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class TrustedDevices {
 static final String COOKIE = "medislot_appareil";
 static final Duration VALIDITY = Duration.ofDays(30);
 private static final int TOKEN_BYTES = 32;

 private final JdbcTemplate jdbc;
 private final boolean secureCookie;
 private final SecureRandom random = new SecureRandom();

 public TrustedDevices(JdbcTemplate jdbc, @Value("${server.servlet.session.cookie.secure:false}") boolean secureCookie) {
  this.jdbc = jdbc;
  this.secureCookie = secureCookie;
 }

 public boolean trusted(long userId, HttpServletRequest request) {
  String token = token(request);
  if (token == null) return false;
  return Boolean.TRUE.equals(jdbc.queryForObject(
    "select exists(select 1 from trusted_device where user_id=? and token_hash=? and expires_at>now())",
    Boolean.class, userId, hash(token)));
 }

 public ResponseCookie remember(long userId) {
  byte[] bytes = new byte[TOKEN_BYTES];
  random.nextBytes(bytes);
  String token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
  jdbc.update("insert into trusted_device(user_id,token_hash,expires_at) values (?,?,?)",
    userId, hash(token), Timestamp.from(Instant.now().plus(VALIDITY)));
  return ResponseCookie.from(COOKIE, token).httpOnly(true).secure(secureCookie).sameSite("Lax")
    .path("/api/login").maxAge(VALIDITY).build();
 }

 @Scheduled(fixedDelay = 86_400_000)
 public void purge() {
  jdbc.update("delete from trusted_device where expires_at<=now()");
 }

 private static String token(HttpServletRequest request) {
  Cookie[] cookies = request.getCookies();
  if (cookies == null) return null;
  return Arrays.stream(cookies).filter(c -> COOKIE.equals(c.getName())).map(Cookie::getValue)
    .filter(v -> v != null && v.length() <= 64).findFirst().orElse(null);
 }

 private static String hash(String token) {
  try {
   return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8)));
  } catch (NoSuchAlgorithmException missing) {
   throw new IllegalStateException(missing);
  }
 }
}
