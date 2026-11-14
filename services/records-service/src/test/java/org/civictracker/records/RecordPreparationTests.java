package org.civictracker.records;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.sun.net.httpserver.HttpServer;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.net.CookieManager;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.graphics.image.LosslessFactory;
import org.civictracker.records.api.OperationViews;
import org.civictracker.records.application.OperationProblem;
import org.civictracker.records.application.PreparedContentReads;
import org.civictracker.records.application.RecordPreparation;
import org.civictracker.records.application.RecordsFoundation;
import org.civictracker.records.application.RecordsOperationWorker;
import org.civictracker.records.application.RecordsOperations;
import org.civictracker.records.domain.AcquisitionData.Format;
import org.civictracker.records.domain.AcquisitionData.State;
import org.civictracker.records.domain.AcquisitionData.Target;
import org.civictracker.records.domain.PreparationData;
import org.civictracker.records.domain.SourceInput;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import tools.jackson.databind.ObjectMapper;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Timeout(50)
class RecordPreparationTests {
  private static final String PASSWORD = UUID.randomUUID().toString();
  private static final String HASH = "{bcrypt}" + new BCryptPasswordEncoder().encode(PASSWORD);
  private static final String TOKEN = UUID.randomUUID().toString();
  private static final AtomicReference<String> MODE = new AtomicReference<>("normal");
  private static final AtomicInteger CALLS = new AtomicInteger();
  private static final ObjectMapper JSON = new ObjectMapper();
  private static final HttpServer OCR = server();
  @Autowired RecordPreparation preparation;
  @Autowired PreparedContentReads reads;
  @Autowired RecordsOperationWorker worker;
  @Autowired RecordsOperations operations;
  @Autowired RecordsFoundation records;
  @Autowired JdbcTemplate jdbc;
  @Autowired ObjectMapper json;
  @Autowired org.springframework.core.env.Environment environment;
  @LocalServerPort int port;
  private final List<UUID> owned = new ArrayList<>();

  @DynamicPropertySource
  static void properties(DynamicPropertyRegistry registry) {
    registry.add("records.security.callers.reader", () -> HASH);
    registry.add("records.security.processors.processor", () -> HASH);
    registry.add("records.security.maintainers.maintainer", () -> HASH);
    registry.add("records.preparation.profiles.fixture.revision", () -> "test-1");
    registry.add("records.preparation.profiles.fixture.ocr-fingerprint", () -> "fixture-v1");
    registry.add(
        "records.preparation.ocr-base-url", () -> "http://127.0.0.1:" + OCR.getAddress().getPort());
    registry.add("records.preparation.ocr-token", () -> TOKEN);
    registry.add("records.preparation.allow-loopback", () -> true);
    registry.add("records.preparation.ocr-timeout-millis", () -> 400);
  }

  @AfterEach
  void cleanup() {
    MODE.set("normal");
    for (UUID id : owned) {
      jdbc.update(
          "update records.operation_slot set operation_id=null,attempt_id=null,worker_id=null where"
              + " operation_id=?",
          id);
      jdbc.update(
          "update records.record_operation set"
              + " state='FAILED',completed_at=now(),error_code='TEST_CLEANUP' where id=? and state"
              + " in ('ACCEPTED','RUNNING')",
          id);
    }
  }

  @AfterAll
  static void stop() {
    OCR.stop(0);
  }

  private record Fixture(UUID acquisition, RecordsFoundation.Retained retained) {}

