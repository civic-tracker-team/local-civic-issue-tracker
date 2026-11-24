package org.civictracker.analysis;

import static org.junit.jupiter.api.Assertions.*;

import jakarta.persistence.EntityManager;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.civictracker.analysis.api.AnalysisCommands.*;
import org.civictracker.analysis.application.*;
import org.civictracker.analysis.persistence.AnalysisOperation.State;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.PlatformTransactionManager;

@SpringBootTest(
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
class AnalysisOperationsTests {
  static final OperationFixture RECORDS = new OperationFixture();

  @DynamicPropertySource
  static void properties(DynamicPropertyRegistry registry) {
    registry.add("analysis.records.base-url", RECORDS::base);
  }

  @AfterAll
  static void close() {
    RECORDS.close();
  }

  @TestConfiguration
  static class Config {
    @Bean
    @Primary
    OperationFixture.FakeModel fakeModel() {
      return new OperationFixture.FakeModel();
    }
  }

  @Autowired AnalysisOperations operations;
  @Autowired AnalysisOperationWorker worker;
  @Autowired OperationFixture.FakeModel model;
  @Autowired FindingsStore store;
  @Autowired TrialImporter importer;
  @Autowired JdbcTemplate jdbc;
  @Autowired EntityManager entities;
  @Autowired PriorContext contexts;
  @Autowired PlatformTransactionManager manager;
  @Autowired org.springframework.core.env.Environment environment;
  final List<UUID> ids = new ArrayList<>();

  @BeforeEach
  void reset() {
    model.mode = "SUCCESS";
    model.contentOverride = null;
    model.calls.set(0);
    RECORDS.unavailable = false;
  }

  @AfterEach
  void finish() {
    RECORDS.unavailable = false;
    model.contentOverride = null;
    for (UUID id : ids) {
      var status = operations.status(id);
      if (status.state() == State.RUNNING)
        operations.resume(id, new Resume(status.attemptId(), status.workerId(), true, true));
    }
    model.mode = "SUCCESS";
    while (worker.processNext()) {}
  }

  Command accept() {
    var c = RECORDS.command();
    operations.accept(c);
    ids.add(c.id());
    return c;
  }

  @Test
  void saveRejectionKeepsConflictingLinkBasisRatherThanRuntimeFailed() throws Exception {
    var source = RECORDS.input;
    var earlier =
        new org.civictracker.analysis.domain.MeetingInput(
            source.meetingId(),
            source.officialId(),
            source.meetingDate().minusDays(1),
            source.title(),
            source.sources(),
            List.of());
    var raw =
        org.civictracker.analysis.evaluation.EvaluationJson.MAPPER
            .readTree(OperationFixture.resource("attempt.json"))
            .path("content")
            .asText();
    var interpreted =
        assertInstanceOf(
            org.civictracker.analysis.model.Interpretation.Interpreted.class,
            org.civictracker.analysis.model.ModelContract.interpret(
                raw, org.civictracker.analysis.domain.CitationIndex.build(earlier), earlier));
    assertTrue(interpreted.accepted());
    var fixture = new FindingsStoreTests.Fixture();
    UUID prior =
        store.save(
            earlier,
            interpreted.candidates(),
            PriorContext.Snapshot.empty(),
            fixture.metadata,
            DevelopmentKinds.from(interpreted));
    var base = RECORDS.command();
    var command = new Command(base.id(), base.selection(), List.of(prior));
    operations.accept(command);
    ids.add(command.id());
    model.contentOverride =
        """
        {"issues":[
          {"id":"i1","matterType":"PROJECT","name":"Elm bridge repair","priorIssueId":"p1"},
          {"id":"i2","matterType":"PROJECT","name":"Elm bridge project","priorIssueId":"p1"}],
         "developments":[{"finding":{"kind":"PROPOSAL","proposedBy":"STAFF","planned":[]},
          "does":"repair the Elm bridge","evidence":[{"firstLine":1,"lastLine":1}],
          "links":[{"issueId":"i1","basis":"SOURCE_STATED","evidence":[{"firstLine":1,"lastLine":1}]},
                   {"issueId":"i2","basis":"INFERRED","evidence":[{"firstLine":1,"lastLine":1}]}]}]}
        """;
    long before = jdbc.queryForObject("select count(*) from analysis.meeting_analysis", Long.class);
    assertTrue(worker.processNext());
    var result = operations.status(command.id());
    assertEquals(State.FAILED, result.state());
    assertEquals("CONFLICTING_LINK_BASIS", result.errorCode());
    assertNull(result.revisionId());
    assertEquals(
        before, jdbc.queryForObject("select count(*) from analysis.meeting_analysis", Long.class));
    assertFalse(worker.processNext());
    assertEquals(
        0,
        jdbc.queryForObject(
            "select count(*) from analysis.operation_slot where operation_id is not null",
            Integer.class));
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "SUCCESS",
        "INVALID_OUTPUT",
        "CAPACITY_REJECTED",
        "RUNTIME_FAILED",
        "UNCERTAIN_EXECUTION",
        "PREFLIGHT_FAILURE",
        "LOST_RESPONSE"
      })
  void mapsEveryRunnerOutcomeAndKeepsOnlyUncertainExecutionsBlocked(String mode) throws Exception {
    model.mode = mode;
    var c = accept();
    int before =
        jdbc.queryForObject(
            "select count(*) from analysis.meeting_analysis where origin='OPERATION'",
            Integer.class);
    assertTrue(worker.processNext());
    var result = operations.status(c.id());
    boolean blocked = mode.equals("UNCERTAIN_EXECUTION") || mode.equals("LOST_RESPONSE");
    assertEquals(
        blocked ? State.RUNNING : mode.equals("SUCCESS") ? State.SUCCEEDED : State.FAILED,
        result.state());
    assertEquals(blocked ? result.attemptId() : null, result.blockedAttemptId());
    assertEquals(
        blocked,
        java.nio.file.Files.exists(
            Path.of(System.getenv("ANALYSIS_STORAGE_ROOT"))
                .resolve("operations/" + c.id() + "/attempt-1/admission.lock")));
    assertEquals(
        blocked ? 1 : 0,
        jdbc.queryForObject(
            "select count(*) from analysis.operation_slot where operation_id is not null",
            Integer.class));
    if (mode.equals("SUCCESS")) {
      assertEquals(1, store.read(result.revisionId()).developments().size());
      var imported =
          importer.importTrial(
              Path.of(getClass().getResource("/import-trial/synthetic/receipt.json").toURI())
                  .getParent());
      assertEquals(
          store.read(imported).profileFingerprint(),
          store.read(result.revisionId()).profileFingerprint());
    } else if (!blocked)
      assertEquals(mode.equals("PREFLIGHT_FAILURE") ? "RUNTIME_FAILED" : mode, result.errorCode());
    assertEquals(
        before + (mode.equals("SUCCESS") ? 1 : 0),
        jdbc.queryForObject(
            "select count(*) from analysis.meeting_analysis where origin='OPERATION'",
            Integer.class));
    assertFalse(worker.processNext());
    assertEquals(0, RECORDS.badCredentials.get());
  }

  @Test
  void identicalCommandReplaysAndChangedCommandConflicts() {
    var c = accept();
    assertEquals(operations.status(c.id()), operations.accept(c));
    var different = RECORDS.command();
    assertEquals(
        409,
        assertThrows(
                OperationProblem.class,
                () ->
                    operations.accept(
                        new Command(c.id(), different.selection(), List.of(UUID.randomUUID()))))
            .status());
    assertEquals(
        1,
        jdbc.queryForObject(
            "select count(*) from analysis.analysis_operation where id=?", Integer.class, c.id()));
  }

  @Test
  void rejectsBadPriorRevisionsBeforeCreatingAnOperation() {
    var unknown = RECORDS.command();
    assertEquals(
        400,
        assertThrows(
                OperationProblem.class,
                () ->
                    operations.accept(
                        new Command(unknown.id(), unknown.selection(), List.of(UUID.randomUUID()))))
            .status());
    assertEquals(
        0,
        jdbc.queryForObject(
            "select count(*) from analysis.analysis_operation where id=?",
            Integer.class,
            unknown.id()));
  }

  @Test
  void onlyOneClaimAndARecreatedWorkerCannotTakeOver() {
    var first = accept();
    var next = accept();
    var claim = operations.claim();
    assertEquals(first.id(), claim.id);
    assertNull(operations.claim());
    var restarted = new AnalysisOperations(entities, jdbc, manager, contexts, store);
    assertNull(restarted.claim());
    var blocked = restarted.status(first.id());
    assertEquals(claim.attemptId, blocked.blockedAttemptId());
    assertThrows(
        OperationProblem.class,
        () ->
            restarted.resume(first.id(), new Resume(claim.attemptId, claim.workerId, false, true)));
    restarted.resume(first.id(), new Resume(claim.attemptId, claim.workerId, true, true));
    assertEquals(State.ACCEPTED, operations.status(first.id()).state());
    assertTrue(worker.processNext());
    assertEquals(2, operations.status(first.id()).attempts());
    assertTrue(worker.processNext());
    assertEquals(State.SUCCEEDED, operations.status(next.id()).state());
  }

  @Test
  void uncertainAttemptRequiresExplicitRuntimeConfirmationAndGetsANewAttempt() {
    model.mode = "UNCERTAIN_EXECUTION";
    var first = accept();
    worker.processNext();
    var blocked = operations.status(first.id());
    var next = accept();
    assertFalse(worker.processNext());
    assertEquals(
        "RUNTIME_RECONCILIATION_REQUIRED",
        assertThrows(
                OperationProblem.class,
                () ->
                    operations.resume(
                        first.id(),
                        new Resume(blocked.attemptId(), blocked.workerId(), true, false)))
            .code());
    operations.resume(first.id(), new Resume(blocked.attemptId(), blocked.workerId(), true, true));
    model.mode = "SUCCESS";
    worker.processNext();
    assertNotEquals(blocked.attemptId(), operations.status(first.id()).attemptId());
    assertEquals(2, operations.status(first.id()).attempts());
    worker.processNext();
    assertEquals(State.SUCCEEDED, operations.status(next.id()).state());
  }

  @Test
  void failedOperationWaitsForManualResumeAndInputFailureReleasesSlot() {
    RECORDS.unavailable = true;
    var c = accept();
    worker.processNext();
    var failed = operations.status(c.id());
    assertEquals(State.FAILED, failed.state());
    assertEquals("INPUT_READ_FAILED", failed.errorCode());
    assertFalse(worker.processNext());
    operations.resume(c.id(), new Resume(failed.attemptId(), failed.workerId(), true, false));
    RECORDS.unavailable = false;
    worker.processNext();
    assertEquals(State.SUCCEEDED, operations.status(c.id()).state());
  }

  @Test
  void findingsFailureRollsBackTheRevisionAndEndsTheOperationWithoutAResult() throws Exception {
    var c = accept();
    String suffix = UUID.randomUUID().toString().replace("-", "");
    String function = "analysis.fail_operation_" + suffix;
    String trigger = "fail_operation_" + suffix;
    long before = jdbc.queryForObject("select count(*) from analysis.meeting_analysis", Long.class);
    try (var connection =
            java.sql.DriverManager.getConnection(
                environment.getProperty("ANALYSIS_DB_URL"),
                environment.getProperty("ANALYSIS_MIGRATION_USER"),
                environment.getProperty("ANALYSIS_MIGRATION_PASSWORD"));
        var sql = connection.createStatement()) {
      sql.execute(
          "create function "
              + function
              + "() returns trigger language plpgsql as $$ begin raise exception 'synthetic"
              + " findings failure'; end $$");
      sql.execute(
          "create trigger "
              + trigger
              + " before insert on analysis.development for each row execute function "
              + function
              + "()");
      try {
        worker.processNext();
        var failed = operations.status(c.id());
        assertEquals(State.FAILED, failed.state());
        assertNull(failed.revisionId());
        assertEquals(
            before,
            jdbc.queryForObject("select count(*) from analysis.meeting_analysis", Long.class));
        assertFalse(worker.processNext());
      } finally {
        sql.execute("drop trigger " + trigger + " on analysis.development");
        sql.execute("drop function " + function + "()");
      }
    }
  }

  @Test
  void activeOrStaleAttemptsCannotBeResumedOrCompleted() {
    var c = accept();
    var first = operations.claim();
    assertThrows(
        OperationProblem.class,
        () -> operations.resume(c.id(), new Resume(first.attemptId, first.workerId, true, true)));
    operations.fail(c.id(), first.attemptId, "RUNTIME_FAILED");
    operations.resume(c.id(), new Resume(first.attemptId, first.workerId, true, false));
    var second = operations.claim();
    assertThrows(
        OperationProblem.class, () -> operations.fail(c.id(), first.attemptId, "RUNTIME_FAILED"));
    assertEquals(second.attemptId, operations.status(c.id()).attemptId());
    operations.fail(c.id(), second.attemptId, "RUNTIME_FAILED");
  }
}
