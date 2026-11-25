package org.civictracker.coordinator.run;

import java.util.List;
import java.util.UUID;

public record RunView(
    UUID runId,
    RunState state,
    Phase phase,
    StartRun command,
    UUID basePublicationId,
    List<MeetingView> meetings,
    CurrentStep current,
    Halt halt,
    UUID publicationId,
    Waiting waiting) {
  public RunView {
    meetings = List.copyOf(meetings);
  }
}