  private Fixture fixture(byte[] bytes, Format format) throws Exception {
    String name = UUID.randomUUID().toString();
    var retained =
        records.retain(
            new SourceInput(
                "fixture",
                name,
                "Council",
                "2026-11-01",
                null,
                null,
                null,
                null,
                "en",
                "fixture",
                name,
                URI.create("https://example.org/" + name),
                "fixture." + format.name().toLowerCase(),
                Instant.now(),
                true,
                null),
            new ByteArrayInputStream(bytes));
    UUID acquisition = UUID.randomUUID();
    UUID meeting =
        jdbc.queryForObject(
            "select id from records.source_meeting where portal='fixture' and official_id=?",
            UUID.class,
            name);
    jdbc.update(
        "insert into records.record_operation(id,kind,state,input_json,accepted_at,completed_at) "
            + "values (?,'ACQUISITION','SUCCEEDED',?,now(),now())",
        acquisition,
        json.writeValueAsString(
            new org.civictracker.records.domain.AcquisitionData.Acquisition(
                UUID.randomUUID(), meeting, null)));
    UUID artifact =
        jdbc.queryForObject(
            "select artifact_id from records.original_version where id=?",
            UUID.class,
            retained.versionId());
    UUID observation =
        jdbc.queryForObject(
            "select id from records.source_observation where version_id=? limit 1",
            UUID.class,
            retained.versionId());
    var target =
        new Target(
            "fixture",
            name,
            URI.create("https://example.org/" + name),
            format,
            null,
            null,
            null,
            null,
            "en",
            "Example",
            null);
    jdbc.update(
        "insert into records.acquisition_item"
            + " (id,operation_id,ordinal,target_json,target_key,document_id,version_id,artifact_id,observation_id,current_version_id,sha256,byte_length,current_changed)"
            + " values (?,?,0,?,?,?,?,?,?,?,?,?,true)",
        UUID.randomUUID(),
        acquisition,
        json.writeValueAsString(target),
        name,
        retained.documentId(),
        retained.versionId(),
        artifact,
        observation,
        retained.versionId(),
        retained.sha256(),
        retained.byteLength());
    return new Fixture(acquisition, retained);
  }

  private OperationViews.PreparationCommand command(Fixture fixture, boolean fresh) {
    UUID id = UUID.randomUUID();
    owned.add(id);
    return new OperationViews.PreparationCommand(id, fixture.acquisition(), null, "fixture", fresh);
  }

  private UUID finish(OperationViews.PreparationCommand command) {
    preparation.accept(command);
    assertTrue(worker.processNext());
    assertEquals(
        State.SUCCEEDED,
        operations.status(command.operationId()).state(),
        operations.status(command.operationId()).errorCode());
    return reads.results(command.operationId(), 0, 20).items().getFirst().preparationId();
  }

  private static byte[] pdf(boolean scanned, int pages) throws Exception {
    try (var document = new PDDocument();
        var bytes = new ByteArrayOutputStream()) {
      for (int n = 0; n < pages; n++) {
        var page = new PDPage();
        document.addPage(page);
        try (var stream = new PDPageContentStream(document, page)) {
          if (scanned) {
            stream.drawImage(
                LosslessFactory.createFromImage(document, PreparationExtractionTests.raster()),
                40,
                100,
                300,
                300);
            PreparationExtractionTests.text(stream, "Packet Pg. 114", 40, 20);
          } else {
            PreparationExtractionTests.text(stream, "Council page " + n, 40, 700);
          }
        }
      }
      document.save(bytes);
      return bytes.toByteArray();
    }
  }

  @Test
  void nativePreparationPersistsExactTextAndReusesOnlyTheSameRevision() throws Exception {
    var fixture = fixture(pdf(false, 2), Format.PDF);
    var command = command(fixture, false);
    UUID id = finish(command);
    assertEquals(command.operationId(), preparation.accept(command).id());
    var first = reads.content(fixture.retained().versionId(), id, 0, 1);
    assertTrue(first.hasNext());
    assertEquals(1, first.units().size());
    assertTrue(first.units().getFirst().segments().getFirst().text().contains("Council page 0"));
    assertEquals(id, finish(command(fixture, false)));
    UUID replacement = finish(command(fixture, true));
    assertNotEquals(id, replacement);
    assertEquals(
        "PREPARATION_REFERENCE_REQUIRED",
        assertThrows(
                OperationProblem.class,
                () -> reads.content(fixture.retained().versionId(), null, 0, 1))
            .code());
    assertEquals(first, reads.content(fixture.retained().versionId(), id, 0, 1));
    assertEquals(
        "PREPARATION_REFERENCE_REQUIRED",
        assertThrows(
                OperationProblem.class,
                () -> reads.content(fixture.retained().versionId(), null, 1, 1))
            .code());
  }

