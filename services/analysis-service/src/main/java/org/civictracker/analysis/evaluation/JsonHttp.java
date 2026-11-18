package org.civictracker.analysis.evaluation;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import tools.jackson.databind.JsonNode;

/** No redirects or retries. Callers own the meaning of a lost response. */
public final class JsonHttp {
  private final URI base;
  private final Duration timeout;
  private final String authorization;
  private final HttpClient client;

  public JsonHttp(URI base, Duration timeout, String authorization) {
    if (base.getHost() == null
        || base.getUserInfo() != null
        || base.getQuery() != null
        || base.getFragment() != null
        || !(base.getPath().isEmpty() || base.getPath().equals("/"))
        || (!base.getScheme().equals("https")
            && !(base.getScheme().equals("http")
                && (base.getHost().equals("127.0.0.1") || base.getHost().equals("[::1]"))))
        || timeout.isNegative()
        || timeout.isZero()) {
      throw new IllegalArgumentException("Use HTTPS or an explicit loopback HTTP endpoint");
    }
    this.base = base;
    this.timeout = timeout;
    this.authorization = authorization;
    client =
        HttpClient.newBuilder()
            .connectTimeout(timeout)
            .followRedirects(HttpClient.Redirect.NEVER)
            .build();
  }

  public JsonNode get(String path) throws IOException, InterruptedException {
    return send(path, null);
  }

  public JsonNode post(String path, Object body) throws IOException, InterruptedException {
    return send(path, body);
  }

  private JsonNode send(String path, Object body) throws IOException, InterruptedException {
    if (!path.startsWith("/") || path.startsWith("//"))
      throw new IllegalArgumentException("Invalid API path");
    var request =
        HttpRequest.newBuilder(base.resolve(path))
            .timeout(timeout)
            .header("Accept", "application/json");
    if (authorization != null) request.header("Authorization", authorization);
    if (body == null) request.GET();
    else
      request
          .header("Content-Type", "application/json")
          .POST(
              HttpRequest.BodyPublishers.ofString(EvaluationJson.MAPPER.writeValueAsString(body)));
    var pending = client.sendAsync(request.build(), HttpResponse.BodyHandlers.ofString());
    HttpResponse<String> response;
    try {
      response = pending.get(timeout.toNanos(), TimeUnit.NANOSECONDS);
    } catch (TimeoutException exception) {
      pending.cancel(true);
      throw new java.net.http.HttpTimeoutException("Response deadline exceeded");
    } catch (InterruptedException exception) {
      pending.cancel(true);
      throw exception;
    } catch (ExecutionException exception) {
      if (exception.getCause() instanceof IOException io) throw io;
      throw new IOException("HTTP request failed", exception.getCause());
    }
    if (response.statusCode() != 200)
      throw new IOException("HTTP " + response.statusCode() + " at " + path.split("\\?")[0]);
    return EvaluationJson.MAPPER.readTree(response.body());
  }
}
