package org.civictracker.analysis.domain;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/** An issue's mentions and linked findings in meeting order. */
public record IssueHistory(SavedFindings.Issue issue, List<Revision> revisions) {
  public IssueHistory {
    revisions = List.copyOf(revisions);
  }

  public record Revision(
      UUID revisionId,
      UUID meetingId,
      LocalDate meetingDate,
      List<String> mentionNames,
      List<SavedFindings.Development> developments,
      List<SavedFindings.Association> associations) {
    public Revision {
      mentionNames = List.copyOf(mentionNames);
      developments = List.copyOf(developments);
      associations = List.copyOf(associations);
    }
  }
}
