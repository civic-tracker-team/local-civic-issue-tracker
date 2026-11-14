package org.civictracker.records.application;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import org.civictracker.records.domain.AcquisitionData.Kind;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.SmartLifecycle;
import org.springframework.stereotype.Component;

/** Uses durable owner state; an interrupted execution is not reclaimed automatically. */
@Component
public class RecordsOperationWorker implements SmartLifecycle {
  private static final System.Logger LOG = System.getLogger(RecordsOperationWorker.class.getName());
  private final RecordsOperations operations;
  private final RecordDiscovery discovery;
  private final RecordAcquisition acquisition;
  private final RecordPreparation preparation;
  private final boolean enabled;
  private final long pollMillis;
  private volatile boolean running;
  private ScheduledExecutorService executor;

  public RecordsOperationWorker(
      RecordsOperations operations,
      RecordDiscovery discovery,
      RecordAcquisition acquisition,
      RecordPreparation preparation,
      @Value("${records.operations.worker-enabled:false}") boolean enabled,
      @Value("${records.operations.poll-millis:500}") long pollMillis) {
    this.operations = operations;
    this.discovery = discovery;
    this.acquisition = acquisition;
    this.preparation = preparation;
    this.enabled = enabled;
    this.pollMillis = pollMillis;
    if (pollMillis < 1) {
      throw new IllegalArgumentException("Worker polling interval must be positive");
    }
  }

  /** Executes at most one accepted operation, including when called by integration tests. */
  public boolean processNext() {
    var operation = operations.claim();
    if (operation == null) {
      return false;
    }
    String failure = null;
    try {
      switch (operation.kind) {
        case DISCOVERY -> discovery.execute(operation);
        case ACQUISITION -> acquisition.execute(operation);
        case PREPARATION -> preparation.execute(operation);
      }
      if (Thread.currentThread().isInterrupted()) {
        return true;
      }
    } catch (InterruptedException error) {
      parkIfUnresolved(operation);
      Thread.currentThread().interrupt();
      return true;
    } catch (OperationProblem error) {
      failure = error.code();
    } catch (java.io.IOException error) {
      failure = "SOURCE_TRANSFER_FAILED";
    } catch (RuntimeException error) {
      LOG.log(
          System.Logger.Level.WARNING,
          "Operation {0} stopped with {1}",
          operation.id,
          error.getClass().getSimpleName());
      failure = "OPERATION_EXECUTION_FAILED";
    }
    if (!parkIfUnresolved(operation)) {
      operations.complete(operation.id, operation.attemptId, failure);
    }
    return true;
  }

  private boolean parkIfUnresolved(org.civictracker.records.persistence.RecordOperation operation) {
    if (operation.kind != Kind.PREPARATION) {
      return false;
    }
    var execution = preparation.unresolved(operation.id);
    if (execution == null) {
      return false;
    }
    operations.park(operation.id, operation.attemptId, execution);
    return true;
  }

  @Override
  public synchronized void start() {
    if (!enabled || running) {
      return;
    }
    running = true;
    executor =
        Executors.newSingleThreadScheduledExecutor(
            Thread.ofPlatform().daemon().name("records-operation-worker").factory());
    executor.scheduleWithFixedDelay(
        () -> {
          try {
            processNext();
          } catch (RuntimeException error) {
            LOG.log(
                System.Logger.Level.WARNING,
                "Records worker cannot reconcile durable state: {0}",
                error.getClass().getSimpleName());
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
