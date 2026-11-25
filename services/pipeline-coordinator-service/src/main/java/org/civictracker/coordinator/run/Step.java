package org.civictracker.coordinator.run;

import java.util.List;
import java.util.UUID;
import org.civictracker.coordinator.publication.Manifest;

public sealed interface Step {
  StepKind kind();

  record Discover(int attempt) implements Step {
    public StepKind kind() {
      return StepKind.DISCOVER;
    }
  }

  record Acquire(PlannedMeeting meeting, int attempt) implements Step {
    public StepKind kind() {
      return StepKind.ACQUIRE;
    }
  }

  record Prepare(PlannedMeeting meeting, int attempt, int acquiredAttempt) implements Step {
    public StepKind kind() {
      return StepKind.PREPARE;
    }
  }

  record Analyze(
      PlannedMeeting meeting,
      int attempt,
      AnalysisSelection selection,
      List<UUID> priorRevisionIds,
      FrozenAnalysisRequest frozenRequest)
      implements Step {
    public Analyze {
      priorRevisionIds = List.copyOf(priorRevisionIds);
    }

    public Analyze(
        PlannedMeeting meeting, int attempt, AnalysisSelection selection, List<UUID> priors) {
      this(meeting, attempt, selection, priors, null);
    }

    public StepKind kind() {
      return StepKind.ANALYZE;
    }
  }

  /** A complete manifest candidate. The run writer records READY before publication. */
  record Publish(Manifest manifest) implements Step {
    public StepKind kind() {
      return StepKind.PUBLISH;
    }
  }
}
