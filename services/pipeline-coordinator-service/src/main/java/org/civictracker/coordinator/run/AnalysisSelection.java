package org.civictracker.coordinator.run;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record AnalysisSelection(
    UUID discoveryId,
    UUID acquisitionId,
    UUID preparationOperationId,
    UUID meetingId,
    String officialId,
    LocalDate meetingDate,
    String title,
    List<Pin> inputs) {
  public AnalysisSelection {
    inputs = List.copyOf(inputs);
  }
}
