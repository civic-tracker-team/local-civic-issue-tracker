package org.civictracker.search;

import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import tools.jackson.databind.json.JsonMapper;

/** A loopback stand-in for one owner: exact path-and-query routes with fixed JSON answers. */
final class OwnerStub implements AutoCloseable {
  static final JsonMapper JSON = JsonMapper.builder().build();
  static final String USER = "search-reader";
  static final String PASSWORD = "stub-password";

  private final HttpServer server;
  private final Map<String, Object> routes = new ConcurrentHashMap<>();
  private final Map<String, Integer> failures = new ConcurrentHashMap<>();
  final List<String> requested = new CopyOnWriteArrayList<>();

  OwnerStub() throws IOException {
    server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    var expected =
        "Basic "
            + Base64.getEncoder()
                .encodeToString((USER + ":" + PASSWORD).getBytes(StandardCharsets.UTF_8));
    server.createContext("/", exchange -> {
      var uri = exchange.getRequestURI();
      var key = uri.getRawPath() + (uri.getRawQuery() == null ? "" : "?" + uri.getRawQuery());
      requested.add(key);
      int status;
      byte[] body = new byte[0];
      if (!expected.equals(exchange.getRequestHeaders().getFirst("Authorization"))) {
        status = 401;
      } else if (failures.containsKey(key)) {
        status = failures.get(key);
      } else if (routes.containsKey(key)) {
        status = 200;
        body = JSON.writeValueAsBytes(routes.get(key));
      } else {
        // Owners answer an absent resource with a 404 problem; the coordinator's code for "not
        // published yet" is NO_PUBLICATION.
        status = 404;
        var code =
            uri.getRawPath().equals("/internal/v1/publications/current")
                ? "NO_PUBLICATION"
                : "NOT_FOUND";
        body = JSON.writeValueAsBytes(Map.of("code", code));
      }
      exchange
          .getResponseHeaders()
          .add("Content-Type", status == 404 ? "application/problem+json" : "application/json");
      exchange.sendResponseHeaders(status, body.length == 0 ? -1 : body.length);
      if (body.length > 0) exchange.getResponseBody().write(body);
      exchange.close();
    });
    server.start();
  }

  String baseUrl() {
    return "http://127.0.0.1:" + server.getAddress().getPort();
  }

  void put(String path, Object body) {
    routes.put(path, body);
  }

  void remove(String path) {
    routes.remove(path);
  }

  void fail(String path, int status) {
    failures.put(path, status);
  }

  void reset() {
    routes.clear();
    failures.clear();
    requested.clear();
  }

  @Override
  public void close() {
    server.stop(0);
  }
}
