package fr.medislot.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.mail.MailSendException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mock.web.MockHttpSession;

class LoginCodesTest {
 static final class MovingClock extends Clock {
  Instant now = Instant.parse("2026-11-17T10:00:00Z");
  @Override public java.time.ZoneId getZone() { return ZoneOffset.UTC; }
  @Override public Clock withZone(java.time.ZoneId zone) { return this; }
  @Override public Instant instant() { return now; }
 }

 final JavaMailSender mail = mock(JavaMailSender.class);
 final MovingClock clock = new MovingClock();
 final LoginCodes codes = new LoginCodes(mail, "MediSlot <no-reply@medislot.test>", clock);
 final MockHttpSession session = new MockHttpSession();

 String sentCode() {
  ArgumentCaptor<SimpleMailMessage> sent = ArgumentCaptor.forClass(SimpleMailMessage.class);
  verify(mail).send(sent.capture());
  assertEquals("patient@medislot.test", sent.getValue().getTo()[0]);
  Matcher m = Pattern.compile("\\b(\\d{6})\\b").matcher(sent.getValue().getText());
  assertTrue(m.find());
  return m.group(1);
 }

 @Test void theMailedCodeSignsInOnce() {
  codes.send(session, "patient@medislot.test");
  String code = sentCode();
  LoginCodes.Result result = codes.verify(session, code);
  assertEquals(LoginCodes.Outcome.VALID, result.outcome());
  assertEquals("patient@medislot.test", result.email());
  assertEquals(LoginCodes.Outcome.EXPIRED, codes.verify(session, code).outcome());
 }

 @Test void aWrongCodeIsRefusedThenTheChallengeIsDroppedAfterFiveTries() {
  codes.send(session, "patient@medislot.test");
  String code = sentCode();
  String wrong = code.equals("000000") ? "111111" : "000000";
  for (int i = 1; i < LoginCodes.MAX_TRIES; i++) assertEquals(LoginCodes.Outcome.WRONG, codes.verify(session, wrong).outcome());
  assertEquals(LoginCodes.Outcome.TOO_MANY_TRIES, codes.verify(session, wrong).outcome());
  assertEquals(LoginCodes.Outcome.EXPIRED, codes.verify(session, code).outcome());
 }

 @Test void theCodeExpiresAfterTenMinutes() {
  codes.send(session, "patient@medislot.test");
  String code = sentCode();
  clock.now = clock.now.plus(LoginCodes.VALIDITY);
  assertEquals(LoginCodes.Outcome.EXPIRED, codes.verify(session, code).outcome());
 }

 @Test void noSessionOrNoChallengeIsExpired() {
  assertEquals(LoginCodes.Outcome.EXPIRED, codes.verify(null, "123456").outcome());
  assertEquals(LoginCodes.Outcome.EXPIRED, codes.verify(session, "123456").outcome());
 }

 @Test void aMailFailureLeavesNoChallenge() {
  doThrow(new MailSendException("smtp down")).when(mail).send(any(SimpleMailMessage.class));
  assertThrows(MailSendException.class, () -> codes.send(session, "patient@medislot.test"));
  assertNull(session.getAttribute(LoginCodes.SESSION_CHALLENGE));
 }

 @Test void theDestinationIsMasked() {
  assertEquals("p***@medislot.test", LoginCodes.masked("patient@medislot.test"));
  assertEquals("***@x.test", LoginCodes.masked("a@x.test"));
 }
}