  private JdbcTemplate migrations() {
    return new JdbcTemplate(
        new org.springframework.jdbc.datasource.DriverManagerDataSource(
            environment.getRequiredProperty("spring.flyway.url"),
            environment.getRequiredProperty("spring.flyway.user"),
            environment.getRequiredProperty("spring.flyway.password")));
  }

  @Test
  void theDatabaseRefusesToChangeASealedRevision() throws Exception {
    var fixture = fixture(pdf(false, 1), Format.PDF);
    UUID sealed = finish(command(fixture, false));
    var before = reads.content(fixture.retained().versionId(), sealed, 0, 1);
    UUID unit =
        jdbc.queryForObject(
            "select id from records.prepared_unit where preparation_id=?", UUID.class, sealed);
    UUID segment =
        jdbc.queryForObject(
            "select id from records.prepared_segment where unit_id=?", UUID.class, unit);
    for (var template : List.of(jdbc, migrations())) {
      List<Runnable> writes =
          List.of(
              () ->
                  template.update(
                      "update records.prepared_content set profile_json='{}' where id=?", sealed),
              () ->
                  template.update(
                      "update records.prepared_content set sealed_at=null where id=?", sealed),
              () -> template.update("delete from records.prepared_content where id=?", sealed),
              () ->
                  template.update(
                      "update records.prepared_unit set native_text='changed' where id=?", unit),
              () -> template.update("delete from records.prepared_unit where id=?", unit),
              () ->
                  template.update(
                      "insert into records.prepared_unit"
                          + " (id,preparation_id,ordinal,disposition,native_text,notes_json)"
                          + " values (?,?,1,'EMPTY','','[]')",
                      UUID.randomUUID(),
                      sealed),
              () ->
                  template.update(
                      "update records.prepared_segment set text_value='changed' where id=?",
                      segment),
              () -> template.update("delete from records.prepared_segment where id=?", segment),
              () ->
                  template.update(
                      "insert into records.prepared_segment"
                          + " (id,unit_id,ordinal,text_value,method,locator_json,selected)"
                          + " values (?,?,1,'added','PDF_NATIVE','{}',false)",
                      UUID.randomUUID(),
                      unit));
      for (Runnable write : writes) {
        var refused =
            assertThrows(org.springframework.dao.DataAccessException.class, write::run);
        assertTrue(
            String.valueOf(refused.getMostSpecificCause().getMessage()).contains("is sealed"),
            refused.getMostSpecificCause().getMessage());
      }
    }
    assertEquals(
        before.units(), reads.content(fixture.retained().versionId(), sealed, 0, 1).units());
  }

  @Test
  void aChangedPipelineDoesNotReuseAnEarlierSealedRevision() throws Exception {
    var fixture = fixture(pdf(false, 1), Format.PDF);
    UUID legacyId = finish(command(fixture, false));
    var sealed = reads.content(fixture.retained().versionId(), legacyId, 0, 1);
    String stored =
        jdbc.queryForObject(
            "select profile_json from records.prepared_content where id=?", String.class, legacyId);
    var specification = json.readValue(stored, PreparationData.Specification.class);
    var legacy =
        new PreparationData.Specification(
            specification.profile(),
            specification.pdfVersion(),
            specification.htmlVersion(),
            "native-first-1");
    String legacyFingerprint =
        org.civictracker.records.domain.Hashes.sha256(
            json.writeValueAsBytes(legacy));
    // Simulate a revision sealed by the earlier pipeline. The sealed-revision trigger refuses this
    // rewrite, so the fixture suspends it through the table owner for this one statement.
    var migrations = migrations();
    migrations.execute(
        "alter table records.prepared_content disable trigger sealed_content_immutable");
    try {
      jdbc.update(
          "update records.prepared_content set profile_json=?,profile_fingerprint=? where id=?",
          json.writeValueAsString(legacy),
          legacyFingerprint,
          legacyId);
    } finally {
      migrations.execute(
          "alter table records.prepared_content enable trigger sealed_content_immutable");
    }
    UUID current = finish(command(fixture, false));
    assertNotEquals(legacyId, current);
    assertNotEquals(
        legacyFingerprint,
        reads.content(fixture.retained().versionId(), current, 0, 1).profileFingerprint());
    assertEquals(
        sealed.units(), reads.content(fixture.retained().versionId(), legacyId, 0, 1).units());
  }

