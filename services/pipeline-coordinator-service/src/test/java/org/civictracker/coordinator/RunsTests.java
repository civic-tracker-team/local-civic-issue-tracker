package org.civictracker.coordinator;

import static org.civictracker.coordinator.CoordinatorOwners.id;
import static org.junit.jupiter.api.Assertions.*;

import jakarta.persistence.EntityManager;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.civictracker.coordinator.config.CoordinatorRuntime;
import org.civictracker.coordinator.config.CoordinatorSettings;
import org.civictracker.coordinator.run.MeetingView;
import org.civictracker.coordinator.run.Phase;
import org.civictracker.coordinator.run.RunDriver;
import org.civictracker.coordinator.run.RunPlan;
import org.civictracker.coordinator.run.RunProblem;
import org.civictracker.coordinator.run.RunState;
import org.civictracker.coordinator.run.RunView;
import org.civictracker.coordinator.run.Runs;
import org.civictracker.coordinator.run.StartRun;
import org.civictracker.coordinator.run.StepKind;
import org.civictracker.coordinator.run.WaitingState;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.core.env.Environment;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import tools.jackson.databind.ObjectMapper;

@SpringBootTest(
    properties = {
      "coordinator.preparation-profile-id=synthetic-profile",
      "coordinator.stall-after=PT1M",
      "coordinator.records.username=owner",
      "coordinator.records.password=synthetic-password",
      "coordinator.records.timeout-seconds=5",
      "coordinator.analysis.username=owner",
      "coordinator.analysis.password=synthetic-password",
      "coordinator.analysis.timeout-seconds=5"
    })
@Import(RunsTests.Config.class)
@Timeout(40)
class RunsTests {
  static final CoordinatorOwners OWNERS = new CoordinatorOwners();

  @DynamicPropertySource
  static void properties(DynamicPropertyRegistry r) {
    r.add("coordinator.records.base-url", OWNERS.records::base);
    r.add("coordinator.analysis.base-url", OWNERS.analysis::base);
  }

  @AfterAll
  static void closeOwners() {
    OWNERS.close();
  }

  @Autowired Runs runs;
  @Autowired org.civictracker.coordinator.publication.Publications publications;
  UUID savedPointer;
  @Autowired JdbcTemplate jdbc;
  @Autowired EntityManager entities;
  @Autowired PlatformTransactionManager manager;
  @Autowired ObjectMapper json;
  @Autowired CoordinatorSettings settings;
  @Autowired Environment environment;
  @Autowired MutableClock clock;
  @Autowired ApplicationContext context;

  @TestConfiguration
  static class Config {
    @Bean
    @Primary
    MutableClock testClock() {
      return new MutableClock();
    }
  }

  static class MutableClock extends Clock {
    Instant now = Instant.parse("2026-01-01T00:00:00Z");

    @Override
    public ZoneId getZone() {
      return ZoneOffset.UTC;
    }

    @Override
    public Clock withZone(ZoneId zone) {
      return this;
    }

    @Override
    public Instant instant() {
      return now;
    }
  }

  @BeforeEach
  void reset() {
    runs.active().ifPresent(r -> runs.abandon(r.runId(), "Fixture cleanup", "maintainer"));
    savedPointer =
        jdbc.queryForObject(
            "select publication_id from coordinator.current_publication where id=1", UUID.class);
    OWNERS.reset();
    clock.now = Instant.parse("2026-01-01T00:00:00Z");
  }

  @AfterEach
  void cleanup() {
    OWNERS.records.beforeReply = r -> {};
    OWNERS.analysis.beforeReply = r -> {};
    runs.active().ifPresent(r -> runs.abandon(r.runId(), "Fixture cleanup", "maintainer"));
    jdbc.update(
        "update coordinator.current_publication set publication_id=? where id=1", savedPointer);
    assertNull(OWNERS.records.failure.get(), () -> String.valueOf(OWNERS.records.failure.get()));
    assertNull(OWNERS.analysis.failure.get(), () -> String.valueOf(OWNERS.analysis.failure.get()));
  }

  StartRun command() {
    return new StartRun(
        UUID.randomUUID(),
        "synthetic",
        LocalDate.of(2026, 1, 1),
        LocalDate.of(2026, 1, 31),
        List.of("council"));
  }

  RunView drive(UUID id, RunState target) {
    for (int n = 0; n < 60; n++) {
      var view = runs.view(id);
      if (view.state() == target) {
        return view;
      }
      if (view.state() != RunState.ACTIVE) {
        fail("Unexpected run state: " + view.state() + " " + view.halt());
      }
      runs.tick();
    }
    fail("Run did not reach " + target);
    return null;
  }

