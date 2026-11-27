package org.civictracker.search.owners;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import java.util.Optional;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** Authenticated JSON reads from one owner. No redirects or retries; 404 is a distinct answer. */
public final class OwnerHttp {
  static final JsonMapper JSON = JsonMapper.builder().build();

  private final String owner;
  private final URI base;
  private final Duration timeout;
  private final String authorization;
  private final HttpClient client;

  public OwnerHttp(String owner, URI base, Duration timeout, String user, String password) {
    if (base == null
        || base.getScheme() == null
        || base.getHost() == null
        || base.getUserInfo() != null
        || base.getQuery() != null
        || base.getFragment() != null
        || !(base.getPath() == null || base.getPath().isEmpty() || base.getPath().equals("/"))
        || !(base.getScheme().equals("https")
            || (base.getScheme().equals("http")
                && (base.getHost().equals("127.0.0.1") || base.getHost().equals("[::1]"))))) {
      throw new IllegalArgumentException(
          "Owner " + owner + " needs an HTTPS or explicit loopback HTTP base URL");
    }
    if (timeout == null || timeout.isNegative() || timeout.isZero()) {
      throw new IllegalArgumentException("Owner timeout must be positive");
    }
    if (user == null || user.isBlank() || user.contains(":") || password == null) {
      throw new IllegalArgumentException("Owner " + owner + " needs a service identity");
    }
    this.owner = owner;
    this.base = base;
    this.timeout = timeout;
    authorization =
        "Basic "
            + Base64.getEncoder()
                .encodeToString((user + ":" + password).getBytes(StandardCharsets.UTF_8));
    client =
        HttpClient.newBuilder()
            .connectTimeout(timeout)
            .followRedirects(HttpClient.Redirect.NEVER)
            .build();
  }

  /** Reads a resource that must exist. */
  public JsonNode get(String path) throws OwnerUnavailable, InterruptedException {
    return find(path, null).orElseThrow();
  }

  /**
   * Reads a resource whose absence is a meaningful answer: a 404 problem carrying {@code
   * absentCode}. Any other 404, such as a wrong base URL or route, is a failure.
   */
  public Optional<JsonNode> find(String path, String absentCode)
      throws OwnerUnavailable, InterruptedException {
    if (!path.startsWith("/") || path.startsWith("//")) {
      throw new IllegalArgumentException("Invalid owner path");
    }
    var request =
        HttpRequest.newBuilder(base.resolve(path))
            .timeout(timeout)
            .header("Accept", "application/json")
            .header("Authorization", authorization)
            .GET()
            .build();
    var pending = client.sendAsync(request, HttpResponse.BodyHandlers.ofString());
    HttpResponse<String> response;
    try {
      response = pending.get(timeout.toNanos(), TimeUnit.NANOSECONDS);
    } catch (TimeoutException error) {
      pending.cancel(true);
      throw new OwnerUnavailable("OWNER_UNREACHABLE", owner + " timed out at " + route(path));
    } catch (InterruptedException error) {
      pending.cancel(true);
      throw error;
    } catch (ExecutionException error) {
      throw new OwnerUnavailable(
          "OWNER_UNREACHABLE", owner + " unreachable at " + route(path), error.getCause());
    }
    if (response.statusCode() == 404) {
      if (absentCode != null && absentCode.equals(problemCode(response.body()))) {
        return Optional.empty();
      }
      throw new OwnerUnavailable("OWNER_NOT_FOUND", owner + " 404 at " + route(path));
    }
    if (response.statusCode() != 200) {
      throw new OwnerUnavailable(
          "OWNER_UNAVAILABLE", owner + " HTTP " + response.statusCode() + " at " + route(path));
    }
    try {
      return Optional.of(JSON.readTree(response.body()));
    } catch (JacksonException error) {
      throw new OwnerUnavailable("OWNER_INVALID_RESPONSE", owner + " sent unreadable JSON", error);
    }
  }

  private static String problemCode(String body) {
    try {
      var code = JSON.readTree(body).path("code");
      return code.isString() ? code.asString() : null;
    } catch (JacksonException error) {
      return null;
    }
  }

  private static String route(String path) {
    return path.split("\\?")[0];
  }
}
