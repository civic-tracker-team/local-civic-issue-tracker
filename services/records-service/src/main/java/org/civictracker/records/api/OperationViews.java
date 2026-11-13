package org.civictracker.records.api;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.civictracker.records.domain.AcquisitionData.Kind;
import org.civictracker.records.domain.AcquisitionData.Meeting;
import org.civictracker.records.domain.AcquisitionData.Receipt;
import org.civictracker.records.domain.AcquisitionData.State;
import org.civictracker.records.domain.AcquisitionData.Target;

/** Commands carry owner references, never caller-selected network or file locations. */
public final class OperationViews {
  private OperationViews() {}

  public record DiscoveryCommand(
      UUID operationId,
      String sourceId,
      LocalDate from,
      LocalDate to,
      List<String> committeeIds,
      UUID replacesOperationId) {}

  public record AcquisitionCommand(
      UUID operationId, UUID discoveryId, UUID meetingId, UUID runId, UUID replacesOperationId) {}

  public record Resume(UUID attemptId, UUID previousWorkerId, boolean previousWorkerStopped) {}

  public record Status(
      UUID id,
      Kind kind,
      State state,
      Instant acceptedAt,
      Instant startedAt,
      Instant completedAt,
      UUID attemptId,
      UUID workerId,
      int attempts,
      String errorCode,
      UUID replacesOperationId,
      String resultUrl,
      List<String> notes) {}

  public record Discovered(UUID id, Meeting source, Instant observedAt) {}

  public record Original(UUID id, Target source, Receipt retained) {}

  public record Page<T>(List<T> items, int page, int size, boolean hasNext) {
    public Page {
      items = List.copyOf(items);
    }
  }
}