  @Test
  void threeMeetingRunPreparesEverythingThenAnalyzesAndStopsAtReadyWithoutPublication() {
    var command = command();
    runs.start(command, "maintainer");
    var ready = drive(command.runId(), RunState.READY);
    assertEquals(
        List.of("M1", "M2", "M3"), ready.meetings().stream().map(MeetingView::officialId).toList());
    assertEquals(
        List.of("ANALYZED", "NO_INPUTS", "ANALYZED"),
        ready.meetings().stream().map(MeetingView::progress).toList());
    assertEquals(
        "CANCELLED",
        jdbc.queryForObject(
            "select no_input_reason from coordinator.run_meeting where run_id=? and ordinal=1",
            String.class,
            command.runId()));
    assertEquals(2, OWNERS.analysis.operations.size());
    assertEquals(
        2,
        OWNERS.records.operations.values().stream()
            .filter(o -> o.kind == StepKind.PREPARE)
            .count());
    var first =
        OWNERS.analysis.operations.values().stream()
            .filter(o -> o.command.get("selection").get("officialId").stringValue().equals("M1"))
            .findFirst()
            .orElseThrow();
    var last =
        OWNERS.analysis.operations.values().stream()
            .filter(o -> o.command.get("selection").get("officialId").stringValue().equals("M3"))
            .findFirst()
            .orElseThrow();
    assertTrue(first.command.get("priorRevisionIds").isEmpty());
    assertEquals(
        CoordinatorOwners.Owner.revision(first).toString(),
        last.command.get("priorRevisionIds").get(0).stringValue());
    assertEquals(Phase.PUBLISHING, ready.phase());
    assertNull(ready.current());
    assertNull(ready.waiting());
    assertNull(
        jdbc.queryForObject(
            "select publication_id from coordinator.current_publication where id=1", UUID.class));
    assertEquals(command.runId(), runs.active().orElseThrow().runId());
    assertEquals(
        "RUN_ACTIVE",
        assertThrows(RunProblem.class, () -> runs.start(command(), "maintainer")).code());
    int calls = OWNERS.records.requests.size() + OWNERS.analysis.requests.size();
    runs.tick();
    assertEquals(calls, OWNERS.records.requests.size() + OWNERS.analysis.requests.size());
  }

  @Test
  void pinsKeepRecordsOrderAfterDatabaseReloadAndRequestIsFrozenBeforePost() {
    var command = command();
    runs.start(command, "maintainer");
    OWNERS.analysis.beforeReply =
        request -> {
          if (!request.method().equals("POST")) {
            return;
          }
          var sent = CoordinatorOwners.JSON.readTree(request.body());
          UUID meeting = UUID.fromString(sent.get("selection").get("meetingId").stringValue());
          assertEquals(
              request.body(),
              jdbc.queryForObject(
                  "select frozen_request_json from coordinator.run_meeting where run_id=? and"
                      + " meeting_id=?",
                  String.class,
                  command.runId(),
                  meeting));
          var savedHash =
              jdbc.queryForObject(
                  "select frozen_request_sha256 from coordinator.run_meeting where run_id=? and"
                      + " meeting_id=?",
                  String.class,
                  command.runId(),
                  meeting);
          assertEquals(
              request.body(),
              org.civictracker.coordinator.owners.AnalysisClient.readFrozen(
                      request.body(), savedHash)
                  .json());
          var ids = sent.get("selection").get("inputs");
          long meetingId = meeting.getLeastSignificantBits();
          assertEquals(
              id(meetingId * 10 + 2).toString(), ids.get(0).get("acquisitionItemId").stringValue());
          assertEquals(
              id(meetingId * 10 + 1).toString(), ids.get(1).get("acquisitionItemId").stringValue());
          assertEquals(
              2,
              jdbc.queryForObject(
                  "select count(*) from coordinator.run_meeting where run_id=? and progress in"
                      + " ('PREPARED','ANALYZED')",
                  Integer.class,
                  command.runId()));
        };
    assertEquals(RunState.READY, drive(command.runId(), RunState.READY).state());
  }