  @Test
  void htmlSegmentsRetainUnicodeAndItemIdentity() throws Exception {
    var fixture =
        fixture(
            ("<div id='MeetingContents'><div class='meeting-item' data-itemid='42'>"
                    + "Road 😀 recommendation</div><script>bad()</script></div>")
                .getBytes(StandardCharsets.UTF_8),
            Format.HTML);
    UUID id = finish(command(fixture, false));
    var unit = reads.content(fixture.retained().versionId(), id, 0, 1).units().getFirst();
    assertTrue(unit.segments().getFirst().text().contains("😀"));
    assertInstanceOf(PreparationData.HtmlLocator.class, unit.segments().getFirst().locator());
    assertEquals(
        "42", ((PreparationData.HtmlLocator) unit.segments().getFirst().locator()).itemId());
    assertFalse(unit.toString().contains("bad()"));
  }

  @Test
  void equalBytesInDifferentMeetingsDoNotSharePreparedIdentity() throws Exception {
    byte[] original = pdf(false, 1);
    var a = fixture(original, Format.PDF);
    var b = fixture(original, Format.PDF);
    UUID first = finish(command(a, false));
    UUID second = finish(command(b, false));
    assertNotEquals(first, second);
    assertNotEquals(
        reads.content(a.retained().versionId(), first, 0, 1).documentId(),
        reads.content(b.retained().versionId(), second, 0, 1).documentId());
  }

  @Test
  void changedRetainedBytesFailBeforeExtraction() throws Exception {
    var fixture = fixture(pdf(false, 1), Format.PDF);
    var command = command(fixture, false);
    preparation.accept(command);
    var root =
        java.nio.file.Path.of(environment.getRequiredProperty("records.storage-root"))
            .toAbsolutePath()
            .normalize();
    var file = root.resolve(fixture.retained().storageKey()).normalize();
    assertTrue(file.startsWith(root));
    byte[] bytes = java.nio.file.Files.readAllBytes(file);
    bytes[0] ^= 1;
    java.nio.file.Files.write(file, bytes);
    worker.processNext();
    assertEquals(
        "PREPARATION_SOURCE_CHANGED", operations.status(command.operationId()).errorCode());
    assertEquals(
        0,
        jdbc.queryForObject(
            "select count(*) from records.preparation_entry where operation_id=?",
            Integer.class,
            command.operationId()));
  }

  @Test
  void aSuccessfulAcquisitionWithoutOriginalsHasAnExplicitEmptyManifest() throws Exception {
    var fixture = fixture(pdf(false, 1), Format.PDF);
    jdbc.update("delete from records.acquisition_item where operation_id=?", fixture.acquisition());
    var command = command(fixture, false);
    preparation.accept(command);
    worker.processNext();
    assertEquals(State.SUCCEEDED, operations.status(command.operationId()).state());
    assertTrue(operations.status(command.operationId()).notes().contains("NO_SELECTED_ORIGINALS"));
    assertTrue(reads.results(command.operationId(), 0, 20).items().isEmpty());
  }

  @Test
  void scannedContentCallsOcrAndKeepsItsOriginalAndGeometry() throws Exception {
    int before = CALLS.get();
    var fixture = fixture(pdf(true, 1), Format.PDF);
    UUID id = finish(command(fixture, false));
    assertEquals(before + 1, CALLS.get());
    var content = reads.content(fixture.retained().versionId(), id, 0, 20);
    assertEquals(fixture.retained().documentId(), content.documentId());
    var recognized =
        content.units().getFirst().segments().stream()
            .filter(s -> s.method().equals("OCR"))
            .findFirst()
            .orElseThrow();
    var location = (PreparationData.PdfLocator) recognized.locator();
    assertEquals(1, location.page());
    assertEquals(4, location.polygon().size());
    assertNotNull(location.image().imageSha256());
    assertEquals(3., location.image().imageToPage().getFirst());
  }

