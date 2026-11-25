package org.civictracker.coordinator;

import static org.junit.jupiter.api.Assertions.*;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDate;
import java.util.Base64;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;
import org.civictracker.coordinator.owners.AnalysisClient;
import org.civictracker.coordinator.owners.DiscoveredMeeting;
import org.civictracker.coordinator.owners.Original;
import org.civictracker.coordinator.owners.OwnerRejected;
import org.civictracker.coordinator.owners.OwnerState;
import org.civictracker.coordinator.owners.OwnerUnreachable;
import org.civictracker.coordinator.owners.Prepared;
import org.civictracker.coordinator.owners.RecordsClient;
import org.civictracker.coordinator.run.AnalysisSelection;
import org.civictracker.coordinator.run.Pin;
import org.civictracker.coordinator.run.StartRun;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

@Timeout(15)
class OwnerClientTests {
  static final JsonMapper JSON = JsonMapper.builder().build();
  HttpServer server;
  URI base;
  RecordsClient records;
  AnalysisClient analysis;
  final List<Request> calls = new CopyOnWriteArrayList<>();
  final AtomicInteger csrf = new AtomicInteger();
  final Map<String, String> accepted = new HashMap<>();
  Function<Request, Reply> respond = request -> new Reply(404, Map.of("code", "NOT_FOUND"));
  UUID operation = UUID.randomUUID(), meeting = UUID.randomUUID();

  @BeforeEach
  void start() throws IOException {
    server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    server.createContext("/", this::serve);
    server.start();
    base = URI.create("http://127.0.0.1:" + server.getAddress().getPort());
    records = new RecordsClient(base, "caller", "synthetic-password", Duration.ofSeconds(3));
    analysis = new AnalysisClient(base, "caller", "synthetic-password", Duration.ofSeconds(3));
  }

  @AfterEach
  void stop() {
    if (server != null) {
      server.stop(0);
    }
  }