  @Test
  void failedOwnerHaltsLaterWorkAndRetryUsesANewIdWithReplacement() {
    OWNERS.records.failKind = StepKind.ACQUIRE;
    var command = command();
    runs.start(command, "maintainer");
    var halted = drive(command.runId(), RunState.HALTED);
    assertEquals("SYNTHETIC_OWNER_FAILURE", halted.halt().code());
    assertEquals(StepKind.ACQUIRE, halted.halt().kind());
    assertEquals(
        1,
        OWNERS.records.operations.values().stream()
            .filter(o -> o.kind == StepKind.ACQUIRE)
            .count());
    assertTrue(OWNERS.analysis.operations.isEmpty());
    var old = halted.halt().operationId();
    var retry = runs.retry(command.runId(), halted.halt().haltId(), "maintainer");
    assertEquals(2, retry.meetings().getFirst().attempts().acquire());
    assertNotEquals(old, retry.current().operationId());
    assertEquals(retry, runs.retry(command.runId(), halted.halt().haltId(), "maintainer"));
    runs.tick();
    var replaced = OWNERS.records.operations.get(retry.current().operationId());
    assertEquals(old.toString(), replaced.command.get("replacesOperationId").stringValue());
    assertEquals(
        1,
        jdbc.queryForObject(
            "select count(*) from coordinator.run_event where run_id=? and kind='RETRIED'",
            Integer.class,
            command.runId()));
    assertEquals(RunState.READY, drive(command.runId(), RunState.READY).state());
  }

  @Test
  void failedAnalysisIsShownAsFailedAndRetryFreezesANewIdWithTheSameInputs() {
    OWNERS.analysis.failKind = StepKind.ANALYZE;
    var command = command();
    runs.start(command, "maintainer");
    var halted = drive(command.runId(), RunState.HALTED);
    assertEquals("FAILED", halted.current().observedState());
    assertEquals(1, OWNERS.analysis.operations.size());
    var old = OWNERS.analysis.operations.get(halted.halt().operationId());
    var oldBytes =
        jdbc.queryForObject(
            "select frozen_request_json from coordinator.run_meeting where run_id=? and ordinal=0",
            String.class,
            command.runId());
    assertEquals(old.bytes, oldBytes);
    runs.retry(command.runId(), halted.halt().haltId(), "maintainer");
    runs.tick();
    var retry = runs.view(command.runId());
    var replacement = OWNERS.analysis.operations.get(retry.current().operationId());
    assertNotNull(replacement);
    assertNotEquals(old.id, replacement.id);
    assertEquals(old.command.get("selection"), replacement.command.get("selection"));
    assertEquals(old.command.get("priorRevisionIds"), replacement.command.get("priorRevisionIds"));
    assertFalse(replacement.command.has("replacesOperationId"));
    assertEquals(
        replacement.bytes,
        jdbc.queryForObject(
            "select frozen_request_json from coordinator.run_meeting where run_id=? and ordinal=0",
            String.class,
            command.runId()));
    assertEquals(RunState.READY, drive(command.runId(), RunState.READY).state());
  }

  @Test
  void blockedOwnerContinuesAfterOwnerSideResumeWithoutACoordinatorHaltOrRetry() {
    OWNERS.records.holdKind = StepKind.PREPARE;
    OWNERS.records.blocked = true;
    var command = command();
    runs.start(command, "maintainer");
    RunView view = null;
    for (int n = 0; n < 20; n++) {
      runs.tick();
      view = runs.view(command.runId());
      if (view.waiting() != null && view.waiting().state() == WaitingState.OWNER_BLOCKED) {
        break;
      }
    }
    assertNotNull(view.waiting());
    assertEquals(WaitingState.OWNER_BLOCKED, view.waiting().state());
    var held = OWNERS.records.latest(StepKind.PREPARE);
    assertEquals(held.blocked, view.waiting().ownerBlockedId());
    assertEquals(view.current().ownerStatusUrl(), view.waiting().ownerStatusUrl());
    assertEquals(RunState.ACTIVE, view.state());
    assertNull(view.halt());
    held.blocked = null;
    held.state = "SUCCEEDED";
    OWNERS.records.holdKind = null;
    assertEquals(RunState.READY, drive(command.runId(), RunState.READY).state());
    assertEquals(
        0,
        jdbc.queryForObject(
            "select count(*) from coordinator.run_event where run_id=? and kind in"
                + " ('HALTED','RETRIED')",
            Integer.class,
            command.runId()));
    assertTrue(
        OWNERS.records.requests.stream().noneMatch(r -> r.uri().getPath().endsWith("/resume")));
  }