  @Test
  void malformedAndEmptyOcrAreConfirmedFailuresWithoutPublishingPartialText() throws Exception {
    for (String mode : List.of("bad-hash", "empty", "footer", "terminal-error")) {
      MODE.set(mode);
      var fixture = fixture(pdf(true, 1), Format.PDF);
      var command = command(fixture, false);
      preparation.accept(command);
      worker.processNext();
      assertEquals(State.FAILED, operations.status(command.operationId()).state());
      assertThrows(
          OperationProblem.class, () -> reads.content(fixture.retained().versionId(), null, 0, 20));
      assertNull(
          jdbc.queryForObject(
              "select operation_id from records.operation_slot where id=1", UUID.class));
    }
  }

  @Test
  void timeoutRetainsTheGuardAndRequiresBothRecoveryConfirmations() throws Exception {
    MODE.set("slow");
    var fixture = fixture(pdf(true, 1), Format.PDF);
    var command = command(fixture, false);
    preparation.accept(command);
    worker.processNext();
    var status = operations.status(command.operationId());
    assertEquals(State.RUNNING, status.state());
    assertTrue(status.notes().contains("OCR_EXECUTION_UNRESOLVED"));
    UUID execution =
        jdbc.queryForObject(
            "select blocked_execution_id from records.record_operation where id=?",
            UUID.class,
            status.id());
    assertNotNull(execution);
    assertFalse(worker.processNext());
    assertEquals(
        "OCR_RECONCILIATION_REQUIRED",
        assertThrows(
                OperationProblem.class,
                () ->
                    operations.resume(
                        status.id(),
                        new OperationViews.Resume(status.attemptId(), status.workerId(), true)))
            .code());
    Thread.sleep(900);
    UUID previousInstance = UUID.randomUUID();
    jdbc.update(
        "update records.record_operation set worker_id=? where id=?",
        previousInstance,
        status.id());
    jdbc.update(
        "update records.operation_slot set worker_id=? where operation_id=?",
        previousInstance,
        status.id());
    operations.resume(
        status.id(),
        new OperationViews.Resume(status.attemptId(), previousInstance, true, execution, true));
    MODE.set("normal");
    worker.processNext();
    assertEquals(State.SUCCEEDED, operations.status(status.id()).state());
    assertEquals(
        1,
        jdbc.queryForObject(
            "select count(*) from records.preparation_ocr where operation_id=? and"
                + " state='STOP_CONFIRMED'",
            Integer.class,
            status.id()));
  }

  @Test
  void checkpointFailureKeepsEarlierUnitsAndDoesNotExposeAnUnsealedRevision() throws Exception {
    var fixture = fixture(pdf(false, 2), Format.PDF);
    var command = command(fixture, false);
    preparation.accept(command);
    var migrations = migrations();
    migrations.execute(
        "alter table records.prepared_unit add constraint fixture_second_unit check (ordinal<1)");
    try {
      worker.processNext();
    } finally {
      migrations.execute("alter table records.prepared_unit drop constraint fixture_second_unit");
    }
    var status = operations.status(command.operationId());
    assertEquals(State.FAILED, status.state());
    UUID content =
        jdbc.queryForObject(
            "select preparation_id from records.preparation_entry where operation_id=?",
            UUID.class,
            status.id());
    UUID segment =
        jdbc.queryForObject(
            "select s.id from records.prepared_segment s join records.prepared_unit u on"
                + " u.id=s.unit_id where u.preparation_id=?",
            UUID.class,
            content);
    assertThrows(
        OperationProblem.class,
        () -> reads.content(fixture.retained().versionId(), content, 0, 20));
    operations.resume(
        status.id(), new OperationViews.Resume(status.attemptId(), status.workerId(), true));
    worker.processNext();
    assertEquals(State.SUCCEEDED, operations.status(status.id()).state());
    assertEquals(
        segment,
        reads
            .content(fixture.retained().versionId(), content, 0, 1)
            .units()
            .getFirst()
            .segments()
            .getFirst()
            .id());
    assertEquals(2, reads.content(fixture.retained().versionId(), content, 0, 20).unitCount());
  }

