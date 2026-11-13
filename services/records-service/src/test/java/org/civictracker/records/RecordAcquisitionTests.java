package org.civictracker.records;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.ByteArrayInputStream;
import java.net.CookieManager;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.civictracker.records.api.OperationViews;
import org.civictracker.records.application.OperationProblem;
import org.civictracker.records.application.RecordsFoundation;
import org.civictracker.records.application.RecordsOperationWorker;
import org.civictracker.records.application.RecordsOperations;
import org.civictracker.records.configuration.PortalSettings;
import org.civictracker.records.domain.AcquisitionData.Format;
import org.civictracker.records.domain.AcquisitionData.Item;
import org.civictracker.records.domain.AcquisitionData.Meeting;
import org.civictracker.records.domain.AcquisitionData.Observation;
import org.civictracker.records.domain.AcquisitionData.Profile;
import org.civictracker.records.domain.AcquisitionData.State;
import org.civictracker.records.domain.SourceInput;
import org.civictracker.records.portal.primegov.PortalHttp;
import org.civictracker.records.portal.primegov.PrimeGovParser;
import org.civictracker.records.storage.OriginalFileStore;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import tools.jackson.databind.ObjectMapper;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Timeout(70)
class RecordAcquisitionTests {
  private static final String PASSWORD = UUID.randomUUID().toString();
  private static final String HASH = "{bcrypt}" + new BCryptPasswordEncoder().encode(PASSWORD);
  private static final AtomicReference<String> LISTING = new AtomicReference<>("[]");
  private static final AtomicReference<String> UPCOMING = new AtomicReference<>("[]");
  private static final AtomicReference<String> PDF =
      new AtomicReference<>("%PDF-1.7\nfirst\n%%EOF");
  private static final AtomicReference<String> HTML = new AtomicReference<>();
  private static final AtomicReference<String> MODE = new AtomicReference<>("normal");
  private static final AtomicInteger DOWNLOADS = new AtomicInteger();
  private static final AtomicInteger CREDENTIAL_LEAKS = new AtomicInteger();
  private static final HttpServer SOURCE = source();
  private static final String BASE = "http://127.0.0.1:" + SOURCE.getAddress().getPort();
  @LocalServerPort int port;
  @Autowired RecordsOperations operations;
  @Autowired RecordsOperationWorker worker;
  @Autowired RecordsFoundation foundation;
  @Autowired OriginalFileStore files;
  @Autowired PrimeGovParser parser;
  @Autowired PortalHttp portal;
  @Autowired PortalSettings.Sources settings;
  @Autowired JdbcTemplate jdbc;
  @Autowired ObjectMapper json;
  @Autowired org.springframework.core.env.Environment environment;
  private final List<UUID> owned = new ArrayList<>();
  private HttpClient client;
  private String officialMeeting;

  @DynamicPropertySource
  static void config(DynamicPropertyRegistry registry) {
    registry.add("records.security.callers.reader", () -> HASH);
    registry.add("records.security.processors.processor", () -> HASH);
    registry.add("records.security.maintainers.maintainer", () -> HASH);
    registry.add("records.portal.sources.fixture.base-url", () -> BASE);
    registry.add("records.portal.sources.fixture.allowed-hosts[0]", () -> "127.0.0.1");
    registry.add("records.portal.sources.fixture.revision", () -> "test-1");
    registry.add("records.portal.sources.fixture.request-interval-millis", () -> 10);
    registry.add("records.portal.sources.fixture.connect-timeout-millis", () -> 500);
    registry.add("records.portal.sources.fixture.transfer-timeout-millis", () -> 700);
    registry.add("records.portal.sources.fixture.max-bytes", () -> 1000000);
    registry.add("records.portal.sources.fixture.max-redirects", () -> 3);
    registry.add("records.portal.sources.fixture.allow-loopback", () -> true);
  }

  @BeforeEach
  void prepare() {
    officialMeeting = UUID.randomUUID().toString();
    MODE.set("normal");
    DOWNLOADS.set(0);
    CREDENTIAL_LEAKS.set(0);
    PDF.set("%PDF-1.7\nfirst\n%%EOF");
    HTML.set(
        "<html><div id='MeetingContents'><div class='meeting-item' data-itemid='item-1' "
            + "data-hasattachments='True'><a href='/api/compilemeetingattachmenthistory/"
            + "historyattachment/?historyId=history-1'>Staff report</a></div></div></html>");
    listing(true, true, false);
    UPCOMING.set("[]");
    client =
        HttpClient.newBuilder()
            .cookieHandler(new CookieManager())
            .connectTimeout(Duration.ofSeconds(5))
            .build();
  }

