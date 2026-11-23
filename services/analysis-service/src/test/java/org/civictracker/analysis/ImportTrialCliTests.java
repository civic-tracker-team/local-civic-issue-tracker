package org.civictracker.analysis;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import org.civictracker.analysis.application.AnalysisOperationWorker;
import org.civictracker.analysis.application.AnalysisOperations;
import org.civictracker.analysis.application.ImportTrialCli;
import org.civictracker.analysis.model.ModelClient;
import org.civictracker.analysis.persistence.AnalysisOperation.State;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.event.ContextRefreshedEvent;
import org.springframework.context.support.GenericApplicationContext;

@SpringBootTest
class ImportTrialCliTests {
  @Autowired AnalysisOperations operations;
  static final OperationFixture.FakeModel MODEL = new OperationFixture.FakeModel();

  public static class Initializer
      implements ApplicationContextInitializer<ConfigurableApplicationContext> {
    public void initialize(ConfigurableApplicationContext context) {
      ((GenericApplicationContext) context)
          .registerBean("modelClient", ModelClient.class, () -> MODEL);
      context.addApplicationListener(event -> {
        if (event instanceof ContextRefreshedEvent
            && context.getBean(AnalysisOperationWorker.class).isRunning()) {
          // Give an incorrectly enabled worker time to claim the pre-existing operation.
          long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
          while (MODEL.calls.get() == 0 && System.nanoTime() < deadline) {
            try {
              Thread.sleep(10);
            } catch (InterruptedException error) {
              Thread.currentThread().interrupt();
              break;
            }
          }
        }
      });
    }
  }

  @Test
  void importContextOverridesEnabledWorkerAndLeavesQueuedWorkUntouched() throws Exception {
    try (var records = new OperationFixture()) {
      var command = records.command();
      operations.accept(command);
      MODEL.calls.set(0);
      var settings =
          Map.of(
              "context.initializer.classes", Initializer.class.getName(),
              "analysis.operations.worker-enabled", "true",
              "analysis.records.base-url", records.base(),
              "analysis.records.user", "reader",
              "analysis.records.password", "secret",
              "analysis.records.timeout-seconds", "2",
              "analysis.model.output-tokens", "100");
      var previous = new LinkedHashMap<String, String>();
      try {
        settings.forEach((key, value) -> {
          previous.put(key, System.getProperty(key));
          System.setProperty(key, value);
        });
        var folder =
            Path.of(getClass().getResource("/import-trial/synthetic/receipt.json").toURI())
                .getParent();
        ImportTrialCli.main(new String[] {folder.toString()});
        assertEquals(State.ACCEPTED, operations.status(command.id()).state());
        assertEquals(0, MODEL.calls.get());
      } finally {
        previous.forEach((key, value) -> {
          if (value == null) System.clearProperty(key);
          else System.setProperty(key, value);
        });
        // Remove only this test's unexecuted command from the isolated operation queue.
        cleanup(command.id());
      }
    }
  }

  @Autowired org.springframework.jdbc.core.JdbcTemplate jdbc;

  private void cleanup(java.util.UUID id) {
    jdbc.update(
        "update analysis.operation_slot set operation_id=null,attempt_id=null,worker_id=null where"
            + " operation_id=?",
        id);
    if (operations.status(id).revisionId() == null)
      jdbc.update("delete from analysis.analysis_operation where id=?", id);
  }
}