  @Test
  void aKilledJvmCannotBypassOcrConfirmationBeforeTheWorkerParks() throws Exception {
    MODE.set("crash-slow");
    var fixture = fixture(pdf(true, 1), Format.PDF);
    var command = command(fixture, false);
    preparation.accept(command);
    int before = CALLS.get();
    String executable =
        System.getProperty("os.name").toLowerCase().contains("win") ? "java.exe" : "java";
    var javaExecutable = java.nio.file.Path.of(System.getProperty("java.home"), "bin", executable);
    var log =
        java.nio.file.Path.of(environment.getRequiredProperty("records.storage-root"))
            .resolve("preparation-child.log");
    var builder =
        new ProcessBuilder(
                javaExecutable.toString(),
                "-cp",
                System.getProperty("java.class.path"),
                RecordsPreparationCrashProbe.class.getName())
            .redirectErrorStream(true)
            .redirectOutput(log.toFile());
    builder
        .environment()
        .put(
            "SPRING_APPLICATION_JSON",
            json.writeValueAsString(
                java.util.Map.of(
                    "records.preparation.ocr-base-url",
                    "http://127.0.0.1:" + OCR.getAddress().getPort(),
                    "records.preparation.ocr-token",
                    TOKEN,
                    "records.preparation.allow-loopback",
                    true,
                    "records.preparation.ocr-timeout-millis",
                    10000)));
    Process child = builder.start();
    try {
      long deadline = System.nanoTime() + java.util.concurrent.TimeUnit.SECONDS.toNanos(18);
      while (CALLS.get() == before && child.isAlive() && System.nanoTime() < deadline) {
        Thread.sleep(25);
      }
      assertTrue(CALLS.get() > before, "Child never reached OCR; inspect its owned log");
      child.destroyForcibly();
      assertTrue(child.waitFor(10, java.util.concurrent.TimeUnit.SECONDS));
      assertNull(
          jdbc.queryForObject(
              "select blocked_execution_id from records.record_operation where id=?",
              UUID.class,
              command.operationId()));
      var status = operations.status(command.operationId());
      assertEquals(State.RUNNING, status.state());
      assertNotNull(status.blockedExecutionId());
      assertFalse(worker.processNext());
      assertEquals(
          "OCR_RECONCILIATION_REQUIRED",
          assertThrows(
                  OperationProblem.class,
                  () ->
                      operations.resume(
                          status.id(),
                          new OperationViews.Resume(status.attemptId(), status.workerId(), true)))
              .code());
      Thread.sleep(3400);
      MODE.set("normal");
      operations.resume(
          status.id(),
          new OperationViews.Resume(
              status.attemptId(), status.workerId(), true, status.blockedExecutionId(), true));
      worker.processNext();
      assertEquals(State.SUCCEEDED, operations.status(status.id()).state());
    } finally {
      if (child.isAlive()) {
        child.destroyForcibly();
        child.waitFor();
      }
    }
  }

  @Test
  void authenticatedHttpContractDeniesWritesToReadersAndReadsPinnedPreparedContent()
      throws Exception {
    var fixture = fixture(pdf(false, 1), Format.PDF);
    var command = command(fixture, false);
    try (var client = HttpClient.newBuilder().cookieHandler(new CookieManager()).build()) {
      String base = "http://127.0.0.1:" + port + "/internal/v1";
      String auth =
          "Basic "
              + Base64.getEncoder()
                  .encodeToString(("processor:" + PASSWORD).getBytes(StandardCharsets.UTF_8));
      var csrf =
          client.send(
              HttpRequest.newBuilder(URI.create(base + "/csrf"))
                  .header("Authorization", auth)
                  .GET()
                  .build(),
              HttpResponse.BodyHandlers.ofString());
      String token = json.readTree(csrf.body()).path("token").asString();
      var accepted =
          client.send(
              HttpRequest.newBuilder(URI.create(base + "/preparation-operations"))
                  .header("Authorization", auth)
                  .header("X-XSRF-TOKEN", token)
                  .header("Content-Type", "application/json")
                  .POST(HttpRequest.BodyPublishers.ofString(json.writeValueAsString(command)))
                  .build(),
              HttpResponse.BodyHandlers.ofString());
      assertEquals(202, accepted.statusCode(), accepted.body());
      worker.processNext();
      UUID content = reads.results(command.operationId(), 0, 1).items().getFirst().preparationId();
      URI uri =
          URI.create(
              base
                  + "/document-versions/"
                  + fixture.retained().versionId()
                  + "/content?preparationId="
                  + content);
      assertEquals(
          401,
          client
              .send(HttpRequest.newBuilder(uri).GET().build(), HttpResponse.BodyHandlers.ofString())
              .statusCode());
      var response =
          client.send(
              HttpRequest.newBuilder(uri).header("Authorization", auth).GET().build(),
              HttpResponse.BodyHandlers.ofString());
      assertEquals(200, response.statusCode(), response.body());
      assertTrue(response.body().contains("Council page 0"));
      assertFalse(response.body().contains("storageKey"));
      String reader =
          "Basic "
              + Base64.getEncoder()
                  .encodeToString(("reader:" + PASSWORD).getBytes(StandardCharsets.UTF_8));
      assertEquals(
          403,
          client
              .send(
                  HttpRequest.newBuilder(URI.create(base + "/preparation-operations"))
                      .header("Authorization", reader)
                      .header("Content-Type", "application/json")
                      .POST(HttpRequest.BodyPublishers.ofString(json.writeValueAsString(command)))
                      .build(),
                  HttpResponse.BodyHandlers.ofString())
              .statusCode());
    }
  }