  @AfterEach
  void cleanup() {
    client.close();
    jdbc.update(
        "update records.portal_request_gate set next_allowed_at=now() where host=?", "127.0.0.1");
    for (UUID id : owned) {
      jdbc.update(
          "update records.operation_slot set operation_id=null,attempt_id=null,worker_id=null "
              + "where operation_id=?",
          id);
      jdbc.update(
          "update records.record_operation set state='FAILED',completed_at=now(), "
              + "error_code='TEST_CLEANUP' where id=? and state in ('ACCEPTED','RUNNING')",
          id);
    }
  }

  @AfterAll
  static void stopSource() {
    SOURCE.stop(0);
  }

  @Test
  void authenticatedHttpPathDiscoversAcquiresAndReadsExactOriginals() throws Exception {
    var command = command();
    assertEquals(403, post("/discovery-operations", command, "processor", null).statusCode());
    assertEquals(403, token("reader").statusCode());
    String token = csrf("processor");
    assertEquals(403, post("/discovery-operations", command, "reader", token).statusCode());
    token = csrf("processor");
    var accepted = post("/discovery-operations", command, "processor", token);
    assertEquals(202, accepted.statusCode(), accepted.body());
    assertTrue(
        accepted
            .headers()
            .firstValue("Location")
            .orElseThrow()
            .endsWith(command.operationId().toString()));
    assertTrue(worker.processNext());
    UUID meeting = operations.meetings(command.operationId(), 0, 1).items().getFirst().id();
    var acquisition = acquire(command.operationId(), meeting);
    assertEquals(State.SUCCEEDED, acquisition.state());
    var result = operations.originals(acquisition.id(), 0, 20);
    assertEquals(3, result.items().size());
    var original =
        result.items().stream()
            .filter(o -> o.source().family().equals("attachment-history"))
            .findFirst()
            .orElseThrow();
    var bytes = get("/artifacts/" + original.retained().artifactId() + "/bytes", "reader");
    assertEquals(200, bytes.statusCode());
    assertEquals(PDF.get(), bytes.body());
    var detail = get("/meetings/" + meeting, "reader");
    assertEquals(200, detail.statusCode(), detail.body());
    assertTrue(detail.body().contains("item-1"));
    assertTrue(detail.body().contains("https://example.invalid/official-video"));
    assertFalse(detail.body().contains("storageKey"));
    token = csrf("processor");
    var replay = post("/discovery-operations", command, "processor", token);
    assertEquals(202, replay.statusCode());
    assertFalse(worker.processNext());
    assertEquals(0, CREDENTIAL_LEAKS.get());
    assertEquals(
        403,
        post(
                "/operations/" + acquisition.id() + "/resume",
                new OperationViews.Resume(UUID.randomUUID(), UUID.randomUUID(), true),
                "processor",
                token)
            .statusCode());
  }

  @Test
  void rescansRefetchKnownIdsAndPreserveChangedVersions() throws Exception {
    UUID first = discover();
    UUID meeting = operations.meetings(first, 0, 20).items().getFirst().id();
    var original = acquire(first, meeting);
    var old =
        operations.originals(original.id(), 0, 20).items().stream()
            .filter(o -> o.source().format() == Format.PDF)
            .findFirst()
            .orElseThrow()
            .retained();
    PDF.set("%PDF-1.7\nrevised\n%%EOF");
    int priorDownloads = DOWNLOADS.get();
    var next = acquire(discover(), meeting);
    assertTrue(DOWNLOADS.get() > priorDownloads);
    var updated =
        operations.originals(next.id(), 0, 20).items().stream()
            .filter(o -> o.retained().documentId().equals(old.documentId()))
            .findFirst()
            .orElseThrow()
            .retained();
    assertEquals(old.documentId(), updated.documentId());
    assertNotEquals(old.versionId(), updated.versionId());
    try (var stream = foundation.openVersion(old.versionId())) {
      assertEquals(
          "%PDF-1.7\nfirst\n%%EOF", new String(stream.readAllBytes(), StandardCharsets.UTF_8));
    }
    assertThrows(OperationProblem.class, () -> operations.originals(next.id(), -1, 20));
  }

