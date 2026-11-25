package org.civictracker.coordinator.run;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public record RunFacts(
    UUID runId,
    StartRun command,
    int maxMeetings,
    String preparationProfileId,
    PublicationBase base,
    int discoveryAttempt,
    Optional<List<MeetingFacts>> meetings) {
  public RunFacts {
    meetings = meetings.map(List::copyOf);
  }
}
