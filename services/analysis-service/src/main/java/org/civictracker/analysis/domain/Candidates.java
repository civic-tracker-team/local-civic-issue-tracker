package org.civictracker.analysis.domain;

import java.util.List;

/** Temporary evaluation values, not production issue identities. */
public record Candidates(
    List<Evidence> evidence,
    List<Development> developments,
    List<Issue> issues,
    List<Association> associations) {
  public Candidates {
    evidence = List.copyOf(evidence);
    developments = List.copyOf(developments);
    issues = List.copyOf(issues);
    associations = List.copyOf(associations);
  }

  public enum Status {
    PROPOSED,
    RECORDED,
    UNCERTAIN
  }

  public enum Role {
    OCCURRENCE,
    MEETING,
    PUBLICATION,
    PLANNED
  }

  public enum Precision {
    DAY,
    MONTH,
    YEAR,
    RANGE,
    UNKNOWN
  }

  public enum Basis {
    SOURCE_STATED,
    INFERRED
  }

  public record Evidence(String id, CitationIndex.Span source) {}

  public record DateClaim(
      Role role, Precision precision, String start, String end, boolean approximate) {}

  public record Development(
      String id,
      String description,
      Status status,
      List<DateClaim> dates,
      List<String> evidenceIds) {
    public Development {
      dates = List.copyOf(dates);
      evidenceIds = List.copyOf(evidenceIds);
    }
  }

  public record Issue(String id, String label, String priorIssueId) {}

  public record Association(
      String developmentId, String issueId, Basis basis, List<String> evidenceIds) {
    public Association {
      evidenceIds = List.copyOf(evidenceIds);
    }
  }
}