  @Test
  void aNewRunsInstanceContinuesTheSavedCursorAfterRestart() {
    var command = command();
    runs.start(command, "maintainer");
    runs.tick();
    UUID discovery = runs.view(command.runId()).current().operationId();
    int posts =
        (int) OWNERS.records.requests.stream().filter(r -> r.method().equals("POST")).count();
    var restarted =
        new Runs(entities, manager, json, settings, new CoordinatorRuntime(environment), clock);
    for (int n = 0; n < 40 && restarted.view(command.runId()).state() == RunState.ACTIVE; n++) {
      restarted.tick();
    }
    assertEquals(RunState.READY, restarted.view(command.runId()).state());
    assertEquals(
        1,
        OWNERS.records.requests.stream()
            .filter(r -> r.method().equals("POST") && r.uri().getPath().contains("discovery"))
            .count());
    assertEquals(StepKind.DISCOVER, OWNERS.records.operations.get(discovery).kind);
    assertEquals(1, posts);
  }

  @Test
  void duplicateStartIsIdempotentConflictsAreExplicitAndAbandonReleasesTheSlot() {
    var command = command();
    var first = runs.start(command, "maintainer");
    assertEquals(first, runs.start(command, "another-maintainer"));
    assertEquals(
        "RUN_ID_CONFLICT",
        assertThrows(
                RunProblem.class,
                () ->
                    runs.start(
                        new StartRun(
                            command.runId(),
                            "different",
                            command.from(),
                            command.to(),
                            command.committeeIds()),
                        "maintainer"))
            .code());
    var busy = assertThrows(RunProblem.class, () -> runs.start(command(), "maintainer"));
    assertEquals(409, busy.status());
    assertEquals(command.runId(), busy.activeRunId());
    assertEquals(
        1,
        jdbc.queryForObject(
            "select count(*) from coordinator.run_event where run_id=? and kind='STARTED'",
            Integer.class,
            command.runId()));
    var abandoned = runs.abandon(command.runId(), "Synthetic stop", "maintainer");
    assertEquals(RunState.ABANDONED, abandoned.state());
    assertEquals(abandoned, runs.abandon(command.runId(), "Repeated stop", "maintainer"));
    assertTrue(runs.active().isEmpty());
    assertEquals(RunState.ACTIVE, runs.start(command(), "maintainer").state());
    assertNull(
        jdbc.queryForObject(
            "select publication_id from coordinator.current_publication where id=1", UUID.class));
  }

  @Test
  void unreachableAndStalledAreDisplayStatesAndDoNotAdvanceAttempts() {
    var command = command();
    OWNERS.records.holdKind = StepKind.DISCOVER;
    runs.start(command, "maintainer");
    runs.tick();
    runs.tick();
    var before = runs.view(command.runId());
    assertNull(before.waiting());
    clock.now = clock.now.plusSeconds(61);
    runs.tick();
    var stalled = runs.view(command.runId());
    assertEquals(WaitingState.STALLED, stalled.waiting().state());
    assertEquals(before.current().observedAt(), stalled.current().observedAt());
    OWNERS.records.unreachable = true;
    runs.tick();
    assertEquals(WaitingState.OWNER_UNREACHABLE, runs.view(command.runId()).waiting().state());
    assertEquals(RunState.ACTIVE, runs.view(command.runId()).state());
    assertEquals(
        before.current().operationId(), runs.view(command.runId()).current().operationId());
    OWNERS.records.unreachable = false;
    OWNERS.records.holdKind = null;
    OWNERS.records.latest(StepKind.DISCOVER).state = "SUCCEEDED";
    assertEquals(RunState.READY, drive(command.runId(), RunState.READY).state());
  }

  @Test
  void lostSubmissionReplyIsReconciledByIdWithoutDuplicatingTheCommand() {
    OWNERS.records.lostPost = true;
    var command = command();
    runs.start(command, "maintainer");
    runs.tick();
    assertEquals(WaitingState.OWNER_UNREACHABLE, runs.view(command.runId()).waiting().state());
    assertEquals(RunState.READY, drive(command.runId(), RunState.READY).state());
    assertEquals(
        1,
        OWNERS.records.requests.stream()
            .filter(r -> r.method().equals("POST") && r.uri().getPath().contains("discovery"))
            .count());
  }

  @Test
  void malformedDiscoveryAndPinJoinHaltBeforeAnyAnalysis() {
    OWNERS.records.undated = true;
    var first = command();
    runs.start(first, "maintainer");
    assertEquals("UNDATED_MEETING", drive(first.runId(), RunState.HALTED).halt().code());
    assertEquals(1, OWNERS.records.operations.size());
    runs.abandon(first.runId(), "Reset synthetic case", "maintainer");
    OWNERS.reset();
    OWNERS.records.meetingCount = settings.maxMeetings() + 1;
    var second = command();
    runs.start(second, "maintainer");
    assertEquals("RANGE_EXCEEDS_LIMIT", drive(second.runId(), RunState.HALTED).halt().code());
    runs.abandon(second.runId(), "Reset synthetic case", "maintainer");
    OWNERS.reset();
    OWNERS.records.badPinJoin = true;
    var third = command();
    runs.start(third, "maintainer");
    assertEquals("PIN_JOIN_MISMATCH", drive(third.runId(), RunState.HALTED).halt().code());
    assertTrue(OWNERS.analysis.operations.isEmpty());
  }

