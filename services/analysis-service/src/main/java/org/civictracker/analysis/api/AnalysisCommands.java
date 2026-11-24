package org.civictracker.analysis.api;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.civictracker.analysis.application.PriorContext;
import org.civictracker.analysis.persistence.AnalysisOperation.State;
import org.civictracker.analysis.records.PreparedMeetingReader;

/** Internal commands and operation status, independent of persistence entities. */
public final class AnalysisCommands {
  private AnalysisCommands() {}

  public record Selection(
      UUID discoveryId,
      UUID acquisitionId,
      UUID preparationOperationId,
      UUID meetingId,
      String officialId,
      LocalDate meetingDate,
      String title,
      List<PreparedMeetingReader.Pin> inputs) {
    public Selection {
      inputs = inputs == null ? null : List.copyOf(inputs);
    }

    public PreparedMeetingReader.Selection pinned(PriorContext.Snapshot context) {
      return new PreparedMeetingReader.Selection(
          discoveryId,
          acquisitionId,
          preparationOperationId,
          meetingId,
          officialId,
          meetingDate,
          title,
          inputs,
          context.issues());
    }
  }

  public record Command(UUID id, Selection selection, List<UUID> priorRevisionIds) {
    public Command {
      priorRevisionIds = priorRevisionIds == null ? null : List.copyOf(priorRevisionIds);
    }
  }

  public record Resume(
      UUID attemptId,
      UUID previousWorkerId,
      boolean previousWorkerStopped,
      boolean previousRuntimeStopped) {}

  public record Status(
      UUID id,
      State state,
      Instant acceptedAt,
      Instant startedAt,
      Instant completedAt,
      UUID attemptId,
      UUID workerId,
      UUID blockedAttemptId,
      String errorCode,
      int attempts,
      UUID revisionId) {}
}
