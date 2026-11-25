package org.civictracker.coordinator.publication;

import java.util.List;
import java.util.UUID;
import org.civictracker.coordinator.run.DateRange;

public record Manifest(
    UUID publicationId,
    UUID runId,
    UUID previousPublicationId,
    List<DateRange> coverage,
    List<PublishedMeeting> meetings) {
  public Manifest {
    coverage = List.copyOf(coverage);
    meetings = List.copyOf(meetings);
  }
}
