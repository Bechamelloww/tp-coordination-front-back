package fr.medislot.calendar;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class GoogleProperties {
 private final String clientId;
 private final String clientSecret;
 private final String tokenKey;
 private final String redirectUri;
 private final String authorizationUri;
 private final String tokenUri;
 private final String revokeUri;
 private final String calendarApiUri;
 private final String loginRedirectUri;
 private final String userInfoUri;

 public GoogleProperties(
   String clientId,
   String clientSecret,
   String tokenKey,
   String redirectUri,
   String authorizationUri,
   String tokenUri,
   String revokeUri,
   String calendarApiUri) {
  this(clientId, clientSecret, tokenKey, redirectUri, authorizationUri, tokenUri, revokeUri, calendarApiUri,
    "http://localhost:8088/api/auth/google/callback", "https://openidconnect.googleapis.com/v1/userinfo");
 }

 @Autowired
 public GoogleProperties(
   @Value("${medislot.google.client-id:}") String clientId,
   @Value("${medislot.google.client-secret:}") String clientSecret,
   @Value("${medislot.google.token-key:}") String tokenKey,
   @Value("${medislot.google.redirect-uri:http://localhost:8088/api/google/callback}") String redirectUri,
   @Value("${medislot.google.authorization-uri:https://accounts.google.com/o/oauth2/v2/auth}") String authorizationUri,
   @Value("${medislot.google.token-uri:https://oauth2.googleapis.com/token}") String tokenUri,
   @Value("${medislot.google.revoke-uri:https://oauth2.googleapis.com/revoke}") String revokeUri,
   @Value("${medislot.google.calendar-api-uri:https://www.googleapis.com/calendar/v3}") String calendarApiUri,
   @Value("${medislot.google.login-redirect-uri:http://localhost:8088/api/auth/google/callback}") String loginRedirectUri,
   @Value("${medislot.google.userinfo-uri:https://openidconnect.googleapis.com/v1/userinfo}") String userInfoUri) {
  this.clientId = clientId;
  this.clientSecret = clientSecret;
  this.tokenKey = tokenKey;
  this.redirectUri = redirectUri;
  this.authorizationUri = authorizationUri;
  this.tokenUri = tokenUri;
  this.revokeUri = revokeUri;
  this.calendarApiUri = calendarApiUri;
  this.loginRedirectUri = loginRedirectUri;
  this.userInfoUri = userInfoUri;
 }

 public boolean enabled() {
  return clientId != null && !clientId.isBlank();
 }

 public String clientId() { return clientId; }
 public String clientSecret() { return clientSecret; }
 public String tokenKey() { return tokenKey; }
 public String redirectUri() { return redirectUri; }
 public String authorizationUri() { return authorizationUri; }
 public String tokenUri() { return tokenUri; }
 public String revokeUri() { return revokeUri; }
 public String calendarApiUri() { return calendarApiUri; }
 public String loginRedirectUri() { return loginRedirectUri; }
 public String userInfoUri() { return userInfoUri; }
}
