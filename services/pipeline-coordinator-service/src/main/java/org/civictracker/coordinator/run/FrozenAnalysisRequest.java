package org.civictracker.coordinator.run;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

/** The saved command bytes and the values from which they were built. */
public record FrozenAnalysisRequest(
    UUID operationId,
    AnalysisSelection selection,
    List<UUID> priorRevisionIds,
    String json,
    String sha256) {
  public FrozenAnalysisRequest {
    Objects.requireNonNull(operationId);
    Objects.requireNonNull(selection);
    priorRevisionIds = List.copyOf(priorRevisionIds);
    Objects.requireNonNull(json);
    Objects.requireNonNull(sha256);
  }
}
