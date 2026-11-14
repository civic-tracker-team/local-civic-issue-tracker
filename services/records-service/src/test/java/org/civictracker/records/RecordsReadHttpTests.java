package org.civictracker.records;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;
import org.civictracker.records.api.RecordViews;
import org.civictracker.records.application.RecordsFoundation;
import org.civictracker.records.domain.SourceInput;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.core.env.Environment;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import tools.jackson.databind.ObjectMapper;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Timeout(40)
class RecordsReadHttpTests {
  private static final String PASSWORD = UUID.randomUUID().toString();
  private static final String HASH = "{bcrypt}" + new BCryptPasswordEncoder().encode(PASSWORD);
  private static final byte[] ORIGINAL = "Original café 𐐀".getBytes(StandardCharsets.UTF_8);
  @LocalServerPort int port;
  @Autowired RecordsFoundation records;
  @Autowired ObjectMapper json;
  @Autowired Environment environment;
  private HttpClient client;
  private RecordsFoundation.Retained first;
  private RecordsFoundation.Retained latest;

  @DynamicPropertySource
  static void credentials(DynamicPropertyRegistry registry) {
    registry.add("records.security.callers.consumer-one", () -> HASH);
    registry.add("records.security.callers.consumer-two", () -> HASH);
  }

  @BeforeEach
  void seed() throws Exception {
    client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
    String portal = "read-fixture-" + UUID.randomUUID();
    first = records.retain(input(portal, 0), new ByteArrayInputStream(ORIGINAL));
    byte[] updated = new byte[100001];
    new java.util.Random(8).nextBytes(updated);
    latest = records.retain(input(portal, 1), new ByteArrayInputStream(updated));
  }

  @AfterEach
  void closeClient() {
    client.close();
  }

  @Test
  void followsDocumentToHistoricalVersionAndExactOriginalBytes() throws Exception {
    var documentResponse = get("/internal/v1/documents/" + first.documentId());
    assertEquals(200, documentResponse.statusCode());
    var contract = json.readTree(documentResponse.body());
    assertEquals(first.documentId().toString(), contract.get("id").asString());
    assertTrue(contract.has("sourceIds") && contract.has("contexts")
        && contract.has("versionIds") && contract.has("currentVersionId"));
    var document = json.readValue(documentResponse.body(), RecordViews.Document.class);
    assertEquals(latest.versionId(), document.currentVersionId());
    assertEquals(2, document.versionIds().size());
    assertTrue(document.versionIds().contains(first.versionId()));
    assertEquals("attachment-history", document.sourceIds().getFirst().family());
    assertEquals("official-meeting", document.contexts().getFirst().officialMeetingId());
    var versionResponse = get("/internal/v1/document-versions/" + first.versionId());
    var version = json.readValue(versionResponse.body(), RecordViews.Version.class);
    assertEquals(first.documentId(), version.documentId());
    assertEquals(first.sha256(), version.sha256());
    assertEquals(List.of("https://example.invalid/original?source=1"), version.sourceUrls());
    assertEquals(Instant.parse("2026-11-08T12:00:00Z"), version.firstObservedAt());
    var bytes = get("/internal/v1/artifacts/" + version.artifactId() + "/bytes");
    assertEquals(200, bytes.statusCode());
    assertArrayEquals(ORIGINAL, bytes.body());
    assertEquals("application/octet-stream",
        bytes.headers().firstValue("Content-Type").orElseThrow());
    assertEquals(String.valueOf(ORIGINAL.length),
        bytes.headers().firstValue("Content-Length").orElseThrow());
    assertTrue(bytes.headers().firstValue("Content-Disposition").orElseThrow()
        .contains(version.artifactId() + ".bin"));
    assertFalse(bytes.headers().firstValue("Content-Disposition").orElseThrow().contains("unsafe"));
    assertTrue(bytes.headers().allValues("Set-Cookie").isEmpty());
  }

  @Test
  void currentVersionHasIndependentCorrectBytesAndNoMetadataPayloadLeak() throws Exception {
    var version = json.readValue(get("/internal/v1/document-versions/" + latest.versionId()).body(),
        RecordViews.Version.class);
    var result = get("/internal/v1/artifacts/" + version.artifactId() + "/bytes");
    byte[] expected = new byte[100001];
    new java.util.Random(8).nextBytes(expected);
    assertArrayEquals(expected, result.body());
    assertEquals(List.of("https://example.invalid/original?source=2"), version.sourceUrls());
    for (String route : List.of("/internal/v1/documents/" + first.documentId(),
        "/internal/v1/document-versions/" + first.versionId())) {
      String body = new String(get(route).body(), StandardCharsets.UTF_8);
      for (String forbidden : List.of("storageKey", "originals/", "revision", "preparedText")) {
        assertFalse(body.contains(forbidden), body);
      }
    }
  }

  @Test
  void rejectsMissingWrongAndUnconfiguredCallerCredentials() throws Exception {
    String path = "/internal/v1/documents/" + first.documentId();
    for (String authorization : List.of("", basic("consumer-one", "wrong"),
        basic("unknown", PASSWORD))) {
      var response = request(path, authorization, "GET");
      problem(response, 401, "AUTHENTICATION_REQUIRED");
    }
  }