  void serve(HttpExchange exchange) throws IOException {
    try {
      var request =
          new Request(
              exchange.getRequestMethod(),
              exchange.getRequestURI(),
              new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8),
              exchange.getRequestHeaders());
      calls.add(request);
      String expected =
          "Basic "
              + Base64.getEncoder()
                  .encodeToString("caller:synthetic-password".getBytes(StandardCharsets.UTF_8));
      Reply reply;
      if (!expected.equals(request.headers.getFirst("Authorization"))) {
        reply = new Reply(401, Map.of("code", "UNAUTHORIZED"));
      } else if (request.uri.getPath().equals("/internal/v1/csrf")) {
        int n = csrf.incrementAndGet();
        exchange
            .getResponseHeaders()
            .add("Set-Cookie", "JSESSIONID=cookie" + n + "; Path=/; HttpOnly; SameSite=Lax");
        reply = new Reply(200, Map.of("headerName", "X-CSRF-TOKEN", "token", "token" + n));
      } else if (request.method.equals("POST")
          && !("token" + csrf.get()).equals(request.headers.getFirst("X-CSRF-TOKEN"))) {
        reply = new Reply(403, Map.of("code", "CSRF_FAILED"));
      } else if (request.method.equals("POST")
          && !("JSESSIONID=cookie" + csrf.get()).equals(request.headers.getFirst("Cookie"))) {
        reply = new Reply(403, Map.of("code", "COOKIE_MISSING"));
      } else {
        reply = respond.apply(request);
      }
      byte[] bytes = JSON.writeValueAsBytes(reply.body);
      exchange.getResponseHeaders().set("Content-Type", "application/json");
      exchange.sendResponseHeaders(reply.status, bytes.length);
      exchange.getResponseBody().write(bytes);
    } finally {
      exchange.close();
    }
  }

  // First contract case, grounded in the records cancellation test and acquisition guide.
  @Test
  void cancelledMeetingIsDiscoveredAndAcquisitionSucceedsWithoutOriginals() throws Exception {
    respond =
        request -> {
          String path = request.uri.getPath();
          if (request.method.equals("POST")) {
            return deduplicate(request);
          }
          if (path.endsWith("/meetings")) {
            return page(request, List.of(discovered(meeting, "Cancelled")), false);
          }
          if (path.endsWith("/originals")) {
            return page(request, List.of(), false);
          }
          return new Reply(200, Map.of("id", operation, "state", "SUCCEEDED"));
        };
    records.discover(UUID.randomUUID(), command("synthetic"), null);
    var found = records.meetings(operation);
    assertEquals(1, found.size());
    assertEquals(meeting, found.getFirst().id());
    assertEquals("Cancelled", found.getFirst().cancellationEvidence());
    records.acquire(operation, UUID.randomUUID(), meeting, UUID.randomUUID(), null);
    assertEquals(OwnerState.SUCCEEDED, records.operation(operation).orElseThrow().state());
    assertTrue(records.originals(operation).isEmpty());
    assertEquals(2, csrf.get());
  }

  @Test
  void sameCommandResendsIdenticallyAndDifferentCommandIsRejected() throws Exception {
    respond = this::deduplicate;
    var command = command("synthetic");
    records.discover(operation, command, null);
    records.discover(operation, command, null);
    var rejected =
        assertThrows(
            OwnerRejected.class, () -> records.discover(operation, command("different"), null));
    assertEquals(409, rejected.status);
    assertEquals("OPERATION_INPUT_CONFLICT", rejected.code);
    var posts = calls.stream().filter(c -> c.method.equals("POST")).toList();
    assertEquals(posts.get(0).body, posts.get(1).body);
    assertEquals(3, csrf.get());
    assertNotEquals(
        posts.get(0).headers.getFirst("Cookie"), posts.get(1).headers.getFirst("Cookie"));
  }

  @Test
  void preparationAndAnalysisUseTheOwnersExactCommandShapesAndFreshCsrf() throws Exception {
    respond = this::deduplicate;
    UUID acquired = UUID.randomUUID(), run = UUID.randomUUID(), replaced = UUID.randomUUID();
    records.prepare(operation, acquired, run, "profile", replaced);
    var prep = JSON.readTree(lastPost().body);
    assertEquals(acquired.toString(), prep.get("acquisitionOperationId").stringValue());
    assertEquals(replaced.toString(), prep.get("replacesOperationId").stringValue());
    assertFalse(prep.get("freshRevision").booleanValue());
    var pin =
        new Pin(
            UUID.randomUUID(),
            UUID.randomUUID(),
            UUID.randomUUID(),
            UUID.randomUUID(),
            "a".repeat(64),
            8,
            UUID.randomUUID(),
            "b".repeat(64));
    var selection =
        new AnalysisSelection(
            UUID.randomUUID(),
            acquired,
            operation,
            meeting,
            "fixture",
            LocalDate.of(2026, 1, 15),
            "Synthetic",
            List.of(pin));
    UUID prior = UUID.randomUUID(), id = UUID.randomUUID();
    analysis.analyze(id, selection, List.of(prior));
    String original = lastPost().body;
    analysis.analyze(id, selection, List.of(prior));
    assertEquals(original, lastPost().body);
    var sent = JSON.readTree(original);
    assertEquals(id.toString(), sent.get("id").stringValue());
    assertFalse(sent.has("operationId"));
    assertEquals("2026-01-15", sent.get("selection").get("meetingDate").stringValue());
    assertEquals(
        pin.versionId().toString(),
        sent.get("selection").get("inputs").get(0).get("versionId").stringValue());
    assertEquals(prior.toString(), sent.get("priorRevisionIds").get(0).stringValue());
    assertEquals(3, csrf.get());
    var conflict =
        assertThrows(OwnerRejected.class, () -> analysis.analyze(id, selection, List.of()));
    assertEquals(409, conflict.status);
  }

  @Test
  void everyPageIsReadAndRetainedVersionsAndPreparationReferencesArePreserved() throws Exception {
    UUID doc = UUID.randomUUID(),
        version = UUID.randomUUID(),
        artifact = UUID.randomUUID(),
        preparation = UUID.randomUUID();
    List<UUID> ids = List.of(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID());
    respond =
        request -> {
          boolean first = request.uri.getQuery().contains("page=0");
          var pageIds = first ? ids.subList(0, 2) : ids.subList(2, 3);
          List<?> rows;
          if (request.uri.getPath().endsWith("/meetings")) {
            rows = pageIds.stream().map(id -> discovered(id, "Cancelled")).toList();
          } else if (request.uri.getPath().endsWith("/originals")) {
            rows =
                pageIds.stream()
                    .map(id ->
                        Map.of(
                            "id",
                            id,
                            "source",
                            Map.of("title", "Ignored wire field"),
                            "retained",
                            Map.of(
                                "documentId",
                                doc,
                                "versionId",
                                version,
                                "artifactId",
                                artifact,
                                "currentVersionId",
                                UUID.randomUUID(),
                                "sha256",
                                "a".repeat(64),
                                "byteLength",
                                9)))
                    .toList();
          } else {
            rows =
                pageIds.stream()
                    .map(id ->
                        Map.of(
                            "acquisitionItemId",
                            id,
                            "documentId",
                            doc,
                            "versionId",
                            version,
                            "preparationId",
                            preparation,
                            "profileFingerprint",
                            "b".repeat(64)))
                    .toList();
          }
          return page(request, rows, first);
        };
    assertEquals(ids, records.meetings(operation).stream().map(DiscoveredMeeting::id).toList());
    var originals = records.originals(operation);
    assertEquals(ids, originals.stream().map(Original::acquisitionItemId).toList());
    assertTrue(
        originals.stream()
            .allMatch(o ->
                o.versionId().equals(version)
                    && o.artifactId().equals(artifact)
                    && o.byteLength() == 9));
    var prepared = records.preparations(operation);
    assertEquals(ids, prepared.stream().map(Prepared::acquisitionItemId).toList());
    assertTrue(prepared.stream().allMatch(p -> p.preparationId().equals(preparation)));
    assertEquals(6, calls.size());
  }

  @Test
  void statusReadsDistinguishAbsentRejectedAndUnreachable() throws Exception {
    assertTrue(records.operation(operation).isEmpty());
    assertTrue(analysis.operation(operation).isEmpty());
    assertTrue(analysis.revision(operation).isEmpty());
    assertEquals(404, assertThrows(OwnerRejected.class, () -> records.originals(operation)).status);
    for (int status : List.of(400, 401, 403, 409, 429)) {
      respond = r -> new Reply(status, Map.of("code", "OWNER_CODE"));
      var rejected = assertThrows(OwnerRejected.class, () -> records.operation(operation));
      assertEquals(status, rejected.status);
      assertEquals("OWNER_CODE", rejected.code);
      assertEquals(
          status, assertThrows(OwnerRejected.class, () -> analysis.operation(operation)).status);
    }
    respond = r -> new Reply(503, Map.of("code", "UNAVAILABLE"));
    assertThrows(OwnerUnreachable.class, () -> records.operation(operation));
    assertThrows(OwnerUnreachable.class, () -> analysis.operation(operation));
    server.stop(0);
    assertThrows(OwnerUnreachable.class, () -> records.operation(operation));
  }

  @Test
  void blockedStatusAndRevisionEvidenceIncludeBothEvidenceCollections() throws Exception {
    UUID blocked = UUID.randomUUID(),
        revision = UUID.randomUUID(),
        v1 = UUID.randomUUID(),
        v2 = UUID.randomUUID();
    respond =
        request -> {
          if (request.uri.getPath().contains("meeting-analyses")) {
            return new Reply(
                200,
                Map.of(
                    "revisionId",
                    revision,
                    "meetingId",
                    meeting,
                    "meetingDate",
                    "2026-01-15",
                    "developments",
                    List.of(Map.of("evidence", List.of(Map.of("versionId", v1)))),
                    "associations",
                    List.of(
                        Map.of(
                            "evidence",
                            List.of(Map.of("versionId", v2), Map.of("versionId", v1))))));
          }
          if (request.uri.getPath().contains("analysis-operations")) {
            return new Reply(
                200, Map.of("id", operation, "state", "RUNNING", "blockedAttemptId", blocked));
          }
          return new Reply(
              200, Map.of("id", operation, "state", "RUNNING", "blockedExecutionId", blocked));
        };
    var recordStatus = records.operation(operation).orElseThrow();
    assertEquals(blocked, recordStatus.blockedId());
    assertEquals(base.resolve("/internal/v1/operations/" + operation), recordStatus.statusUrl());
    assertEquals(blocked, analysis.operation(operation).orElseThrow().blockedId());
    var header = analysis.revision(revision).orElseThrow();
    assertEquals(meeting, header.meetingId());
    assertEquals(LocalDate.of(2026, 1, 15), header.meetingDate());
    assertEquals(Set.of(v1, v2), header.evidenceVersionIds());
  }

  @Test
  void malformedSuccessCannotBecomeAbsenceOrAnEmptyResult() {
    respond = r -> new Reply(200, Map.of("id", UUID.randomUUID(), "state", "SUCCEEDED"));
    assertThrows(OwnerUnreachable.class, () -> records.operation(operation));
    respond = r -> new Reply(200, Map.of("items", List.of(), "page", 0));
    assertThrows(OwnerUnreachable.class, () -> records.meetings(operation));
    respond = r -> page(r, List.of(), true);
    assertThrows(OwnerUnreachable.class, () -> records.meetings(operation));
  }

  @Test
  void timeoutIsUnreachable() throws Exception {
    var shortClient =
        new RecordsClient(base, "caller", "synthetic-password", Duration.ofMillis(100));
    respond =
        r -> {
          try {
            Thread.sleep(500);
          } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
          }
          return new Reply(200, Map.of("id", operation, "state", "RUNNING"));
        };
    assertThrows(OwnerUnreachable.class, () -> shortClient.operation(operation));
  }

  @Test
  void connectionRequiresHttpsOrLoopbackAndExplicitCredentials() {
    assertThrows(
        IllegalArgumentException.class,
        () ->
            new RecordsClient(
                URI.create("http://example.invalid"), "caller", "secret", Duration.ofSeconds(1)));
    assertThrows(
        IllegalArgumentException.class,
        () -> new RecordsClient(base, "", "secret", Duration.ofSeconds(1)));
    assertThrows(
        IllegalArgumentException.class,
        () -> new AnalysisClient(base, "caller", "secret", Duration.ZERO));
  }

  @Test
  void storedAnalysisRequestResendsItsExactBytesAfterReload() throws Exception {
    respond = this::deduplicate;
    var pin =
        new Pin(
            UUID.randomUUID(),
            UUID.randomUUID(),
            UUID.randomUUID(),
            UUID.randomUUID(),
            "a".repeat(64),
            8,
            UUID.randomUUID(),
            "b".repeat(64));
    var selection =
        new AnalysisSelection(
            UUID.randomUUID(),
            UUID.randomUUID(),
            UUID.randomUUID(),
            meeting,
            "fixture",
            LocalDate.of(2026, 1, 15),
            "Réunion civique",
            List.of(pin));
    var frozen = AnalysisClient.freeze(operation, selection, List.of());
    String bytes = " \n" + frozen.json() + "\n";
    String hash =
        HexFormat.of()
            .formatHex(
                java.security.MessageDigest.getInstance("SHA-256")
                    .digest(bytes.getBytes(StandardCharsets.UTF_8)));
    var reloaded = AnalysisClient.readFrozen(bytes, hash);
    analysis.analyze(reloaded);
    assertEquals(bytes, lastPost().body);
    analysis.analyze(reloaded);
    assertEquals(bytes, lastPost().body);
    assertEquals(2, csrf.get());
  }

  StartRun command(String source) {
    return new StartRun(
        UUID.randomUUID(),
        source,
        LocalDate.of(2026, 1, 1),
        LocalDate.of(2026, 1, 31),
        List.of("council"));
  }

  Request lastPost() {
    return calls.stream().filter(r -> r.method.equals("POST")).reduce((a, b) -> b).orElseThrow();
  }

  Reply deduplicate(Request request) {
    JsonNode node = JSON.readTree(request.body);
    String id = (node.has("operationId") ? node.get("operationId") : node.get("id")).stringValue();
    String key = request.uri.getPath() + id;
    String prior = accepted.putIfAbsent(key, request.body);
    return prior == null || prior.equals(request.body)
        ? new Reply(202, Map.of())
        : new Reply(409, Map.of("code", "OPERATION_INPUT_CONFLICT"));
  }

  static Map<String, Object> discovered(UUID id, String cancellation) {
    return Map.of(
        "id",
        id,
        "source",
        Map.of(
            "officialId",
            "fixture",
            "title",
            "Synthetic",
            "localDate",
            "2026-01-15",
            "cancellationEvidence",
            cancellation));
  }

  static Reply page(Request request, List<?> items, boolean next) {
    int page = request.uri.getQuery().contains("page=0") ? 0 : 1;
    return new Reply(200, Map.of("items", items, "page", page, "size", 100, "hasNext", next));
  }

  record Request(String method, URI uri, String body, com.sun.net.httpserver.Headers headers) {}

  record Reply(int status, Object body) {}
}
