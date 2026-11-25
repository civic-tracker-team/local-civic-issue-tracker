package org.civictracker.coordinator.owners;

import java.time.LocalDate;
import java.util.Set;
import java.util.UUID;

public record RevisionHeader(
    UUID revisionId, UUID meetingId, LocalDate meetingDate, Set<UUID> evidenceVersionIds) {
  public RevisionHeader {
    evidenceVersionIds = Set.copyOf(evidenceVersionIds);
  }
}
