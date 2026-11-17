package fr.medislot.security;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class LoginAttemptsTest {
 static final class MovingClock extends Clock {
  Instant now=Instant.parse("2026-11-16T10:00:00Z");
  @Override public java.time.ZoneId getZone(){return ZoneOffset.UTC;}
  @Override public Clock withZone(java.time.ZoneId zone){return this;}
  @Override public Instant instant(){return now;}
 }
 final MovingClock clock=new MovingClock();
 final LoginAttempts attempts=new LoginAttempts(clock);
 void fail(String email,int times){for(int i=0;i<times;i++)attempts.failed(email);}

 @Test void blocksAfterFiveFailuresForTheSameAddress(){
  fail("patient@medislot.test",LoginAttempts.MAX_FAILURES-1);
  assertFalse(attempts.blocked("patient@medislot.test"));
  attempts.failed("patient@medislot.test");
  assertTrue(attempts.blocked("patient@medislot.test"));
 }
 @Test void countsTheAddressWhateverItsCaseOrSpaces(){
  fail(" Patient@MediSlot.test ",LoginAttempts.MAX_FAILURES);
  assertTrue(attempts.blocked("patient@medislot.test"));
 }
 @Test void otherAddressesAreNotBlocked(){
  fail("patient@medislot.test",LoginAttempts.MAX_FAILURES);
  assertFalse(attempts.blocked("autre@medislot.test"));
 }
 @Test void unblocksWhenTheWindowEnds(){
  fail("patient@medislot.test",LoginAttempts.MAX_FAILURES);
  clock.now=clock.now.plus(LoginAttempts.WINDOW);
  assertFalse(attempts.blocked("patient@medislot.test"));
  attempts.failed("patient@medislot.test");
  assertFalse(attempts.blocked("patient@medislot.test"));
 }
 @Test void successResetsTheCounter(){
  fail("patient@medislot.test",LoginAttempts.MAX_FAILURES-1);
  attempts.succeeded("patient@medislot.test");
  fail("patient@medislot.test",LoginAttempts.MAX_FAILURES-1);
  assertFalse(attempts.blocked("patient@medislot.test"));
 }
 @Test void keepsCountingWhenTheTableIsFull(){
  for(int i=0;i<LoginAttempts.MAX_TRACKED;i++)attempts.failed("remplissage"+i+"@exemple.test");
  fail("patient@medislot.test",LoginAttempts.MAX_FAILURES);
  assertTrue(attempts.blocked("patient@medislot.test"));
 }
 @Test void aFloodOfNewAddressesDoesNotUnblockAnAccount(){
  fail("patient@medislot.test",LoginAttempts.MAX_FAILURES);
  for(int i=0;i<2*LoginAttempts.MAX_TRACKED;i++)attempts.failed("remplissage"+i+"@exemple.test");
  assertTrue(attempts.blocked("patient@medislot.test"));
 }
 @Test void missingAddressDoesNotFail(){
  attempts.failed(null);
  assertFalse(attempts.blocked(null));
 }
}