  @Test
  void equivalentCommandsReplayAndChangedInputsConflict() throws Exception {
    var fixture = fixture(pdf(false, 1), Format.PDF);
    var command = command(fixture, false);
    preparation.accept(command);
    assertEquals(command.operationId(), preparation.accept(command).id());
    assertEquals(
        "OPERATION_INPUT_CONFLICT",
        assertThrows(
                OperationProblem.class,
                () ->
                    preparation.accept(
                        new OperationViews.PreparationCommand(
                            command.operationId(), fixture.acquisition(), null, "fixture", true)))
            .code());
    worker.processNext();
  }

  private static HttpServer server() {
    try {
      var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
      server.setExecutor(Executors.newCachedThreadPool(Thread.ofPlatform().daemon().factory()));
      server.createContext(
          "/internal/v1/ocr",
          exchange -> {
            try {
              CALLS.incrementAndGet();
              assertEquals(
                  "Bearer " + TOKEN, exchange.getRequestHeaders().getFirst("Authorization"));
              String request =
                  new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.ISO_8859_1);
              int start = request.indexOf("\r\n\r\n") + 4;
              var metadata =
                  JSON.readTree(request.substring(start, request.indexOf("\r\n--", start)));
              String id = metadata.path("executionId").asString();
              int width = metadata.path("width").asInt();
              int height = metadata.path("height").asInt();
              String mode = MODE.get();
              if (mode.equals("slow") || mode.equals("crash-slow")) {
                Thread.sleep(mode.equals("crash-slow") ? 3000 : 800);
              }
              var result = new java.util.LinkedHashMap<String, Object>();
              result.put("executionId", id);
              result.put(
                  "imageSha256",
                  mode.equals("bad-hash") ? "bad" : metadata.path("imageSha256").asString());
              result.put("fingerprint", "fixture-v1");
              result.put("provenance", java.util.Map.of("fixture", true));
              result.put("width", width);
              result.put("height", height);
              result.put(
                  "regions",
                  mode.equals("empty")
                      ? List.of()
                      : List.of(
                          java.util.Map.of(
                              "text",
                              mode.equals("footer") ? "Packet Pg. 114" : "Recognized road",
                              "score",
                              .9,
                              "polygon",
                              List.of(
                                  java.util.Map.of("x", 1, "y", 1),
                                  java.util.Map.of("x", width - 1, "y", 1),
                                  java.util.Map.of("x", width - 1, "y", height - 1),
                                  java.util.Map.of("x", 1, "y", height - 1)))));
              byte[] bytes =
                  JSON.writeValueAsBytes(
                      java.util.Map.of(
                          "executionId", id, "executionFinished", true, "result", result));
              exchange.sendResponseHeaders(mode.equals("terminal-error") ? 422 : 200, bytes.length);
              exchange.getResponseBody().write(bytes);
            } catch (Exception ignored) {
            } finally {
              exchange.close();
            }
          });
      server.start();
      return server;
    } catch (Exception error) {
      throw new ExceptionInInitializerError(error);
    }
  }
}
