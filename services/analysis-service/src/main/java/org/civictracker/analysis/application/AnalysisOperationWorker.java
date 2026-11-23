package org.civictracker.analysis.application;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import org.civictracker.analysis.api.AnalysisCommands.Command;
import org.civictracker.analysis.configuration.AnalysisRuntime;
import org.civictracker.analysis.evaluation.EvaluationJson;
import org.civictracker.analysis.evaluation.EvaluationRunner;
import org.civictracker.analysis.model.ModelClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.SmartLifecycle;
import org.springframework.stereotype.Component;

/** Executes one durable analysis attempt without automatic retries or takeover. */
@Component
public class AnalysisOperationWorker implements SmartLifecycle {
  private static final System.Logger LOG =
      System.getLogger(AnalysisOperationWorker.class.getName());
  private final AnalysisOperations operations;
  private final PriorContext contexts;
  private final AnalysisRuntime runtime;
  private final ModelClient model;
  private final Path storage;
  private final boolean enabled;
  private final long pollMillis;
  private volatile boolean running;
  private ScheduledExecutorService executor;

  public AnalysisOperationWorker(
      AnalysisOperations operations,
      PriorContext contexts,
      AnalysisRuntime runtime,
      ModelClient model,
      @Value("${analysis.storage-root}") String storage,
      @Value("${analysis.operations.worker-enabled:false}") boolean enabled,
      @Value("${analysis.operations.poll-millis:500}") long pollMillis) {
    this.operations = operations;
    this.contexts = contexts;
    this.runtime = runtime;
    this.model = model;
    this.storage = Path.of(storage);
    this.enabled = enabled;
    this.pollMillis = pollMillis;
    if (pollMillis < 1)
      throw new IllegalArgumentException("Worker polling interval must be positive");
  }

  public boolean processNext() {
    var operation = operations.claim();
    if (operation == null) return false;
    String key = "operations/" + operation.id + "/attempt-" + operation.attempts;
    Path folder = storage.resolve(key);
    boolean evaluating = false;
    boolean saving = false;
    try {
      var command = EvaluationJson.MAPPER.readValue(operation.inputJson, Command.class);
      var context = contexts.build(command.priorRevisionIds(), command.selection().meetingDate());
      var input = runtime.reader().readPinned(command.selection().pinned(context));
      Files.createDirectories(folder.getParent());
      evaluating = true;
      var state =
          new EvaluationRunner(model)
              .evaluate(
                  input,
                  runtime.outputTokens(),
                  runtime.seed(),
                  runtime.sampling(),
                  folder,
                  folder.resolve("admission.lock"));
      if (unresolved(folder)) operations.park(operation.id, operation.attemptId);
      else if (state == ModelClient.State.SUCCESS) {
        saving = true;
        operations.succeed(
            operation.id, operation.attemptId, TrialArtifacts.read(folder), input, context, key);
      } else operations.fail(operation.id, operation.attemptId, state.name());
    } catch (IOException | RuntimeException | InterruptedException error) {
      LOG.log(
          System.Logger.Level.WARNING,
          "Analysis operation {0} stopped with {1}",
          operation.id,
          error.getClass().getSimpleName());
      if (evaluating && unresolved(folder)) operations.park(operation.id, operation.attemptId);
      else
        operations.fail(
            operation.id,
            operation.attemptId,
            saving
                ? error instanceof SaveRejected rejected ? rejected.code().name() : "SAVE_FAILED"
                : evaluating ? "RUNTIME_FAILED" : "INPUT_READ_FAILED");
      if (error instanceof InterruptedException) Thread.currentThread().interrupt();
    }
    return true;
  }

  private boolean unresolved(Path folder) {
    try {
      var receipt =
          EvaluationJson.MAPPER.readTree(Files.readAllBytes(folder.resolve("receipt.json")));
      return receipt.path("runtimeReconciliationRequired").asBoolean()
          || receipt.path("state").asText().equals("UNCERTAIN_EXECUTION");
    } catch (IOException | RuntimeException error) {
      return true;
    }
  }

  @Override
  public synchronized void start() {
    if (!enabled || running) return;
    running = true;
    executor =
        Executors.newSingleThreadScheduledExecutor(
            Thread.ofPlatform().daemon().name("analysis-operation-worker").factory());
    executor.scheduleWithFixedDelay(
        () -> {
          try {
            processNext();
          } catch (RuntimeException error) {
            LOG.log(
                System.Logger.Level.WARNING,
                "Analysis worker cannot reconcile durable state: {0}",
                error.getClass().getSimpleName());
          } catch (Error error) {
            LOG.log(
                System.Logger.Level.ERROR,
                "Analysis worker stopped; claimed operations stay RUNNING");
            throw error;
          }
        },
        0,
        pollMillis,
        TimeUnit.MILLISECONDS);
  }

  @Override
  public synchronized void stop() {
    running = false;
    if (executor != null) {
      executor.shutdownNow();
      try {
        executor.awaitTermination(5, TimeUnit.SECONDS);
      } catch (InterruptedException error) {
        Thread.currentThread().interrupt();
      }
    }
  }

  @Override
  public boolean isRunning() {
    return running;
  }
}