  @Test
  void twoConcurrentStartsCannotBothAcquireTheSlot() throws Exception {
    var a = command();
    var b = command();
    var gate = new CountDownLatch(1);
    try (var pool = Executors.newFixedThreadPool(2)) {
      var fa = pool.submit(() -> startAfter(gate, a));
      var fb = pool.submit(() -> startAfter(gate, b));
      gate.countDown();
      var results = List.of(fa.get(10, TimeUnit.SECONDS), fb.get(10, TimeUnit.SECONDS));
      assertEquals(1, results.stream().filter(RunView.class::isInstance).count());
      var rejected =
          results.stream()
              .filter(RunProblem.class::isInstance)
              .map(RunProblem.class::cast)
              .findFirst()
              .orElseThrow();
      assertEquals("RUN_ACTIVE", rejected.code());
      assertEquals(runs.active().orElseThrow().runId(), rejected.activeRunId());
    }
  }

  Object startAfter(CountDownLatch gate, StartRun command) throws InterruptedException {
    gate.await();
    try {
      return runs.start(command, "maintainer");
    } catch (RunProblem problem) {
      return problem;
    }
  }

  @Test
  void abandonCanCommitWhileOwnerHttpIsInFlightAndTheLateResultIsIgnored() throws Exception {
    var command = command();
    runs.start(command, "maintainer");
    runs.tick();
    var entered = new CountDownLatch(1);
    var release = new CountDownLatch(1);
    OWNERS.records.beforeReply =
        request -> {
          if (request.method().equals("GET")) {
            entered.countDown();
            await(release);
          }
        };
    try (var pool = Executors.newSingleThreadExecutor()) {
      var pending = pool.submit(runs::tick);
      try {
        assertTrue(entered.await(5, TimeUnit.SECONDS));
        assertEquals(
            RunState.ABANDONED,
            runs.abandon(command.runId(), "Stop during HTTP", "maintainer").state());
      } finally {
        release.countDown();
      }
      pending.get(10, TimeUnit.SECONDS);
    }
    assertTrue(runs.active().isEmpty());
    assertTrue(runs.view(command.runId()).meetings().isEmpty());
  }

  @Test
  void conflictingConcurrentDiscoveryFactsHaltRatherThanOverwriteTheFirstFact() throws Exception {
    var command = command();
    runs.start(command, "maintainer");
    runs.tick();
    var entered = new CountDownLatch(1);
    var release = new CountDownLatch(1);
    OWNERS.records.discovery =
        read -> {
          var source = new LinkedHashMap<String, Object>();
          source.put("officialId", "M1");
          source.put("title", read == 0 ? "First response" : "Changed response");
          source.put("localDate", "2026-01-01");
          source.put("cancellationEvidence", null);
          if (read == 0) {
            entered.countDown();
            await(release);
          }
          return List.of(Map.of("id", id(101), "source", source));
        };
    try (var pool = Executors.newSingleThreadExecutor()) {
      var pending = pool.submit(runs::tick);
      try {
        assertTrue(entered.await(5, TimeUnit.SECONDS));
        runs.tick();
      } finally {
        release.countDown();
      }
      pending.get(10, TimeUnit.SECONDS);
    }
    var halted = runs.view(command.runId());
    assertEquals(RunState.HALTED, halted.state());
    assertEquals("FACT_CONFLICT", halted.halt().code());
    assertEquals("Changed response", halted.meetings().getFirst().title());
  }

