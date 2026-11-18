package org.civictracker.analysis.domain;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** A complete, revision-pinned selection, including unused extraction alternatives. */
public record MeetingInput(
    UUID meetingId,
    String officialId,
    LocalDate meetingDate,
    String title,
    List<Source> sources,
    List<PriorIssue> priorIssues) {
  public MeetingInput {
    sources = List.copyOf(sources);
    priorIssues = List.copyOf(priorIssues);
  }

  public record Source(
      UUID documentId,
      UUID versionId,
      UUID artifactId,
      String sha256,
      long byteLength,
      UUID preparationId,
      String profileFingerprint,
      List<String> sourceUrls,
      List<Unit> units) {
    public Source {
      sourceUrls = List.copyOf(sourceUrls);
      units = List.copyOf(units);
    }
  }

  public record Unit(
      int ordinal,
      String disposition,
      String nativeText,
      List<Segment> segments,
      List<String> notes) {
    public Unit {
      segments = List.copyOf(segments);
      notes = List.copyOf(notes);
    }
  }

  public record Segment(
      UUID id,
      String text,
      String method,
      Map<String, Object> locator,
      Double score,
      boolean selected) {
    public Segment {
      locator = java.util.Collections.unmodifiableMap(new java.util.LinkedHashMap<>(locator));
    }
  }

  public record PriorIssue(
      String id, String description, LocalDate reviewedThrough, String snapshotSha256) {}

  public List<Segment> selectedSegments() {
    return sources.stream()
        .flatMap(s -> s.units().stream())
        .flatMap(u -> u.segments().stream())
        .filter(Segment::selected)
        .toList();
  }
}
