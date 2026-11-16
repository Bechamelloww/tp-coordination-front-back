package fr.medislot.calendar;

import java.util.Base64;

final class GoogleTestProperties {
 static final String TOKEN_KEY = Base64.getEncoder().encodeToString(new byte[32]);
 static final String TOKEN_URI = "https://oauth.test/token";
 static final String REVOKE_URI = "https://oauth.test/revoke";
 static final String CALENDAR_API_URI = "https://calendar.test/v3";

 private GoogleTestProperties() {
 }

 static GoogleProperties enabled() {
  return withTokenKey(TOKEN_KEY);
 }

 static GoogleProperties withTokenKey(String tokenKey) {
  return new GoogleProperties("client-id", "client-secret", tokenKey, "http://localhost:8088/api/google/callback",
    "https://auth.test/authorize", TOKEN_URI, REVOKE_URI, CALENDAR_API_URI);
 }

 static GoogleProperties disabled() {
  return new GoogleProperties("", "", "", "http://localhost:8088/api/google/callback",
    "https://auth.test/authorize", TOKEN_URI, REVOKE_URI, CALENDAR_API_URI);
 }
}