  @Test
  void concurrentTicksCannotInsertDuplicateFactsBeforeTheVersionGuard() throws Exception {
    var command = command();
    runs.start(command, "maintainer");
    runs.tick();
    var both = new CountDownLatch(2);
    var original = OWNERS.records.discovery;
    OWNERS.records.discovery =
        read -> {
          both.countDown();
          await(both);
          return original.apply(read);
        };
    String suffix = UUID.randomUUID().toString().replace("-", "");
    String function = "coordinator.pause_" + suffix;
    String trigger = "pause_" + suffix;
    try (var connection =
            java.sql.DriverManager.getConnection(
                environment.getProperty("COORDINATOR_DB_URL"),
                environment.getProperty("COORDINATOR_MIGRATION_USER"),
                environment.getProperty("COORDINATOR_MIGRATION_PASSWORD"));
        var statement = connection.createStatement()) {
      statement.execute(
          "create function "
              + function
              + "() returns trigger language plpgsql as $$ begin if NEW.run_id='"
              + command.runId()
              + "' then perform pg_sleep(0.3); end if; return NEW; end $$");
      statement.execute(
          "create trigger "
              + trigger
              + " before insert on coordinator.run_meeting for each row execute function "
              + function
              + "()");
      try (var pool = Executors.newFixedThreadPool(2)) {
        var first = pool.submit(runs::tick);
        var second = pool.submit(runs::tick);
        first.get(15, TimeUnit.SECONDS);
        second.get(15, TimeUnit.SECONDS);
      } finally {
        statement.execute("drop trigger " + trigger + " on coordinator.run_meeting");
        statement.execute("drop function " + function + "()");
      }
    }
    assertEquals(3, runs.view(command.runId()).meetings().size());
    assertNull(runs.view(command.runId()).halt());
    assertEquals(RunState.READY, drive(command.runId(), RunState.READY).state());
  }

  @Test
  void admissionPinsExistingPublicationAndRejectsItsInclusiveCoverage() {
    UUID previousPointer =
        jdbc.queryForObject(
            "select publication_id from coordinator.current_publication where id=1", UUID.class);
    var previous =
        new StartRun(
            UUID.randomUUID(),
            "synthetic",
            LocalDate.of(2025, 12, 1),
            LocalDate.of(2025, 12, 31),
            List.of("council"));
    runs.start(previous, "maintainer");
    UUID publication = RunPlan.publicationId(previous.runId()), revision = UUID.randomUUID();
    // Seed a previously published collection. Unit 4 itself never publishes.
    new org.springframework.transaction.support.TransactionTemplate(manager)
        .executeWithoutResult(tx -> {
          jdbc.update(
              "insert into coordinator.publication(id,run_id,published_at) values (?,?,now())",
              publication,
              previous.runId());
          jdbc.update(
              "insert into coordinator.publication_range(publication_id,range_from,range_to) values"
                  + " (?,?,?)",
              publication,
              previous.from(),
              previous.to());
          jdbc.update(
              "insert into"
                  + " coordinator.publication_meeting(publication_id,meeting_id,official_id,meeting_date,title,disposition,revision_id)"
                  + " values (?,?,'previous',?,'Previous synthetic meeting','ANALYZED',?)",
              publication,
              UUID.randomUUID(),
              LocalDate.of(2025, 12, 15),
              revision);
          jdbc.update(
              "update coordinator.current_publication set publication_id=? where id=1",
              publication);
          jdbc.update(
              "update coordinator.run set state='PUBLISHED',completed_at=now() where id=?",
              previous.runId());
          jdbc.update("update coordinator.run_slot set run_id=null where id=1");
        });
    try {
      var overlap =
          new StartRun(
              UUID.randomUUID(),
              "synthetic",
              previous.to(),
              LocalDate.of(2026, 1, 3),
              List.of("council"));
      assertEquals(
          "RANGE_ALREADY_PUBLISHED",
          assertThrows(RunProblem.class, () -> runs.start(overlap, "maintainer")).code());
      assertTrue(runs.active().isEmpty());
      var command = command();
      assertEquals(publication, runs.start(command, "maintainer").basePublicationId());
      drive(command.runId(), RunState.READY);
      var first =
          OWNERS.analysis.operations.values().stream()
              .filter(o -> o.command.get("selection").get("officialId").stringValue().equals("M1"))
              .findFirst()
              .orElseThrow();
      assertEquals(revision.toString(), first.command.get("priorRevisionIds").get(0).stringValue());
      assertEquals(
          publication,
          jdbc.queryForObject(
              "select publication_id from coordinator.current_publication where id=1", UUID.class));
    } finally {
      jdbc.update(
          "update coordinator.current_publication set publication_id=? where id=1",
          previousPointer);
    }
  }

  @Test
  void missingOwnerConfigurationDoesNotTakeTheRunSlotOrSaveAnAcceptance() {
    var unconfigured =
        new Runs(
            entities,
            manager,
            json,
            settings,
            new CoordinatorRuntime(new org.springframework.mock.env.MockEnvironment()),
            clock);
    var command = command();
    var error = assertThrows(RunProblem.class, () -> unconfigured.start(command, "maintainer"));
    assertEquals(503, error.status());
    assertEquals("COORDINATOR_NOT_CONFIGURED", error.code());
    assertTrue(runs.active().isEmpty());
    assertEquals(
        0,
        jdbc.queryForObject(
            "select count(*) from coordinator.run where id=?", Integer.class, command.runId()));
  }

