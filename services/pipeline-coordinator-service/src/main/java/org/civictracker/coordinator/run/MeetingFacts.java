package org.civictracker.coordinator.run;

public record MeetingFacts(
    PlannedMeeting meeting,
    Progress progress,
    Attempts attempts,
    FrozenAnalysisRequest frozenAnalysis) {
  public MeetingFacts(PlannedMeeting meeting, Progress progress, Attempts attempts) {
    this(meeting, progress, attempts, null);
  }
}
