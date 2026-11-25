package org.civictracker.analysis.views;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import org.civictracker.analysis.domain.Candidates;
import org.civictracker.analysis.domain.FindingKind;
import org.civictracker.analysis.domain.SavedFindings;

/**
 * One saved development placed in its meeting revision. {@code sourceOrder} is its position in
 * the revision's developments and {@code links} are its associations. T07 does not apply these
 * checks when reading, so the views apply them here: exactly one MEETING day equal to the meeting
 * date, at most one OCCURRENCE date, non-empty evidence, one link per issue, a saved status that
 * matches the kind, and span offsets that match their text.
 */
record MeetingDevelopment(
    SavedFindings revision,
    int sourceOrder,
    SavedFindings.Development development,
    List<SavedFindings.Association> links) {
  MeetingDevelopment {
    Objects.requireNonNull(revision);
    Objects.requireNonNull(revision.meetingId());
    Objects.requireNonNull(revision.meetingDate());
    Objects.requireNonNull(development);
    Objects.requireNonNull(development.id());
    Objects.requireNonNull(development.kind());
    Objects.requireNonNull(development.description());
    links = List.copyOf(links);
    if (sourceOrder < 0) throw new IllegalArgumentException("Negative source order");
    if (development.status() != status(development.kind()))
      throw new IllegalArgumentException("Saved status does not match kind " + development.id());
    if (development.evidence().isEmpty())
      throw new IllegalArgumentException("A development needs evidence");
    development.evidence().forEach(MeetingDevelopment::check);
    var dates = development.dates();
    var meeting = dates.stream().filter(d -> d.role() == Candidates.Role.MEETING).toList();
    if (meeting.size() != 1
        || meeting.getFirst().precision() != Candidates.Precision.DAY
        || !revision.meetingDate().toString().equals(meeting.getFirst().start()))
      throw new IllegalArgumentException("Exactly one MEETING day matching the meeting date");
    if (dates.stream().filter(d -> d.role() == Candidates.Role.OCCURRENCE).count() > 1)
      throw new IllegalArgumentException("At most one OCCURRENCE date");
    var issues = new HashSet<UUID>();
    for (var link : links) {
      if (!development.id().equals(link.developmentId()))
        throw new IllegalArgumentException("Link belongs to another development");
      Objects.requireNonNull(link.issueId());
      Objects.requireNonNull(link.basis());
      if (!issues.add(link.issueId()))
        throw new IllegalArgumentException("One link per issue per development");
      if (link.evidence().isEmpty()) throw new IllegalArgumentException("A link needs evidence");
      link.evidence().forEach(MeetingDevelopment::check);
    }
  }

  /** Each development of one revision in source order, with its associations grouped. */
  static List<MeetingDevelopment> of(SavedFindings revision) {
    var links = new LinkedHashMap<UUID, List<SavedFindings.Association>>();
    for (var development : revision.developments())
      if (links.putIfAbsent(development.id(), new ArrayList<>()) != null)
        throw new IllegalArgumentException("Duplicate development " + development.id());
    for (var association : revision.associations()) {
      var group = links.get(association.developmentId());
      if (group == null)
        throw new IllegalArgumentException(
            "Association to unknown development " + association.developmentId());
      group.add(association);
    }
    var placed = new ArrayList<MeetingDevelopment>();
    for (int i = 0; i < revision.developments().size(); i++) {
      var development = revision.developments().get(i);
      placed.add(new MeetingDevelopment(revision, i, development, links.get(development.id())));
    }
    return placed;
  }

  /** An earlier reported action is still a recorded action, but keeps its own kind. */
  static Candidates.Status status(FindingKind kind) {
    return switch (kind) {
      case PROPOSAL -> Candidates.Status.PROPOSED;
      case ACTION, EARLIER_ACTION -> Candidates.Status.RECORDED;
      case UNCLEAR -> Candidates.Status.UNCERTAIN;
    };
  }

  UUID meetingId() {
    return revision.meetingId();
  }

  LocalDate meetingDate() {
    return revision.meetingDate();
  }

  private static void check(SavedFindings.EvidenceSpan span) {
    Objects.requireNonNull(span.documentId());
    Objects.requireNonNull(span.versionId());
    Objects.requireNonNull(span.segmentId());
    Objects.requireNonNull(span.text());
    if (span.start() < 0
        || span.end() <= span.start()
        || span.text().codePointCount(0, span.text().length()) != span.end() - span.start())
      throw new IllegalArgumentException("Span offsets must match its text");
    if (span.unitOrdinal() < 0 || span.segmentOrdinal() < 0)
      throw new IllegalArgumentException("Negative span position");
  }
}
