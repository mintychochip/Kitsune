package org.aincraft.kitsune.embedding;

import java.net.http.HttpClient;
import java.time.Duration;

public class HttpClientFactory {

  private static final Duration TIMEOUT_SECONDS = Duration.ofSeconds(30);

  private HttpClientFactory() {
    throw new UnsupportedOperationException("do not instantiate this class");
  }
  public static HttpClient create() {
    return HttpClient.newBuilder()
        .connectTimeout(TIMEOUT_SECONDS)
        .followRedirects(HttpClient.Redirect.NORMAL)
        .build();
  }
}