  @Test
  void metadataOnlyCancellationSurvivesWithoutInventingDocumentsOrOutcomes() {
    listing(false, false, true);
    UUID discovery = discover();
    UUID meeting = operations.meetings(discovery, 0, 20).items().getFirst().id();
    var acquired = acquire(discovery, meeting);
    assertEquals(State.SUCCEEDED, acquired.state());
    assertTrue(operations.originals(acquired.id(), 0, 20).items().isEmpty());
    assertTrue(acquired.notes().contains("NO_PUBLISHED_SELECTED_ORIGINALS"));
    assertTrue(
        jdbc.queryForObject(
                "select cancellation_evidence from records.source_meeting " + "where id=?",
                String.class,
                meeting)
            .contains("Cancelled"));
  }

  @Test
  void unavailableTypesDifferFromBrokenPublishedLinksAndPriorBytesRemain() {
    listing(false, true, false);
    UUID discovery = discover();
    UUID meeting = operations.meetings(discovery, 0, 20).items().getFirst().id();
    var successful = acquire(discovery, meeting);
    assertEquals(State.SUCCEEDED, successful.state());
    UUID artifact =
        operations.originals(successful.id(), 0, 20).items().getFirst().retained().artifactId();
    MODE.set("missing");
    var failed = acquire(discovery, meeting);
    assertEquals(State.FAILED, failed.state());
    assertEquals("SOURCE_HTTP_FAILURE", failed.errorCode());
    assertThrows(OperationProblem.class, () -> operations.originals(failed.id(), 0, 20));
    assertEquals(
        1,
        jdbc.queryForObject(
            "select count(*) from records.original_version " + "where artifact_id=?",
            Integer.class,
            artifact));
  }

  @Test
  void emptyListingSucceedsButMalformedOrConflictingResponsesFail() {
    LISTING.set("[]");
    assertTrue(operations.meetings(discover(), 0, 20).items().isEmpty());
    LISTING.set("{\"error\":\"unavailable\"}");
    var malformed = command();
    operations.discover(malformed);
    worker.processNext();
    assertEquals(State.FAILED, operations.status(malformed.operationId()).state());
    listing(false, true, false);
    UPCOMING.set(LISTING.get().replace("Meeting fixture", "Different current title"));
    var conflict = command();
    operations.discover(conflict);
    worker.processNext();
    assertEquals(
        "CONFLICTING_SOURCE_MEETING", operations.status(conflict.operationId()).errorCode());
  }

  @Test
  void staleSnapshotAndUnsupportedDestinationDoNotBecomeSuccessfulImports() {
    UUID discovery = discover();
    UUID meeting = operations.meetings(discovery, 0, 20).items().getFirst().id();
    LISTING.set(LISTING.get().replace("Meeting fixture", "Changed source"));
    assertEquals("SOURCE_SELECTION_CHANGED", acquire(discovery, meeting).errorCode());
    assertThrows(
        OperationProblem.class,
        () ->
            portal.validate(
                settings.required("fixture"), URI.create("http://localhost/forbidden")));
    assertThrows(
        OperationProblem.class,
        () -> portal.validate(settings.required("fixture"), URI.create(BASE + "/file#fragment")));
  }

