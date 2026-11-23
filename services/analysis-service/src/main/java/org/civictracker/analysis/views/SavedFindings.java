package org.civictracker.analysis.views;

import java.time.LocalDate;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import org.civictracker.analysis.domain.Candidates;

/**
 * The saved findings that T08 views are assembled from. T07 storage supplies these values; the
 * views never call a model.
 */
public final class SavedFindings {
  private SavedFindings() {}

  /** The finding kind, kept separate so an earlier reported action is not shown as new. */
  public enum Kind {
    PROPOSAL(Candidates.Status.PROPOSED),
    ACTION(Candidates.Status.RECORDED),
    EARLIER_ACTION(Candidates.Status.RECORDED),
    UNCLEAR(Candidates.Status.UNCERTAIN);

    private final Candidates.Status status;

    Kind(Candidates.Status status) {
      this.status = status;
    }

    public Candidates.Status status() {
      return status;
    }
  }

  /** An exact slice of one prepared segment, with code-point offsets as in CitationIndex. */
  public record Span(
      UUID documentId, UUID versionId, UUID segmentId, int start, int end, String text) {
    public Span {
      Objects.requireNonNull(documentId);
      Objects.requireNonNull(versionId);
      Objects.requireNonNull(segmentId);
      Objects.requireNonNull(text);
      if (start < 0 || end <= start || text.codePointCount(0, text.length()) != end - start)
        throw new IllegalArgumentException("Span offsets must match its text");
    }
  }

  public record Link(UUID issueId, Candidates.Basis basis, List<Span> evidence) {
    public Link {
      Objects.requireNonNull(issueId);
      Objects.requireNonNull(basis);
      evidence = List.copyOf(evidence);
      if (evidence.isEmpty()) throw new IllegalArgumentException("A link needs evidence");
    }
  }

  /**
   * One saved development. {@code sourceOrder} is its position among the meeting's developments.
   * Dates follow the T06 host rules: exactly one MEETING day equal to the meeting date and at
   * most one OCCURRENCE date.
   */
  public record Development(
      UUID id,
      UUID meetingId,
      LocalDate meetingDate,
      int sourceOrder,
      Kind kind,
      String statement,
      List<Candidates.DateClaim> dates,
      List<Span> evidence,
      List<Link> links) {
    public Development {
      Objects.requireNonNull(id);
      Objects.requireNonNull(meetingId);
      Objects.requireNonNull(meetingDate);
      Objects.requireNonNull(kind);
      Objects.requireNonNull(statement);
      dates = List.copyOf(dates);
      evidence = List.copyOf(evidence);
      links = List.copyOf(links);
      if (sourceOrder < 0) throw new IllegalArgumentException("Negative source order");
      if (evidence.isEmpty()) throw new IllegalArgumentException("A development needs evidence");
      var meeting = dates.stream().filter(d -> d.role() == Candidates.Role.MEETING).toList();
      if (meeting.size() != 1
          || meeting.getFirst().precision() != Candidates.Precision.DAY
          || !meetingDate.toString().equals(meeting.getFirst().start()))
        throw new IllegalArgumentException("Exactly one MEETING day matching the meeting date");
      if (dates.stream().filter(d -> d.role() == Candidates.Role.OCCURRENCE).count() > 1)
        throw new IllegalArgumentException("At most one OCCURRENCE date");
      var issues = new HashSet<UUID>();
      for (var link : links)
        if (!issues.add(link.issueId()))
          throw new IllegalArgumentException("One link per issue per development");
    }

    public Candidates.Status status() {
      return kind.status();
    }
  }

  public record Issue(UUID id, String name) {
    public Issue {
      Objects.requireNonNull(id);
      Objects.requireNonNull(name);
    }
  }
}
