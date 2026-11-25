package org.civictracker.coordinator.run;

import java.util.List;
import java.util.UUID;
import org.civictracker.coordinator.publication.PublishedMeeting;

public record PublicationBase(
    UUID publicationId, List<PublishedMeeting> meetings, List<DateRange> coverage) {
  public static final PublicationBase NONE = new PublicationBase(null, List.of(), List.of());

  public PublicationBase {
    meetings = List.copyOf(meetings);
    coverage = List.copyOf(coverage);
  }
}
