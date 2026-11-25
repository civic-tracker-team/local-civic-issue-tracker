package org.civictracker.coordinator.publication;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record PublishedMeeting(
    UUID meetingId,
    String officialId,
    LocalDate date,
    String title,
    Disposition disposition,
    UUID revisionId,
    List<SourceVersion> sources,
    String skipReason) {
  public PublishedMeeting {
    sources = List.copyOf(sources);
  }
}
