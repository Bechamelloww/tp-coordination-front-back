package fr.medislot.security;

import jakarta.servlet.http.HttpSession;
import java.io.Serializable;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;

@Component
public class LoginCodes {
 static final String SESSION_CHALLENGE = "medislot.login.challenge";
 static final Duration VALIDITY = Duration.ofMinutes(10);
 static final int MAX_TRIES = 5;

 public enum Outcome { VALID, WRONG, EXPIRED, TOO_MANY_TRIES }

 public record Result(Outcome outcome, String email) {}

 record Challenge(String email, byte[] salt, byte[] hash, Instant expiresAt, int tries) implements Serializable {}

 private final JavaMailSender mail;
 private final String from;
 private final Clock clock;
 private final SecureRandom random = new SecureRandom();

 @Autowired
 public LoginCodes(JavaMailSender mail, @Value("${medislot.mail-from}") String from) {
  this(mail, from, Clock.systemUTC());
 }

 LoginCodes(JavaMailSender mail, String from, Clock clock) {
  this.mail = mail;
  this.from = from;
  this.clock = clock;
 }

 public void send(HttpSession session, String email) {
  String code = String.format("%06d", random.nextInt(1_000_000));
  byte[] salt = new byte[16];
  random.nextBytes(salt);
  session.setAttribute(SESSION_CHALLENGE, new Challenge(email, salt, hash(salt, code), clock.instant().plus(VALIDITY), 0));
  SimpleMailMessage message = new SimpleMailMessage();
  message.setFrom(from);
  message.setTo(email);
  message.setSubject("Votre code de connexion MediSlot");
  message.setText("Votre code de connexion : " + code + "\n\nIl expire dans " + VALIDITY.toMinutes() + " minutes.\n"
    + "Si vous n'êtes pas à l'origine de cette connexion, ignorez ce message et changez votre mot de passe.");
  try {
   mail.send(message);
  } catch (RuntimeException failure) {
   session.removeAttribute(SESSION_CHALLENGE);
   throw failure;
  }
 }

 public Result verify(HttpSession session, String code) {
  Challenge challenge = session == null ? null : (Challenge) session.getAttribute(SESSION_CHALLENGE);
  if (challenge == null || !clock.instant().isBefore(challenge.expiresAt())) {
   if (session != null) session.removeAttribute(SESSION_CHALLENGE);
   return new Result(Outcome.EXPIRED, null);
  }
  if (code != null && MessageDigest.isEqual(challenge.hash(), hash(challenge.salt(), code))) {
   session.removeAttribute(SESSION_CHALLENGE);
   return new Result(Outcome.VALID, challenge.email());
  }
  int tries = challenge.tries() + 1;
  if (tries >= MAX_TRIES) {
   session.removeAttribute(SESSION_CHALLENGE);
   return new Result(Outcome.TOO_MANY_TRIES, null);
  }
  session.setAttribute(SESSION_CHALLENGE, new Challenge(challenge.email(), challenge.salt(), challenge.hash(), challenge.expiresAt(), tries));
  return new Result(Outcome.WRONG, null);
 }

 static String masked(String email) {
  int at = email.indexOf('@');
  return at <= 1 ? "***" + email.substring(Math.max(at, 0)) : email.charAt(0) + "***" + email.substring(at);
 }

 private static byte[] hash(byte[] salt, String code) {
  try {
   MessageDigest digest = MessageDigest.getInstance("SHA-256");
   digest.update(salt);
   return digest.digest(code.getBytes(StandardCharsets.UTF_8));
  } catch (NoSuchAlgorithmException missing) {
   throw new IllegalStateException(missing);
  }
 }
}
