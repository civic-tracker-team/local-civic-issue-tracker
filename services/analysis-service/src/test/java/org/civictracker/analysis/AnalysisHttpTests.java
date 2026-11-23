package org.civictracker.analysis;

import static org.junit.jupiter.api.Assertions.*;

import java.net.CookieManager;
import java.net.CookiePolicy;
import java.net.URI;
import java.net.http.*;
import java.time.Duration;
import java.util.Base64;
import java.util.List;
import java.util.UUID;
import org.civictracker.analysis.api.AnalysisCommands.*;
import org.civictracker.analysis.application.*;
import org.civictracker.analysis.domain.*;
import org.civictracker.analysis.evaluation.EvaluationJson;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import tools.jackson.databind.JsonNode;

@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = {
      "analysis.records.user=reader",
      "analysis.records.password=secret",
      "analysis.records.timeout-seconds=2",
      "analysis.model.output-tokens=100",
      "analysis.model.temperature=0.2",
      "analysis.model.top-p=0.95",
      "analysis.model.top-k=40",
      "analysis.model.min-p=0.05",
      "analysis.model.presence-penalty=0"
    })
@Import(AnalysisOperationsTests.Config.class)
@Timeout(40)
class AnalysisHttpTests {
  static final OperationFixture RECORDS = new OperationFixture();
  static final String PASSWORD = UUID.randomUUID().toString();
  static final String HASH = "{bcrypt}" + new BCryptPasswordEncoder(4).encode(PASSWORD);

  @DynamicPropertySource
  static void properties(DynamicPropertyRegistry registry) {
    registry.add("analysis.records.base-url", RECORDS::base);
    registry.add("analysis.security.callers.reader", () -> HASH);
    registry.add("analysis.security.processors.processor", () -> HASH);
    registry.add("analysis.security.maintainers.maintainer", () -> HASH);
  }

  @AfterAll
  static void closeRecords() {
    RECORDS.close();
  }

  @LocalServerPort int port;
  @Autowired AnalysisOperations operations;
  @Autowired AnalysisOperationWorker worker;
  @Autowired FindingsStore findings;
  @Autowired OperationFixture.FakeModel model;
  HttpClient client;

  @BeforeEach
  void open() {
    model.mode = "SUCCESS";
    client =
        HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .cookieHandler(new CookieManager(null, CookiePolicy.ACCEPT_ALL))
            .build();
  }

  @AfterEach
  void close() {
    while (worker.processNext()) {}
    client.close();
  }

  @Test
  void commandToSavedFindingsAndIssueHistoryRoundTripHasOnlyDtoFields() throws Exception {
    var c = RECORDS.command();
    var token = csrf();
    var accepted = send("POST", "/analysis-operations", "processor", c, token);
    assertEquals(202, accepted.statusCode());
    assertEquals(
        "/internal/v1/analysis-operations/" + c.id(),
        accepted.headers().firstValue("location").orElseThrow());
    assertEquals(202, send("POST", "/analysis-operations", "processor", c, token).statusCode());
    assertTrue(worker.processNext());
    var status = read(send("GET", "/analysis-operations/" + c.id(), "reader", null, null));
    var id = UUID.fromString(status.path("revisionId").asText());
    var response = send("GET", "/meeting-analyses/" + id, "reader", null, null);
    assertEquals(200, response.statusCode());
    assertEquals("no-store", response.headers().firstValue("cache-control").orElseThrow());
    var saved = EvaluationJson.MAPPER.readValue(response.body(), SavedFindings.class);
    assertEquals(findings.read(id), saved);
    assertEquals(
        org.civictracker.analysis.domain.FindingKind.PROPOSAL,
        saved.developments().getFirst().kind());
    var history =
        send("GET", "/issues/" + saved.issues().getFirst().issueId(), "reader", null, null);
    assertEquals(200, history.statusCode());
    assertEquals(
        findings.issueHistory(saved.issues().getFirst().issueId()),
        EvaluationJson.MAPPER.readValue(history.body(), IssueHistory.class));
    for (String field :
        List.of(
            "inputJson",
            "commandSha256",
            "receiptSha256",
            "artifactKey",
            "localLabel",
            "isNew",
            "password",
            "workerId")) assertFalse(response.body().contains("\"" + field + "\""), field);
  }

