package org.civictracker.coordinator;

import static org.junit.jupiter.api.Assertions.*;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import java.util.function.Predicate;
import org.civictracker.coordinator.run.RunState;
import org.civictracker.coordinator.run.RunView;
import org.civictracker.coordinator.run.Runs;
import org.civictracker.coordinator.run.StartRun;
import org.civictracker.coordinator.run.StepKind;
import org.civictracker.coordinator.run.WaitingState;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

@SpringBootTest(
    properties = {
      "coordinator.driver.enabled=true",
      "coordinator.driver.delay=PT0.05S",
      "coordinator.preparation-profile-id=synthetic-profile",
      "coordinator.stall-after=PT1M",
      "coordinator.records.username=owner",
      "coordinator.records.password=synthetic-password",
      "coordinator.records.timeout-seconds=5",
      "coordinator.analysis.username=owner",
      "coordinator.analysis.password=synthetic-password",
      "coordinator.analysis.timeout-seconds=5"
    })
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@Timeout(40)
class RunDriverIntegrationTests {
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
  @Autowired org.springframework.core.env.ConfigurableEnvironment environment;
  UUID originalPointer;

  @org.junit.jupiter.api.BeforeEach
  void reset() {
    OWNERS.reset();
    originalPointer =
        jdbc.queryForObject(
            "select publication_id from coordinator.current_publication where id=1", UUID.class);
  }

  @Autowired JdbcTemplate jdbc;

  @AfterEach
  void cleanup() {
    runs.active()
        .ifPresent(r -> runs.abandon(r.runId(), "Scheduled fixture cleanup", "maintainer"));
    jdbc.update(
        "update coordinator.current_publication set publication_id=? where id=1", originalPointer);
    environment.getPropertySources().remove("automatic-test");
    assertNull(OWNERS.records.failure.get());
    assertNull(OWNERS.analysis.failure.get());
  }

  @Test
  void enabledSchedulerContinuesAfterOwnerSideResumeWithoutManualTicks() throws Exception {
    OWNERS.records.holdKind = StepKind.PREPARE;
    OWNERS.records.blocked = true;
    var command =
        new StartRun(
            UUID.randomUUID(),
            "synthetic",
            LocalDate.of(2026, 1, 1),
            LocalDate.of(2026, 1, 31),
            List.of("council"));
    runs.start(command, "maintainer");
    var blocked =
        await(
            command.runId(),
            r -> r.waiting() != null && r.waiting().state() == WaitingState.OWNER_BLOCKED);
    assertEquals(RunState.ACTIVE, blocked.state());
    assertNull(blocked.halt());
    var operation = OWNERS.records.latest(StepKind.PREPARE);
    OWNERS.records.holdKind = null;
    operation.blocked = null;
    operation.state = "SUCCEEDED";
    assertEquals(RunState.READY, await(command.runId(), r -> r.state() == RunState.READY).state());
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
  void enabledSchedulerPublishesAutomaticallyAndUsesTheSameRejectionCheck() throws Exception {
    environment
        .getPropertySources()
        .addFirst(
            new org.springframework.core.env.MapPropertySource(
                "automatic-test", java.util.Map.of("coordinator.publish.automatic", true)));
    for (boolean bad : List.of(false, true)) {
      OWNERS.reset();
      if (bad) {
        OWNERS.analysis.revisionProblem = "source";
      }
      var c =
          new StartRun(
              UUID.randomUUID(),
              "synthetic",
              LocalDate.of(2026, 1, 1),
              LocalDate.of(2026, 1, 31),
              List.of("council"));
      runs.start(c, "maintainer");
      var result =
          await(c.runId(), r -> r.state() == RunState.PUBLISHED || r.state() == RunState.HALTED);
      if (bad) {
        assertEquals("PUBLISH_CHECK_FAILED", result.halt().code());
        assertNull(
            jdbc.queryForObject(
                "select publication_id from coordinator.current_publication where id=1",
                UUID.class));
        runs.abandon(c.runId(), "Synthetic reset", "maintainer");
      } else {
        assertEquals(RunState.PUBLISHED, result.state());
        assertEquals(RunState.PUBLISHED, runs.publish(c.runId()).state());
        jdbc.update("update coordinator.current_publication set publication_id=null where id=1");
      }
    }
  }

  private RunView await(UUID id, Predicate<RunView> condition) throws InterruptedException {
    long deadline = System.nanoTime() + java.util.concurrent.TimeUnit.SECONDS.toNanos(15);
    while (System.nanoTime() < deadline) {
      var view = runs.view(id);
      if (condition.test(view)) {
        return view;
      }
      assertTrue(
          view.state() == RunState.ACTIVE || view.state() == RunState.READY,
          () -> "Unexpected halt: " + view.halt());
      Thread.sleep(25);
    }
    fail("Scheduled run did not reach the expected state");
    return null;
  }
}