  @Test
  void distinctConfiguredCallersCanReadButCannotWriteOrAccessPublicViews() throws Exception {
    String path = "/internal/v1/documents/" + first.documentId();
    assertEquals(200, request(path, basic("consumer-two", PASSWORD), "GET").statusCode());
    for (String method : List.of("POST", "PUT", "DELETE", "PATCH")) {
      problem(request(path, basic("consumer-one", PASSWORD), method), 403, "ACCESS_DENIED");
    }
    problem(get("/v1/documents/" + first.documentId()), 403, "ACCESS_DENIED");
  }

  @Test
  void malformedMissingAndUnimplementedResourcesAreDistinctFromSuccess() throws Exception {
    problem(get("/internal/v1/documents/not-a-uuid"), 400, "INVALID_REQUEST");
    for (String route : List.of("documents/", "document-versions/", "artifacts/")) {
      String path = "/internal/v1/" + route + UUID.randomUUID();
      if (route.equals("artifacts/")) {
        path += "/bytes";
      }
      problem(get(path), 404, "RECORD_NOT_FOUND");
    }
    problem(get("/internal/v1/document-versions/" + first.versionId() + "/content"),
        404, "PREPARED_CONTENT_NOT_FOUND");
    problem(get("/internal/v1/document-versions/" + first.versionId() + "/content/unimplemented"),
        403, "ACCESS_DENIED");
  }

  @Test
  void missingAndTruncatedFilesReturnSafeUnavailableErrorsBeforeHeaders() throws Exception {
    var version = records.inspectVersion(first.versionId());
    Path root = Path.of(environment.getRequiredProperty("records.storage-root"))
        .toAbsolutePath().normalize();
    Path file = root.resolve(version.storageKey()).normalize();
    assertTrue(file.startsWith(root));
    try {
      Files.delete(file);
      problem(get("/internal/v1/artifacts/" + version.artifactId() + "/bytes"),
          503, "RECORDS_UNAVAILABLE");
      Files.write(file, new byte[] {1});
      problem(get("/internal/v1/artifacts/" + version.artifactId() + "/bytes"),
          503, "RECORDS_UNAVAILABLE");
    } finally {
      Files.write(file, ORIGINAL);
    }
  }

  @Test
  void multipleHttpConsumersReadExactImmutableVersionWithoutSessions() throws Exception {
    String path = "/internal/v1/document-versions/" + first.versionId();
    try (var executor = Executors.newFixedThreadPool(4)) {
      List<Callable<HttpResponse<byte[]>>> tasks = java.util.stream.IntStream.range(0, 8)
          .mapToObj(i -> (Callable<HttpResponse<byte[]>>) () -> get(path)).toList();
      for (var future : executor.invokeAll(tasks)) {
        var result = future.get();
        assertEquals(200, result.statusCode());
        assertEquals(first.versionId(),
            json.readValue(result.body(), RecordViews.Version.class).id());
        assertTrue(result.headers().allValues("Set-Cookie").isEmpty());
      }
    }
  }

  private void problem(HttpResponse<byte[]> response, int status, String code) {
    assertEquals(status, response.statusCode(),
        new String(response.body(), StandardCharsets.UTF_8));
    assertTrue(response.headers().firstValue("Content-Type").orElse("")
        .startsWith("application/problem+json"));
    var body = json.readTree(response.body());
    assertEquals(status, body.get("status").asInt());
    assertEquals(code, body.get("code").asString());
    UUID.fromString(body.get("correlationId").asString());
    String text = body.toString();
    for (String forbidden : List.of("storageKey", "originals/", PASSWORD, "Exception", "unsafe")) {
      assertFalse(text.contains(forbidden), text);
    }
  }

  private HttpResponse<byte[]> get(String path) throws Exception {
    return request(path, basic("consumer-one", PASSWORD), "GET");
  }

  private HttpResponse<byte[]> request(String path, String authorization, String method)
      throws Exception {
    var request = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + path))
        .timeout(Duration.ofSeconds(10)).method(method, HttpRequest.BodyPublishers.noBody());
    if (!authorization.isEmpty()) {
      request.header("Authorization", authorization);
    }
    return client.send(request.build(), HttpResponse.BodyHandlers.ofByteArray());
  }

  private static String basic(String name, String password) {
    return "Basic " + Base64.getEncoder()
        .encodeToString((name + ":" + password).getBytes(StandardCharsets.UTF_8));
  }

  private SourceInput input(String portal, int second) {
    return new SourceInput(portal, "official-meeting", "Council", "2026-11-01", "official-item",
        "Item 1", null, "PDF", "en", "attachment-history", "official-document",
        URI.create("https://example.invalid/original?source=" + (second + 1)),
        "../unsafe\r\nname.pdf",
        Instant.parse("2026-11-08T12:00:00Z").plusSeconds(second), true, null);
  }
}