  @Test
  void duplicateConcurrentCommandsConvergeAndOnlyOneOperationIsClaimed() throws Exception {
    var command = command();
    try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
      var calls = new ArrayList<java.util.concurrent.Callable<OperationViews.Status>>();
      for (int i = 0; i < 6; i++) {
        calls.add(() -> operations.discover(command));
      }
      for (var future : executor.invokeAll(calls)) {
        assertEquals(command.operationId(), future.get().id());
      }
    }
    assertEquals(
        1,
        jdbc.queryForObject(
            "select count(*) from records.record_operation where id=?",
            Integer.class,
            command.operationId()));
    assertThrows(
        OperationProblem.class,
        () ->
            operations.discover(
                new OperationViews.DiscoveryCommand(
                    command.operationId(),
                    "fixture",
                    LocalDate.of(2026, 10, 1),
                    command.to(),
                    List.of("1"),
                    null)));
    var claimed = operations.claim();
    assertEquals(command.operationId(), claimed.id);
    assertEquals(null, operations.claim());
    assertThrows(
        OperationProblem.class,
        () ->
            operations.resume(
                claimed.id, new OperationViews.Resume(claimed.attemptId, claimed.workerId, true)));
    operations.complete(claimed.id, claimed.attemptId, "TEST_FAILURE");
    assertEquals(State.FAILED, operations.discover(command).state());
    assertFalse(worker.processNext());
  }

  @Test
  void interruptedProcessBlocksNewClaimsUntilExplicitReconciliation(@TempDir Path temporary)
      throws Exception {
    var command = command();
    operations.discover(command);
    Path log = temporary.resolve("claimed.log");
    String executable = System.getProperty("os.name").startsWith("Windows") ? "java.exe" : "java";
    Process process =
        new ProcessBuilder(
                Path.of(System.getProperty("java.home"), "bin", executable).toString(),
                "-cp",
                System.getProperty(
                    "surefire.test.class.path", System.getProperty("java.class.path")),
                RecordsOperationCrashProbe.class.getName())
            .redirectErrorStream(true)
            .redirectOutput(log.toFile())
            .start();
    try {
      long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(35);
      while (!Files.readString(log).contains("DURABLE_OPERATION_CLAIMED")
          && process.isAlive()
          && System.nanoTime() < deadline) {
        Thread.sleep(100);
      }
      assertTrue(
          Files.readString(log).contains("DURABLE_OPERATION_CLAIMED"), Files.readString(log));
      var running = operations.status(command.operationId());
      assertEquals(State.RUNNING, running.state());
      process.destroyForcibly().waitFor();
      assertFalse(worker.processNext());
      var resume = new OperationViews.Resume(running.attemptId(), running.workerId(), true);
      assertEquals(State.ACCEPTED, operations.resume(running.id(), resume).state());
      assertThrows(
          OperationProblem.class,
          () ->
              operations.transaction(() -> {
                operations.guard(running.id(), running.attemptId());
                return null;
              }));
      assertTrue(worker.processNext());
      assertEquals(State.SUCCEEDED, operations.status(running.id()).state());
      assertEquals(2, operations.status(running.id()).attempts());
    } finally {
      if (process.isAlive()) {
        process.destroyForcibly().waitFor();
      }
    }
  }

  @Test
  void incompleteOversizedAndStalledBodiesLeaveNoUsableReceipt() throws Exception {
    listing(false, true, false);
    UUID discovery = discover();
    UUID meeting = operations.meetings(discovery, 0, 20).items().getFirst().id();
    for (String mode : List.of("truncated", "large", "stall", "html-error")) {
      MODE.set(mode);
      var operation = acquire(discovery, meeting);
      assertEquals(State.FAILED, operation.state(), mode);
      assertEquals(
          0,
          jdbc.queryForObject(
              "select count(*) from records.acquisition_item "
                  + "where operation_id=? and version_id is not null",
              Integer.class,
              operation.id()),
          mode);
    }
  }

  @Test
  void receiptNamesHistoricalImportedBytesInsteadOfTheCurrentPointer() throws Exception {
    var at = Instant.parse("2026-11-13T12:00:00Z");
    String portalId = "receipt-" + UUID.randomUUID();
    var current =
        new SourceInput(
            portalId,
            "m",
            "Meeting",
            "2026-11-01",
            null,
            null,
            "template",
            "1",
            "en-US",
            "document-list",
            "d",
            URI.create(BASE + "/pdf"),
            "original.pdf",
            at,
            true,
            null);
    var selected = foundation.retain(current, new ByteArrayInputStream("newer".getBytes()));
    var older =
        new SourceInput(
            portalId,
            "m",
            "Meeting",
            "2026-11-01",
            null,
            null,
            "template",
            "1",
            "en-US",
            "document-list",
            "d",
            URI.create(BASE + "/old"),
            "original.pdf",
            at.minusSeconds(60),
            false,
            null);
    try (var staged = files.stage(new ByteArrayInputStream("older".getBytes()))) {
      var receipt = foundation.retainStaged(older, staged, "2026-10-01", older.sourceUrl());
      assertNotEquals(selected.versionId(), receipt.versionId());
      assertEquals(selected.versionId(), receipt.currentVersionId());
      assertFalse(receipt.currentChanged());
      try (var stream = foundation.openVersion(receipt.versionId())) {
        assertArrayEquals("older".getBytes(), stream.readAllBytes());
      }
    }
  }

  @Test
  void failedCheckpointRollsBackObservationAndExplicitResumeCommitsOnlyOnce() {
    listing(false, true, false);
    UUID discovery = discover();
    UUID meeting = operations.meetings(discovery, 0, 20).items().getFirst().id();
    UUID id = UUID.randomUUID();
    owned.add(id);
    operations.acquire(new OperationViews.AcquisitionCommand(id, discovery, meeting, null, null));
    var owner =
        new org.springframework.jdbc.datasource.DriverManagerDataSource(
            environment.getRequiredProperty("spring.flyway.url"),
            environment.getRequiredProperty("spring.flyway.user"),
            environment.getRequiredProperty("spring.flyway.password"));
    var migrations = new JdbcTemplate(owner);
    migrations.execute(
        "create function records.reject_test_checkpoint() returns trigger "
            + "language plpgsql as $$ begin if NEW.operation_id='"
            + id
            + "'::uuid and NEW.version_id is not null then raise exception 'test checkpoint"
            + " failure'; end if; return NEW; end $$");
    migrations.execute(
        "create trigger reject_test_checkpoint before update on records.acquisition_item for each"
            + " row execute function records.reject_test_checkpoint()");
    try {
      worker.processNext();
      assertEquals(State.FAILED, operations.status(id).state());
      assertEquals(
          0,
          jdbc.queryForObject(
              "select count(*) from records.source_observation o "
                  + "join records.source_document d on d.id=o.document_id where d.meeting_id=?",
              Integer.class,
              meeting));
      assertEquals(
          0,
          jdbc.queryForObject(
              "select count(*) from records.acquisition_item "
                  + "where operation_id=? and version_id is not null",
              Integer.class,
              id));
    } finally {
      migrations.execute("drop trigger reject_test_checkpoint on records.acquisition_item");
      migrations.execute("drop function records.reject_test_checkpoint()");
    }
    var failed = operations.status(id);
    operations.resume(id, new OperationViews.Resume(failed.attemptId(), failed.workerId(), true));
    worker.processNext();
    assertEquals(State.SUCCEEDED, operations.status(id).state());
    assertEquals(
        1,
        jdbc.queryForObject(
            "select count(*) from records.source_observation o "
                + "join records.source_document d on d.id=o.document_id where d.meeting_id=?",
            Integer.class,
            meeting));
  }

  @Test
  void resumeReusesCompletedPacketAndItsAtomicallySavedAttachmentSelection() {
    UUID discovery = discover();
    UUID meeting = operations.meetings(discovery, 0, 20).items().getFirst().id();
    MODE.set("missing");
    var failed = acquire(discovery, meeting);
    assertEquals(State.FAILED, failed.state());
    assertEquals(
        1,
        jdbc.queryForObject(
            "select count(*) from records.acquisition_item "
                + "where operation_id=? and version_id is not null",
            Integer.class,
            failed.id()));
    HTML.set("<html>must not be fetched on resume</html>");
    MODE.set("normal");
    operations.resume(
        failed.id(), new OperationViews.Resume(failed.attemptId(), failed.workerId(), true));
    worker.processNext();
    assertEquals(State.SUCCEEDED, operations.status(failed.id()).state());
    assertEquals(3, operations.originals(failed.id(), 0, 20).items().size());
    assertEquals(
        3,
        jdbc.queryForObject(
            "select count(*) from records.source_observation o "
                + "join records.source_document d on d.id=o.document_id where d.meeting_id=?",
            Integer.class,
            meeting));
  }

  @Test
  void approvedCrossHostRedirectUsesThePortalRateBudgetAndBoundedRedirectCount() throws Exception {
    var original = settings.required("fixture");
    var profile =
        new Profile(
            original.baseUrl(),
            List.of("127.0.0.1", "localhost"),
            original.revision(),
            80,
            original.connectTimeoutMillis(),
            2000,
            original.maxBytes(),
            3,
            true);
    try (var fetched = portal.fetch(profile, URI.create(BASE + "/redirect"))) {
      assertEquals("localhost", fetched.resolved().getHost());
      assertEquals(PDF.get(), Files.readString(fetched.staged().path()));
    }
    assertEquals(
        0,
        jdbc.queryForObject(
            "select count(*) from records.portal_request_gate where host='localhost'",
            Integer.class));
    var noRedirects =
        new Profile(
            original.baseUrl(),
            profile.allowedHosts(),
            original.revision(),
            0,
            original.connectTimeoutMillis(),
            2000,
            original.maxBytes(),
            0,
            true);
    var denied =
        assertThrows(
            OperationProblem.class,
            () -> portal.fetch(noRedirects, URI.create(BASE + "/redirect")));
    assertEquals("TOO_MANY_SOURCE_REDIRECTS", denied.code());
  }

  @Test
  void rateLimitedResponsePersistsRetryAfterWithoutAutomaticRetry() {
    var denied =
        assertThrows(
            OperationProblem.class,
            () -> portal.fetch(settings.required("fixture"), URI.create(BASE + "/rate-limit")));
    assertEquals("SOURCE_RATE_LIMITED", denied.code());
    Instant next =
        jdbc.queryForObject(
            "select next_allowed_at from records.portal_request_gate " + "where host='127.0.0.1'",
            (row, n) -> row.getTimestamp(1).toInstant());
    assertTrue(next.isAfter(Instant.now().plusMillis(1000)));
  }

  @Test
  void maintainerCanResumeFailedWorkThroughCsrfProtectedHttp() throws Exception {
    listing(false, true, false);
    UUID discovery = discover();
    UUID meeting = operations.meetings(discovery, 0, 20).items().getFirst().id();
    MODE.set("missing");
    var failed = acquire(discovery, meeting);
    String token = csrf("maintainer");
    var input = new OperationViews.Resume(failed.attemptId(), failed.workerId(), true);
    assertEquals(
        202,
        post("/operations/" + failed.id() + "/resume", input, "maintainer", token).statusCode());
    MODE.set("normal");
    worker.processNext();
    assertEquals(State.SUCCEEDED, operations.status(failed.id()).state());
  }

  @Test
  void unknownMetadataCannotErasePreviouslyObservedMeetingAndItemValues() {
    Instant at = Instant.now();
    String source = "metadata-" + UUID.randomUUID();
    Meeting first =
        new Meeting(
            "m",
            "1",
            "Known meeting",
            "2026-11-01T16:00:00",
            LocalDate.of(2026, 11, 1),
            null,
            null,
            null,
            List.of());
    UUID id = foundation.observeMeeting(new Observation(source, first, at));
    foundation.observeItems(id, List.of(new Item("item", "Known item label")), at);
    Meeting partial = new Meeting("m", null, null, null, null, null, null, null, List.of());
    foundation.observeMeeting(new Observation(source, partial, at.plusSeconds(1)));
    foundation.observeItems(id, List.of(new Item("item", null)), at.plusSeconds(1));
    assertEquals(
        "1",
        jdbc.queryForObject(
            "select committee_id from records.source_meeting " + "where id=?", String.class, id));
    assertEquals(
        LocalDate.of(2026, 11, 1),
        jdbc.queryForObject(
            "select local_date from records.source_meeting where id=?", LocalDate.class, id));
    assertEquals(
        "Known item label",
        jdbc.queryForObject(
            "select label from records.agenda_item where meeting_id=? and official_id='item'",
            String.class,
            id));
  }

  private UUID discover() {
    var command = command();
    operations.discover(command);
    assertTrue(worker.processNext());
    assertEquals(
        State.SUCCEEDED,
        operations.status(command.operationId()).state(),
        operations.status(command.operationId()).errorCode());
    return command.operationId();
  }

  private OperationViews.DiscoveryCommand command() {
    UUID id = UUID.randomUUID();
    owned.add(id);
    return new OperationViews.DiscoveryCommand(
        id, "fixture", LocalDate.of(2026, 11, 1), LocalDate.of(2026, 11, 30), List.of("1"), null);
  }

  private OperationViews.Status acquire(UUID discovery, UUID meeting) {
    UUID id = UUID.randomUUID();
    owned.add(id);
    operations.acquire(new OperationViews.AcquisitionCommand(id, discovery, meeting, null, null));
    assertTrue(worker.processNext());
    return operations.status(id);
  }

  private void listing(boolean html, boolean pdf, boolean cancelled) {
    List<String> docs = new ArrayList<>();
    if (html) {
      docs.add(
          "{\"id\":101,\"templateId\":7,\"compileOutputType\":3,\"language\":\"en-US\","
              + "\"templateName\":\"HTML Packet\",\"publishDate\":\"2026-11-01T10:00:00\"}");
    }
    if (pdf) {
      docs.add(
          "{\"id\":102,\"templateId\":8,\"compileOutputType\":1,\"language\":\"en-US\","
              + "\"templateName\":\"Agenda\"}");
    }
    LISTING.set(
        "[{\"id\":\""
            + officialMeeting
            + "\",\"committeeId\":1,"
            + "\"dateTime\":\"2026-11-01T16:00:00\",\"title\":\"Meeting fixture"
            + (cancelled ? " - Cancelled" : "")
            + "\",\"videoUrl\":\"https://example.invalid/official-video\","
            + "\"documentList\":["
            + String.join(",", docs)
            + "]}]");
  }

  private HttpResponse<String> token(String user) throws Exception {
    return get("/csrf", user);
  }

  private String csrf(String user) throws Exception {
    var response = token(user);
    assertEquals(200, response.statusCode(), response.body());
    return json.readTree(response.body()).get("token").asString();
  }

  private HttpResponse<String> get(String path, String user) throws Exception {
    return client.send(
        HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + "/internal/v1" + path))
            .header("Authorization", basic(user))
            .timeout(Duration.ofSeconds(10))
            .GET()
            .build(),
        HttpResponse.BodyHandlers.ofString());
  }

  private HttpResponse<String> post(String path, Object body, String user, String token)
      throws Exception {
    var request =
        HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + "/internal/v1" + path))
            .header("Authorization", basic(user))
            .header("Content-Type", "application/json")
            .timeout(Duration.ofSeconds(10));
    if (token != null) {
      request.header("X-XSRF-TOKEN", token);
    }
    return client.send(
        request.POST(HttpRequest.BodyPublishers.ofString(json.writeValueAsString(body))).build(),
        HttpResponse.BodyHandlers.ofString());
  }

  private String basic(String user) {
    return "Basic "
        + Base64.getEncoder()
            .encodeToString((user + ":" + PASSWORD).getBytes(StandardCharsets.UTF_8));
  }

  private static HttpServer source() {
    try {
      HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
      server.setExecutor(Executors.newVirtualThreadPerTaskExecutor());
      server.createContext("/", RecordAcquisitionTests::reply);
      server.start();
      return server;
    } catch (java.io.IOException error) {
      throw new ExceptionInInitializerError(error);
    }
  }

  private static void reply(HttpExchange exchange) throws java.io.IOException {
    try (exchange) {
      if (exchange.getRequestHeaders().containsKey("Authorization")) {
        CREDENTIAL_LEAKS.incrementAndGet();
      }
      String path = exchange.getRequestURI().getPath();
      if (path.equals("/redirect")) {
        exchange
            .getResponseHeaders()
            .add(
                "Location",
                "http://localhost:" + SOURCE.getAddress().getPort() + "/redirected-pdf");
        exchange.sendResponseHeaders(302, -1);
        return;
      }
      if (path.equals("/rate-limit")) {
        exchange.getResponseHeaders().add("Retry-After", "2");
        exchange.sendResponseHeaders(429, -1);
        return;
      }
      String value;
      if (path.contains("ListArchivedMeetings")) {
        value = LISTING.get();
      } else if (path.contains("ListUpcomingMeetings")) {
        value = UPCOMING.get();
      } else if (path.equals("/Portal/Meeting")) {
        value = HTML.get();
      } else {
        DOWNLOADS.incrementAndGet();
        if (MODE.get().equals("missing")) {
          exchange.sendResponseHeaders(404, -1);
          return;
        }
        if (MODE.get().equals("large")) {
          exchange.sendResponseHeaders(200, 2000000);
          return;
        }
        if (MODE.get().equals("truncated")) {
          exchange.sendResponseHeaders(200, 100);
          exchange.getResponseBody().write("%PDF-".getBytes(StandardCharsets.UTF_8));
          return;
        }
        if (MODE.get().equals("stall")) {
          exchange.sendResponseHeaders(200, 100);
          exchange.getResponseBody().write("%PDF-".getBytes(StandardCharsets.UTF_8));
          exchange.getResponseBody().flush();
          try {
            Thread.sleep(1800);
          } catch (InterruptedException error) {
            Thread.currentThread().interrupt();
          }
          return;
        }
        value = MODE.get().equals("html-error") ? "<html>upstream error</html>" : PDF.get();
      }
      byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
      exchange.sendResponseHeaders(200, bytes.length);
      exchange.getResponseBody().write(bytes);
    }
  }
}
