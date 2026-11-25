package org.civictracker.coordinator.owners;

import java.net.HttpCookie;
import java.net.URI;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import tools.jackson.core.StreamReadFeature;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** HTTP and wire parsing shared by the two owner clients. */
final class OwnerHttp {
  static final JsonMapper JSON =
      JsonMapper.builder()
          .enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION)
          .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
          .build();
  private final URI base;
  private final RestClient client;

  OwnerHttp(URI base, String username, String password, Duration timeout) {
    if (base.getHost() == null
        || base.getUserInfo() != null
        || base.getQuery() != null
        || base.getFragment() != null
        || !("https".equals(base.getScheme())
            || ("http".equals(base.getScheme())
                && List.of("localhost", "127.0.0.1", "::1", "[::1]").contains(base.getHost())))
        || username == null
        || username.isBlank()
        || username.contains(":")
        || password == null
        || password.isBlank()
        || timeout.isZero()
        || timeout.isNegative()) {
      throw new IllegalArgumentException(
          "Owner connection requires credentials, a timeout and HTTPS or loopback");
    }
    this.base = base;
    var factory =
        new JdkClientHttpRequestFactory(
            HttpClient.newBuilder()
                .connectTimeout(timeout)
                .followRedirects(HttpClient.Redirect.NEVER)
                .build());
    factory.setReadTimeout(timeout);
    client =
        RestClient.builder()
            .baseUrl(base.toString())
            .requestFactory(factory)
            .defaultHeaders(h -> h.setBasicAuth(username, password, StandardCharsets.UTF_8))
            .build();
  }

  URI url(String path) {
    return base.resolve(path);
  }

  <T> Optional<T> get(String path, boolean absentOn404, Function<JsonNode, T> decode)
      throws OwnerRejected, OwnerUnreachable {
    var reply = exchange(HttpMethod.GET, path, null, null);
    if (reply.status == 404 && absentOn404) {
      return Optional.empty();
    }
    check(reply);
    try {
      return Optional.of(decode.apply(JSON.readTree(reply.body)));
    } catch (RuntimeException invalid) {
      throw new OwnerUnreachable();
    }
  }

  <T> List<T> pages(String path, Function<JsonNode, T> decode)
      throws OwnerRejected, OwnerUnreachable {
    var result = new ArrayList<T>();
    for (int page = 0; ; page++) {
      int expectedPage = page;
      var part =
          get(
                  path + "?page=" + page + "&size=100",
                  false,
                  node -> {
                    var items = node.get("items");
                    var next = node.get("hasNext");
                    if (items == null
                        || !items.isArray()
                        || next == null
                        || !next.isBoolean()
                        || number(node, "page") != expectedPage) {
                      throw new IllegalArgumentException();
                    }
                    var values = new ArrayList<T>();
                    items.forEach(item -> values.add(decode.apply(item)));
                    if (next.booleanValue() && values.isEmpty()) {
                      throw new IllegalArgumentException();
                    }
                    return new Page<>(List.copyOf(values), next.booleanValue());
                  })
              .orElseThrow();
      result.addAll(part.items);
      if (!part.hasNext) {
        return List.copyOf(result);
      }
    }
  }

  void post(String path, Object command) throws OwnerRejected, OwnerUnreachable {
    postJson(path, JSON.writeValueAsString(command));
  }

  void postJson(String path, String json) throws OwnerRejected, OwnerUnreachable {
    var token = exchange(HttpMethod.GET, "/internal/v1/csrf", null, null);
    check(token);
    var headers = new HttpHeaders();
    try {
      var csrf = JSON.readTree(token.body);
      headers.set(text(csrf, "headerName"), text(csrf, "token"));
      var cookies = new ArrayList<String>();
      for (String value : token.cookies) {
        for (var cookie : HttpCookie.parse(value)) {
          cookies.add(cookie.getName() + "=" + cookie.getValue());
        }
      }
      if (cookies.isEmpty()) {
        throw new IllegalArgumentException();
      }
      headers.set(HttpHeaders.COOKIE, String.join("; ", cookies));
    } catch (RuntimeException invalid) {
      throw new OwnerUnreachable();
    }
    check(exchange(HttpMethod.POST, path, json, headers));
  }

  private Reply exchange(HttpMethod method, String path, String json, HttpHeaders headers)
      throws OwnerUnreachable {
    try {
      var request = client.method(method).uri(path).accept(MediaType.APPLICATION_JSON);
      if (headers != null) {
        request.headers(h -> h.addAll(headers));
      }
      if (json != null) {
        request.contentType(MediaType.APPLICATION_JSON).body(json);
      }
      return request.exchange((req, res) ->
          new Reply(
              res.getStatusCode().value(),
              new String(res.getBody().readAllBytes(), StandardCharsets.UTF_8),
              List.copyOf(res.getHeaders().getOrDefault(HttpHeaders.SET_COOKIE, List.of()))));
    } catch (RestClientException failure) {
      throw new OwnerUnreachable();
    }
  }

  private static void check(Reply reply) throws OwnerRejected, OwnerUnreachable {
    if (reply.status >= 200 && reply.status < 300) {
      return;
    }
    if (reply.status >= 400 && reply.status < 500) {
      String code = "HTTP_" + reply.status;
      try {
        code = text(JSON.readTree(reply.body), "code");
      } catch (RuntimeException ignored) {
        /* The status still identifies the rejection. */
      }
      throw new OwnerRejected(reply.status, code);
    }
    throw new OwnerUnreachable();
  }

  static String text(JsonNode node, String field) {
    var value = node.get(field);
    if (value == null || !value.isString()) {
      throw new IllegalArgumentException();
    }
    return value.stringValue();
  }

  static String optionalText(JsonNode node, String field) {
    var value = node.get(field);
    return value == null || value.isNull() ? null : text(node, field);
  }

  static UUID id(JsonNode node, String field) {
    return UUID.fromString(text(node, field));
  }

  static UUID optionalId(JsonNode node, String field) {
    var value = optionalText(node, field);
    return value == null ? null : UUID.fromString(value);
  }

  static LocalDate date(JsonNode node, String field) {
    var value = optionalText(node, field);
    return value == null ? null : LocalDate.parse(value);
  }

  static long number(JsonNode node, String field) {
    var value = node.get(field);
    if (value == null || !value.isIntegralNumber() || !value.canConvertToLong()) {
      throw new IllegalArgumentException();
    }
    return value.longValue();
  }

  private record Reply(int status, String body, List<String> cookies) {}

  private record Page<T>(List<T> items, boolean hasNext) {}
}
