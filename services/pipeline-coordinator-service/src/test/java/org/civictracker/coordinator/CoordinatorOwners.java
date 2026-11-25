package org.civictracker.coordinator;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.function.IntFunction;
import org.civictracker.coordinator.run.StepKind;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** Loopback operation owners. No portal, OCR or model endpoints are involved. */
final class CoordinatorOwners implements AutoCloseable {
  static final JsonMapper JSON = JsonMapper.builder().build();
  final Owner records = new Owner(false);
  final Owner analysis = new Owner(true);

  void reset() {
    records.reset();
    analysis.reset();
  }

  @Override
  public void close() {
    records.close();
    analysis.close();
  }

  static UUID id(long value) {
    return new UUID(0, value);
  }

  static final class Operation {
    final UUID id;
    final StepKind kind;
    final JsonNode command;
    final String bytes;
    volatile String state = "SUCCEEDED";
    volatile UUID blocked;
    volatile String error;

    Operation(UUID id, StepKind kind, JsonNode command, String bytes) {
      this.id = id;
      this.kind = kind;
      this.command = command;
      this.bytes = bytes;
    }
  }

  static final class Owner implements AutoCloseable {
    final boolean isAnalysis;
    final HttpServer server;
    final java.util.concurrent.ExecutorService executor = Executors.newCachedThreadPool();
    final Map<UUID, Operation> operations = new ConcurrentHashMap<>();
    final List<Request> requests = new CopyOnWriteArrayList<>();
    final AtomicInteger nonce = new AtomicInteger();
    final AtomicInteger discoveryReads = new AtomicInteger();
    final AtomicReference<Throwable> failure = new AtomicReference<>();
    volatile StepKind failKind;
    volatile StepKind holdKind;
    volatile boolean blocked;
    volatile boolean unreachable;
    volatile boolean lostPost;
    volatile boolean badPinJoin;
    volatile boolean undated;
    volatile int meetingCount = 3;
    volatile java.util.Set<UUID> withoutOriginals = java.util.Set.of(id(102));
    volatile Consumer<Request> beforeReply = r -> {};
    volatile IntFunction<List<Map<String, Object>>> discovery = this::meetings;

    Owner(boolean analysis) {
      isAnalysis = analysis;
      try {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.setExecutor(executor);
        server.createContext("/", this::serve);
        server.start();
      } catch (IOException error) {
        throw new IllegalStateException(error);
      }
    }

    String base() {
      return "http://127.0.0.1:" + server.getAddress().getPort();
    }

    void reset() {
      tokens.clear();
      operations.clear();
      requests.clear();
      failure.set(null);
      discoveryReads.set(0);
      failKind = null;
      holdKind = null;
      blocked = false;
      unreachable = false;
      lostPost = false;
      revisionProblem = null;
      badPinJoin = false;
      undated = false;
      meetingCount = 3;
      withoutOriginals = java.util.Set.of(id(102));
      beforeReply = r -> {};
      discovery = this::meetings;
    }

    @Override
    public void close() {
      server.stop(0);
      executor.shutdownNow();
    }

    volatile String revisionProblem;

