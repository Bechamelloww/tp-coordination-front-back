package fr.medislot.security;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
@Component
public class LoginAttempts {
 static final int MAX_FAILURES=5;
 static final Duration WINDOW=Duration.ofMinutes(15);
 static final int MAX_TRACKED=10_000;
 private record Failures(int count,Instant since){}
 private final Map<String,Failures> byEmail=new ConcurrentHashMap<>();
 private final Clock clock;
 public LoginAttempts(){this(Clock.systemUTC());}
 LoginAttempts(Clock clock){this.clock=clock;}
 static String key(String email){return email==null?"":email.strip().toLowerCase(Locale.ROOT);}
 private boolean expired(Failures f){return !f.since().plus(WINDOW).isAfter(clock.instant());}
 public boolean blocked(String email){
  Failures f=byEmail.get(key(email));
  return f!=null&&!expired(f)&&f.count()>=MAX_FAILURES;
 }
 public void failed(String email){
  String k=key(email);
  if(!byEmail.containsKey(k)&&byEmail.size()>=MAX_TRACKED){purge();if(byEmail.size()>=MAX_TRACKED)return;}
  byEmail.compute(k,(ignored,f)->f==null||expired(f)?new Failures(1,clock.instant()):new Failures(f.count()+1,f.since()));
 }
 public void succeeded(String email){byEmail.remove(key(email));}
 @Scheduled(fixedDelay=600_000) public void purge(){byEmail.values().removeIf(this::expired);}
}
