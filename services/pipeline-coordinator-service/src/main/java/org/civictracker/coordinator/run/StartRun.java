package org.civictracker.coordinator.run;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record StartRun(
    UUID runId, String sourceId, LocalDate from, LocalDate to, List<String> committeeIds) {
  public StartRun {
    committeeIds = List.copyOf(committeeIds);
  }
}