    private void serve(HttpExchange exchange) throws IOException {
      try {
        var request =
            new Request(
                exchange.getRequestMethod(),
                exchange.getRequestURI(),
                new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
        requests.add(request);
        String auth =
            "Basic "
                + Base64.getEncoder()
                    .encodeToString("owner:synthetic-password".getBytes(StandardCharsets.UTF_8));
        if (!auth.equals(exchange.getRequestHeaders().getFirst("Authorization"))) {
          reply(exchange, 401, Map.of("code", "AUTH_REQUIRED"));
          return;
        }
        if (request.uri.getPath().equals("/internal/v1/csrf")) {
          String cookie = UUID.randomUUID().toString();
          String token = "token-" + nonce.incrementAndGet();
          exchange
              .getResponseHeaders()
              .add("Set-Cookie", "session=" + cookie + "; Path=/; HttpOnly");
          tokens.put(cookie, token);
          reply(exchange, 200, Map.of("headerName", "X-CSRF-TOKEN", "token", token));
          return;
        }
        beforeReply.accept(request);
        if (unreachable) {
          reply(exchange, 503, Map.of("code", "UNAVAILABLE"));
          return;
        }
        if (request.method.equals("POST")) {
          String cookie = exchange.getRequestHeaders().getFirst("Cookie");
          String token = cookie == null ? null : tokens.remove(cookie.replace("session=", ""));
          if (token == null
              || !token.equals(exchange.getRequestHeaders().getFirst("X-CSRF-TOKEN"))) {
            reply(exchange, 403, Map.of("code", "CSRF_FAILED"));
            return;
          }
          JsonNode command = JSON.readTree(request.body);
          UUID id = UUID.fromString(command.get(isAnalysis ? "id" : "operationId").stringValue());
          StepKind kind =
              isAnalysis
                  ? StepKind.ANALYZE
                  : request.uri.getPath().contains("discovery")
                      ? StepKind.DISCOVER
                      : request.uri.getPath().contains("acquisition")
                          ? StepKind.ACQUIRE
                          : StepKind.PREPARE;
          var operation = new Operation(id, kind, command, request.body);
          if (kind == failKind) {
            operation.state = "FAILED";
            operation.error = "SYNTHETIC_OWNER_FAILURE";
            failKind = null;
          }
          if (kind == holdKind) {
            operation.state = "RUNNING";
            operation.blocked = blocked ? UUID.randomUUID() : null;
          }
          var existing = operations.putIfAbsent(id, operation);
          if (existing != null && !existing.bytes.equals(operation.bytes)) {
            reply(exchange, 409, Map.of("code", "OPERATION_INPUT_CONFLICT"));
            return;
          }
          if (lostPost) {
            lostPost = false;
            reply(exchange, 503, Map.of("code", "LOST_REPLY"));
            return;
          }
          reply(exchange, 202, Map.of("id", id, "state", operation.state));
          return;
        }
        String[] parts = request.uri.getPath().split("/");
        UUID id = UUID.fromString(parts[4]);
        if (isAnalysis && parts[3].equals("meeting-analyses")) {
          var op =
              operations.values().stream()
                  .filter(o -> revision(o).equals(id))
                  .findFirst()
                  .orElseThrow();
          var selection = op.command.get("selection");
          var evidence = new ArrayList<Map<String, Object>>();
          selection
              .get("inputs")
              .forEach(pin ->
                  evidence.add(
                      Map.of(
                          "versionId",
                          revisionProblem != null && revisionProblem.equals("source")
                              ? UUID.randomUUID().toString()
                              : pin.get("versionId").stringValue())));
          reply(
              exchange,
              200,
              Map.of(
                  "revisionId",
                  id,
                  "meetingId",
                  revisionProblem != null && revisionProblem.equals("meeting")
                      ? UUID.randomUUID().toString()
                      : selection.get("meetingId").stringValue(),
                  "meetingDate",
                  revisionProblem != null && revisionProblem.equals("date")
                      ? "1999-01-01"
                      : selection.get("meetingDate").stringValue(),
                  "developments",
                  List.of(Map.of("evidence", evidence)),
                  "associations",
                  List.of()));
          return;
        }
        var operation = operations.get(id);
        if (operation == null) {
          reply(exchange, 404, Map.of("code", "NOT_FOUND"));
          return;
        }
        if (parts.length == 5) {
          var body = new LinkedHashMap<String, Object>();
          body.put("id", id);
          body.put("state", operation.state);
          body.put("errorCode", operation.error);
          body.put(isAnalysis ? "blockedAttemptId" : "blockedExecutionId", operation.blocked);
          if (isAnalysis && operation.state.equals("SUCCEEDED")) {
            body.put("revisionId", revision(operation));
          }
          reply(exchange, 200, body);
          return;
        }
        List<?> items =
            switch (parts[5]) {
              case "meetings" -> discovery.apply(discoveryReads.getAndIncrement());
              case "originals" -> originals(operation);
              case "preparations" -> preparations(operation);
              default -> throw new IllegalArgumentException("Unexpected fixture route");
            };
        reply(exchange, 200, Map.of("items", items, "page", 0, "size", 100, "hasNext", false));
      } catch (Throwable error) {
        failure.compareAndSet(null, error);
        reply(exchange, 500, Map.of("code", "FIXTURE_FAILURE"));
      } finally {
        exchange.close();
      }
    }

    private final Map<String, String> tokens = new ConcurrentHashMap<>();

    private static void reply(HttpExchange exchange, int status, Object body) throws IOException {
      byte[] bytes = JSON.writeValueAsBytes(body);
      exchange.getResponseHeaders().set("Content-Type", "application/json");
      exchange.sendResponseHeaders(status, bytes.length);
      exchange.getResponseBody().write(bytes);
    }

    private List<Map<String, Object>> meetings(int ignored) {
      var rows = new ArrayList<Map<String, Object>>();
      for (int n = meetingCount; n >= 1; n--) {
        var source = new LinkedHashMap<String, Object>();
        source.put("officialId", "M" + n);
        source.put("title", "Synthetic meeting " + n);
        source.put("localDate", undated ? null : "2026-01-" + String.format("%02d", n));
        source.put("cancellationEvidence", n == 2 ? "Cancelled" : null);
        rows.add(Map.of("id", id(100 + n), "source", source));
      }
      return rows;
    }

    List<Map<String, Object>> originals(Operation operation) {
      UUID meeting = UUID.fromString(operation.command.get("meetingId").stringValue());
      if (withoutOriginals.contains(meeting)) {
        return List.of();
      }
      var result = new ArrayList<Map<String, Object>>();
      for (int n : List.of(2, 1)) {
        long key = meeting.getLeastSignificantBits() * 10 + n;
        result.add(
            Map.of(
                "id",
                id(key),
                "retained",
                Map.of(
                    "documentId",
                    id(key + 10000),
                    "versionId",
                    id(key + 20000),
                    "artifactId",
                    id(key + 30000),
                    "sha256",
                    "a".repeat(64),
                    "byteLength",
                    12)));
      }
      return result;
    }

    private List<Map<String, Object>> preparations(Operation operation) {
      var acquisition =
          operations.get(
              UUID.fromString(operation.command.get("acquisitionOperationId").stringValue()));
      var originals = new ArrayList<>(originals(acquisition));
      Collections.reverse(originals);
      return originals.stream()
          .map(original -> {
            @SuppressWarnings("unchecked")
            var retained = (Map<String, Object>) original.get("retained");
            return Map.of(
                "acquisitionItemId",
                original.get("id"),
                "documentId",
                badPinJoin ? UUID.randomUUID() : retained.get("documentId"),
                "versionId",
                retained.get("versionId"),
                "preparationId",
                id(((UUID) original.get("id")).getLeastSignificantBits() + 40000),
                "profileFingerprint",
                "b".repeat(64));
          })
          .toList();
    }

    Operation latest(StepKind kind) {
      return operations.values().stream().filter(o -> o.kind == kind).findFirst().orElseThrow();
    }

    static UUID revision(Operation operation) {
      return UUID.nameUUIDFromBytes((operation.id + "/revision").getBytes(StandardCharsets.UTF_8));
    }
  }

  record Request(String method, URI uri, String body) {}
}
