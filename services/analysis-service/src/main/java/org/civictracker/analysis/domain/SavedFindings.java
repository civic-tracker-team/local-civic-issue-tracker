package org.civictracker.analysis.domain;

import java.time.LocalDate;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** One saved revision, independent of its persistence entities. */
public record SavedFindings(
    UUID revisionId,
    UUID meetingId,
    String officialId,
    LocalDate meetingDate,
    String title,
    String contractRevision,
    String profileFingerprint,
    List<Development> developments,
    List<Issue> issues,
    List<Association> associations) {
  public SavedFindings {
    developments = List.copyOf(developments);
    issues = List.copyOf(issues);
    associations = List.copyOf(associations);
  }

  public record Development(
      UUID id,
      FindingKind kind,
      String description,
      Candidates.Status status,
      List<Candidates.DateClaim> dates,
      List<EvidenceSpan> evidence) {
    public Development {
      dates = List.copyOf(dates);
      evidence = List.copyOf(evidence);
    }
  }

  public record Issue(UUID issueId, String name, boolean continued) {}

  public record Association(
      UUID developmentId, UUID issueId, Candidates.Basis basis, List<EvidenceSpan> evidence) {
    public Association {
      evidence = List.copyOf(evidence);
    }
  }

  public record EvidenceSpan(
      UUID documentId,
      UUID versionId,
      UUID preparationId,
      UUID segmentId,
      int start,
      int end,
      String text,
      int unitOrdinal,
      int segmentOrdinal,
      Map<String, Object> locator) {
    public EvidenceSpan {
      locator = Collections.unmodifiableMap(new LinkedHashMap<>(locator));
    }
  }
}