  @Test
  void driverIsAbsentByDefaultAndNothingPollsOwnersOnStart() {
    assertTrue(context.getBeansOfType(RunDriver.class).isEmpty());
    runs.start(command(), "maintainer");
    assertTrue(OWNERS.records.requests.isEmpty());
    assertTrue(OWNERS.analysis.requests.isEmpty());
  }

  @Test
  void twoMeetingsCanPublishTheSameDocumentVersion() {
    var c = command();
    runs.start(c, "maintainer");
    drive(c.runId(), RunState.READY);
    UUID document = UUID.randomUUID(), version = UUID.randomUUID();
    // Seed shared retained references and matching synthetic revision evidence.
    jdbc.update(
        "update coordinator.run_input set document_id=?,version_id=? where run_id=?",
        document,
        version,
        c.runId());
    for (var operation : OWNERS.analysis.operations.values()) {
      for (var pin : operation.command.get("selection").get("inputs")) {
        var value = (tools.jackson.databind.node.ObjectNode) pin;
        value.put("documentId", document.toString());
        value.put("versionId", version.toString());
      }
    }
    assertEquals(RunState.PUBLISHED, runs.publish(c.runId()).state());
    var manifest = publications.current().orElseThrow();
    var analyzed =
        manifest.meetings().stream()
            .filter(m ->
                m.disposition() == org.civictracker.coordinator.publication.Disposition.ANALYZED)
            .toList();
    assertEquals(2, analyzed.size());
    for (var meeting : analyzed) {
      assertEquals(
          List.of(new org.civictracker.coordinator.publication.SourceVersion(document, version)),
          meeting.sources());
    }
  }

  @Test
  void manualPublishChecksRevisionsAndCarriesForwardThePreviousCollection() {
    assertTrue(publications.current().isEmpty());
    var first = command();
    runs.start(first, "maintainer");
    drive(first.runId(), RunState.READY);
    assertTrue(publications.current().isEmpty());
    assertEquals(RunState.PUBLISHED, runs.publish(first.runId()).state());
    var manifest = publications.current().orElseThrow();
    assertEquals(3, manifest.meetings().size());
    assertEquals(manifest, publications.byId(manifest.publicationId()).orElseThrow());
    assertEquals(RunState.PUBLISHED, runs.publish(first.runId()).state());
    assertTrue(runs.active().isEmpty());
    OWNERS.reset();
    OWNERS.records.meetingCount = 0;
    var second =
        new StartRun(
            UUID.randomUUID(),
            "synthetic",
            LocalDate.of(2026, 2, 1),
            LocalDate.of(2026, 2, 28),
            List.of("council"));
    runs.start(second, "maintainer");
    drive(second.runId(), RunState.READY);
    runs.publish(second.runId());
    var next = publications.current().orElseThrow();
    assertEquals(manifest.publicationId(), next.previousPublicationId());
    assertEquals(manifest.meetings(), next.meetings());
    assertEquals(2, next.coverage().size());
  }

  @Test
  void publicationMismatchHaltsWithoutCreatingAPublication() {
    for (String problem : List.of("source", "meeting", "date")) {
      OWNERS.reset();
      var c = command();
      runs.start(c, "maintainer");
      drive(c.runId(), RunState.READY);
      OWNERS.analysis.revisionProblem = problem;
      assertEquals("PUBLISH_CHECK_FAILED", runs.publish(c.runId()).halt().code());
      assertTrue(publications.current().isEmpty());
      assertTrue(publications.byId(RunPlan.publicationId(c.runId())).isEmpty());
      runs.abandon(c.runId(), "Synthetic reset", "maintainer");
    }
  }

  @Test
  void automaticPublicationUsesTheSameChecksAndManualPublishRemainsIdempotent() {
    var automatic =
        new CoordinatorRuntime(environment) {
          @Override
          public boolean automaticPublish() {
            return true;
          }
        };
    var driver = new Runs(entities, manager, json, settings, automatic, clock);
    var c = command();
    driver.start(c, "maintainer");
    for (int n = 0; n < 50 && driver.view(c.runId()).state() != RunState.PUBLISHED; n++) {
      driver.tick();
    }
    assertEquals(RunState.PUBLISHED, driver.view(c.runId()).state());
    assertEquals(RunState.PUBLISHED, driver.publish(c.runId()).state());
  }

