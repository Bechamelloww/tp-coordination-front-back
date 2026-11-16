package fr.medislot.calendar;

import java.net.http.HttpClient;
import java.time.Duration;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

final class GoogleHttp {
 private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(5);
 private static final Duration READ_TIMEOUT = Duration.ofSeconds(10);

 private GoogleHttp() {
 }

 static RestClient.Builder builderWithTimeouts() {
  HttpClient httpClient = HttpClient.newBuilder()
    .connectTimeout(CONNECT_TIMEOUT)
    .followRedirects(HttpClient.Redirect.NEVER)
    .build();
  JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
  requestFactory.setReadTimeout(READ_TIMEOUT);
  return RestClient.builder().requestFactory(requestFactory);
 }
}