  @Test
  void readAndWritePermissionsAndCsrfAreEnforcedOnExactRoutes() throws Exception {
    var c = RECORDS.command();
    operations.accept(c);
    for (String user : List.of("reader", "processor", "maintainer"))
      assertEquals(
          200, send("GET", "/analysis-operations/" + c.id(), user, null, null).statusCode());
    assertEquals(401, send("GET", "/analysis-operations/" + c.id(), null, null, null).statusCode());
    assertEquals(
        401, send("GET", "/analysis-operations/" + c.id(), "unknown", null, null).statusCode());
    assertEquals(403, send("GET", "/csrf", "reader", null, null).statusCode());
    assertEquals(
        403,
        send("POST", "/analysis-operations", "processor", RECORDS.command(), null).statusCode());
    var token = csrf();
    assertEquals(
        403, send("POST", "/analysis-operations", "reader", RECORDS.command(), token).statusCode());
    assertEquals(
        403,
        send(
                "POST",
                "/analysis-operations/" + c.id() + "/resume",
                "processor",
                new Resume(null, null, true, true),
                token)
            .statusCode());
    for (String path :
        List.of(
            "/unimplemented",
            "/meeting-analyses/" + UUID.randomUUID() + "/extra",
            "/issues/" + UUID.randomUUID() + "/extra"))
      assertEquals(403, send("GET", path, "reader", null, null).statusCode());
    for (String method : List.of("PUT", "DELETE", "POST"))
      assertEquals(
          403,
          send(method, "/meeting-analyses/" + UUID.randomUUID(), "maintainer", null, token)
              .statusCode());
    assertEquals(
        202,
        send("POST", "/analysis-operations", "maintainer", RECORDS.command(), token).statusCode());
  }

  @Test
  void maintainerResumeRequiresMatchingAttemptAndStoppedWorker() throws Exception {
    var c = RECORDS.command();
    operations.accept(c);
    var claim = operations.claim();
    operations.fail(c.id(), claim.attemptId, "RUNTIME_FAILED");
    var token = csrf();
    assertEquals(
        409,
        send(
                "POST",
                "/analysis-operations/" + c.id() + "/resume",
                "maintainer",
                new Resume(UUID.randomUUID(), claim.workerId, true, true),
                token)
            .statusCode());
    assertEquals(
        202,
        send(
                "POST",
                "/analysis-operations/" + c.id() + "/resume",
                "maintainer",
                new Resume(claim.attemptId, claim.workerId, true, false),
                token)
            .statusCode());
  }

  @Test
  void badInputsMissingIdsAndConflictsReturnSanitizedProblems() throws Exception {
    var token = csrf();
    var c = RECORDS.command();
    operations.accept(c);
    var changed = new Command(c.id(), c.selection(), List.of(UUID.randomUUID()));
    assertEquals(
        409, send("POST", "/analysis-operations", "processor", changed, token).statusCode());
    var bad = RECORDS.command();
    var invalid =
        send(
            "POST",
            "/analysis-operations",
            "processor",
            new Command(bad.id(), bad.selection(), List.of(UUID.randomUUID())),
            token);
    assertEquals(400, invalid.statusCode());
    assertThrows(OperationProblem.class, () -> operations.status(bad.id()));
    for (String path : List.of("/analysis-operations/", "/meeting-analyses/", "/issues/")) {
      var missing = send("GET", path + UUID.randomUUID(), "reader", null, null);
      assertEquals(404, missing.statusCode());
      safe(missing);
      var malformed = send("GET", path + "private-machine-path", "reader", null, null);
      assertEquals(400, malformed.statusCode());
      safe(malformed);
    }
    var malformed =
        send(
            "POST",
            "/analysis-operations",
            "processor",
            java.util.Map.of("id", "private-machine-path"),
            token);
    assertEquals(400, malformed.statusCode());
    safe(malformed);
    safe(invalid);
  }

  private JsonNode csrf() throws Exception {
    var response = send("GET", "/csrf", "processor", null, null);
    assertEquals(200, response.statusCode());
    assertFalse(response.headers().allValues("set-cookie").isEmpty());
    return read(response);
  }

  private HttpResponse<String> send(
      String method, String path, String user, Object body, JsonNode token) throws Exception {
    // Authentication clears the preceding CSRF cookie, as in the Records client tests.
    if (token != null) token = csrf();
    var request =
        HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + "/internal/v1" + path))
            .timeout(Duration.ofSeconds(10));
    if (user != null)
      request.header(
          "Authorization",
          "Basic "
              + Base64.getEncoder()
                  .encodeToString(
                      (user + ":" + PASSWORD).getBytes(java.nio.charset.StandardCharsets.UTF_8)));
    if (token != null)
      request.header(token.path("headerName").asText(), token.path("token").asText());
    request.header("Content-Type", "application/json");
    request.method(
        method,
        body == null
            ? HttpRequest.BodyPublishers.noBody()
            : HttpRequest.BodyPublishers.ofString(EvaluationJson.MAPPER.writeValueAsString(body)));
    return client.send(request.build(), HttpResponse.BodyHandlers.ofString());
  }

  private JsonNode read(HttpResponse<String> response) {
    return EvaluationJson.MAPPER.readTree(response.body());
  }

  private void safe(HttpResponse<String> response) {
    assertTrue(
        response
            .headers()
            .firstValue("content-type")
            .orElseThrow()
            .startsWith("application/problem+json"));
    var problem = read(response);
    assertTrue(problem.has("code"));
    assertTrue(problem.has("correlationId"));
    for (String privateText :
        List.of("private-machine-path", "java.", "SQLException", "inputJson", PASSWORD))
      assertFalse(response.body().contains(privateText));
  }
}