  @Test
  void failedPublicationTransactionRollsBackAndCanBeRepeated() throws Exception {
    var c = command();
    runs.start(c, "maintainer");
    drive(c.runId(), RunState.READY);
    String suffix = UUID.randomUUID().toString().replace("-", "");
    String fn = "coordinator.reject_" + suffix;
    try (var connection =
            java.sql.DriverManager.getConnection(
                environment.getProperty("COORDINATOR_DB_URL"),
                environment.getProperty("COORDINATOR_MIGRATION_USER"),
                environment.getProperty("COORDINATOR_MIGRATION_PASSWORD"));
        var statement = connection.createStatement()) {
      statement.execute(
          "create function "
              + fn
              + "() returns trigger language plpgsql as $$ begin raise exception 'synthetic publish"
              + " rollback'; end $$");
      statement.execute(
          "create trigger reject_publish before update on coordinator.run for each"
              + " row execute function "
              + fn
              + "()");
      try {
        assertThrows(RuntimeException.class, () -> runs.publish(c.runId()));
        assertTrue(publications.current().isEmpty());
        assertTrue(publications.byId(RunPlan.publicationId(c.runId())).isEmpty());
        assertEquals(RunState.READY, runs.view(c.runId()).state());
      } finally {
        statement.execute("drop trigger reject_publish on coordinator.run");
        statement.execute("drop function " + fn + "()");
      }
    }
    assertEquals(RunState.PUBLISHED, runs.publish(c.runId()).state());
  }

  @Test
  void manualSkipKeepsItsReasonAndSourcesAndExcludesTheRevisionFromLaterPriors() {
    OWNERS.analysis.failKind = StepKind.ANALYZE;
    var c = command();
    runs.start(c, "maintainer");
    var halted = drive(c.runId(), RunState.HALTED);
    assertEquals(
        400,
        assertThrows(
                RunProblem.class,
                () -> runs.skip(c.runId(), halted.halt().haltId(), " ", "maintainer"))
            .status());
    runs.skip(c.runId(), halted.halt().haltId(), "Unsupported synthetic input", "maintainer");
    runs.skip(c.runId(), halted.halt().haltId(), "Repeated request", "maintainer");
    drive(c.runId(), RunState.READY);
    runs.publish(c.runId());
    var manifest = publications.current().orElseThrow();
    var skipped = manifest.meetings().getFirst();
    assertEquals(
        org.civictracker.coordinator.publication.Disposition.SKIPPED, skipped.disposition());
    assertNull(skipped.revisionId());
    assertEquals("Unsupported synthetic input", skipped.skipReason());
    assertEquals(2, skipped.sources().size());
    var later =
        OWNERS.analysis.operations.values().stream()
            .filter(o -> o.command.get("selection").get("officialId").stringValue().equals("M3"))
            .findFirst()
            .orElseThrow();
    assertTrue(later.command.get("priorRevisionIds").isEmpty());
    assertEquals(
        1,
        jdbc.queryForObject(
            "select count(*) from coordinator.run_event where run_id=? and kind='SKIPPED'",
            Integer.class,
            c.runId()));
    assertEquals(
        "maintainer",
        jdbc.queryForObject(
            "select skipped_by from coordinator.run_meeting where run_id=? and ordinal=0",
            String.class,
            c.runId()));
  }

  @Test
  void movedPointerRollsBackTheNewManifestAndHalts() {
    var first = command();
    runs.start(first, "maintainer");
    drive(first.runId(), RunState.READY);
    runs.publish(first.runId());
    OWNERS.reset();
    OWNERS.records.meetingCount = 0;
    var next =
        new StartRun(
            UUID.randomUUID(),
            "synthetic",
            LocalDate.of(2026, 2, 1),
            LocalDate.of(2026, 2, 28),
            List.of("council"));
    runs.start(next, "maintainer");
    drive(next.runId(), RunState.READY);
    jdbc.update("update coordinator.current_publication set publication_id=null where id=1");
    assertEquals("POINTER_MOVED", runs.publish(next.runId()).halt().code());
    assertTrue(publications.byId(RunPlan.publicationId(next.runId())).isEmpty());
    assertTrue(publications.current().isEmpty());
  }

  static void await(CountDownLatch latch) {
    try {
      if (!latch.await(10, TimeUnit.SECONDS)) {
        throw new AssertionError("Fixture wait expired");
      }
    } catch (InterruptedException interrupted) {
      Thread.currentThread().interrupt();
      throw new AssertionError(interrupted);
    }
  }
}
