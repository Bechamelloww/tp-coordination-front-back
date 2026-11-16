package fr.medislot.calendar;

import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriComponentsBuilder;

@Component
public class GoogleOAuthClient {
 public static final String CALENDAR_SCOPE = "https://www.googleapis.com/auth/calendar.app.created";
 private static final ParameterizedTypeReference<Map<String, Object>> JSON_OBJECT = new ParameterizedTypeReference<>() {
 };

 public record GrantedTokens(String accessToken, String refreshToken, String grantedScopes) {
  public boolean includesCalendarScope() {
   return grantedScopes != null && java.util.Arrays.asList(grantedScopes.split(" ")).contains(CALENDAR_SCOPE);
  }
 }

 private final GoogleProperties properties;
 private final RestClient http;

 @Autowired
 public GoogleOAuthClient(GoogleProperties properties) {
  this(properties, GoogleHttp.builderWithTimeouts());
 }

 GoogleOAuthClient(GoogleProperties properties, RestClient.Builder httpBuilder) {
  this.properties = properties;
  this.http = httpBuilder.build();
 }

 public String authorizationUrl(String state, String codeChallenge) {
  return UriComponentsBuilder.fromUriString(properties.authorizationUri())
    .queryParam("client_id", properties.clientId())
    .queryParam("redirect_uri", properties.redirectUri())
    .queryParam("response_type", "code")
    .queryParam("scope", CALENDAR_SCOPE)
    .queryParam("access_type", "offline")
    .queryParam("prompt", "consent")
    .queryParam("state", state)
    .queryParam("code_challenge", codeChallenge)
    .queryParam("code_challenge_method", "S256")
    .encode()
    .build()
    .toUriString();
 }

 public GrantedTokens exchangeCode(String authorizationCode, String codeVerifier) {
  MultiValueMap<String, String> form = clientCredentials();
  form.add("grant_type", "authorization_code");
  form.add("code", authorizationCode);
  form.add("code_verifier", codeVerifier);
  form.add("redirect_uri", properties.redirectUri());
  Map<String, Object> response = postForm(form);
  return new GrantedTokens(text(response, "access_token"), text(response, "refresh_token"), text(response, "scope"));
 }

 public String refreshAccessToken(String refreshToken) {
  MultiValueMap<String, String> form = clientCredentials();
  form.add("grant_type", "refresh_token");
  form.add("refresh_token", refreshToken);
  try {
   return text(postForm(form), "access_token");
  } catch (HttpClientErrorException rejected) {
   if (rejected.getStatusCode().isSameCodeAs(HttpStatus.BAD_REQUEST)
     && rejected.getResponseBodyAsString().contains("invalid_grant")) {
    throw new GoogleAccessRevokedException();
   }
   throw rejected;
  }
 }

 public void revoke(String token) {
  MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
  form.add("token", token);
  http.post()
    .uri(properties.revokeUri())
    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
    .body(form)
    .retrieve()
    .toBodilessEntity();
 }

 private MultiValueMap<String, String> clientCredentials() {
  MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
  form.add("client_id", properties.clientId());
  form.add("client_secret", properties.clientSecret());
  return form;
 }

 private Map<String, Object> postForm(MultiValueMap<String, String> form) {
  Map<String, Object> response = http.post()
    .uri(properties.tokenUri())
    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
    .accept(MediaType.APPLICATION_JSON)
    .body(form)
    .retrieve()
    .body(JSON_OBJECT);
  return response == null ? Map.of() : response;
 }

 private static String text(Map<String, Object> response, String field) {
  Object value = response.get(field);
  return value == null ? null : value.toString();
 }
}
