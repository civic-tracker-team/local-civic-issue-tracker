package org.civictracker.analysis.views;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.civictracker.analysis.domain.Candidates;
import org.civictracker.analysis.domain.FindingKind;
import org.civictracker.analysis.domain.IssueHistory;
import org.civictracker.analysis.domain.SavedFindings;

/**
 * T08 views assembled deterministically from saved T07 findings: issue timelines and summaries,
 * and each meeting's developments. No model is called and nothing is persisted.
 *
 * <p>The caller passes the revisions of one publication, one per meeting; a second revision of a
 * meeting would repeat its developments. An issue is whatever T07 saved: the same real-world
 * matter may appear as two issues when the model did not link it to the earlier one.
 *
 * <p>Agenda items, the meeting's record list and FR-38's unavailable outcomes need Records data
 * and the publication manifest, so the T10 routes add them (ADR-0052).
 */
public final class FindingViews {
  public record LinkView(
      UUID issueId, String issueName, Candidates.Basis basis, List<Passage> passages) {
    public LinkView {
      passages = List.copyOf(passages);
    }
  }

  public record DevelopmentView(
      UUID id,
      String description,
      FindingKind kind,
      Candidates.Status status,
      List<Candidates.DateClaim> dates,
      UUID meetingId,
      List<Passage> passages,
      List<LinkView> issues) {
    public DevelopmentView {
      dates = List.copyOf(dates);
      passages = List.copyOf(passages);
      issues = List.copyOf(issues);
    }
  }

  /** First and latest dates are timeline display dates, so both carry their role. */
  public record IssueSummary(
      UUID id,
      String name,
      int developmentCount,
      Candidates.DateClaim firstDate,
      Candidates.DateClaim latestDate) {}

  /** One development as seen from an issue, with the passages tying it to that issue. */
  public record TimelineEntry(
      DevelopmentView development,
      Candidates.DateClaim displayDate,
      Candidates.Basis basis,
      List<Passage> linkPassages) {
    public TimelineEntry {
      linkPassages = List.copyOf(linkPassages);
    }
  }

  /** {@code summary} is the timeline's descriptions in timeline order (no model-written text). */
  public record IssueDetail(
      IssueSummary issue, List<String> summary, List<TimelineEntry> timeline) {
    public IssueDetail {
      summary = List.copyOf(summary);
      timeline = List.copyOf(timeline);
    }
  }

  private final Map<UUID, String> issueNames = new LinkedHashMap<>();
  private final Map<UUID, MeetingDevelopment> developments = new LinkedHashMap<>();

  /**
   * An issue is named by its latest mention among {@code revisions} only. {@link
   * IssueHistory#issue()} is not used, because its name can come from a revision outside the
   * publication (ADR-0051).
   *
   * @param revisions one saved revision per meeting, all from one publication
   * @param histories the history of every issue those revisions link to
   */
  public FindingViews(List<SavedFindings> revisions, List<IssueHistory> histories) {
    var included = new HashSet<UUID>();
    for (var revision : revisions) included.add(revision.revisionId());
    var seen = new HashSet<UUID>();
    for (var history : histories) {
      var issueId = history.issue().issueId();
      if (!seen.add(issueId)) throw new IllegalArgumentException("Duplicate issue " + issueId);
      String name = null;
      for (var revision : history.revisions())
        if (included.contains(revision.revisionId()) && !revision.mentionNames().isEmpty())
          name = revision.mentionNames().getLast();
      if (name != null) issueNames.put(issueId, name);
    }
    var meetings = new HashSet<UUID>();
    for (var revision : revisions) {
      if (!meetings.add(revision.meetingId()))
        throw new IllegalArgumentException("Two revisions of meeting " + revision.meetingId());
      for (var placed : MeetingDevelopment.of(revision)) {
        var id = placed.development().id();
        if (developments.putIfAbsent(id, placed) != null)
          throw new IllegalArgumentException("Duplicate development " + id);
        for (var link : placed.links())
          if (!issueNames.containsKey(link.issueId()))
            throw new IllegalArgumentException("No mention in these revisions: " + link.issueId());
      }
    }
  }

  /** Issues with at least one development, most recently active first. */
  public List<IssueSummary> issueSummaries() {
    return issueNames.keySet().stream()
        .map(issueId -> summary(issueId, linked(issueId)))
        .flatMap(Optional::stream)
        .sorted(
            Comparator.comparing(
                    (IssueSummary s) -> TimelineDates.latest(s.latestDate()),
                    Comparator.reverseOrder())
                .thenComparing(IssueSummary::name)
                .thenComparing(IssueSummary::id))
        .toList();
  }

  /** Empty when the issue is unknown or no development in these revisions is linked to it. */
  public Optional<IssueDetail> issueDetail(UUID issueId) {
    if (!issueNames.containsKey(issueId)) return Optional.empty();
    var linked = linked(issueId);
    return summary(issueId, linked)
        .map(
            summary -> {
              var timeline = new ArrayList<TimelineEntry>();
              for (var placed : linked) {
                var link =
                    placed.links().stream()
                        .filter(l -> l.issueId().equals(issueId))
                        .findFirst()
                        .orElseThrow();
                timeline.add(
                    new TimelineEntry(
                        view(placed),
                        TimelineDates.displayDate(placed.development()),
                        link.basis(),
                        Passages.assemble(link.evidence(), placed)));
              }
              var descriptions =
                  timeline.stream().map(entry -> entry.development().description()).toList();
              return new IssueDetail(summary, descriptions, timeline);
            });
  }

  /** The meeting's developments in source order; empty when it has none. */
  public List<DevelopmentView> meetingDevelopments(UUID meetingId) {
    return developments.values().stream()
        .filter(d -> d.meetingId().equals(meetingId))
        .sorted(Comparator.comparingInt(MeetingDevelopment::sourceOrder))
        .map(this::view)
        .toList();
  }

  /** Developments citing any version of this record, in timeline order (FR-37 allows none). */
  public List<DevelopmentView> recordDevelopments(UUID recordId) {
    return developments.values().stream()
        .filter(
            d ->
                d.development().evidence().stream()
                    .anyMatch(s -> s.documentId().equals(recordId)))
        .sorted(TimelineDates.ORDER)
        .map(this::view)
        .toList();
  }

  private List<MeetingDevelopment> linked(UUID issueId) {
    return developments.values().stream()
        .filter(d -> d.links().stream().anyMatch(l -> l.issueId().equals(issueId)))
        .sorted(TimelineDates.ORDER)
        .toList();
  }

  private Optional<IssueSummary> summary(UUID issueId, List<MeetingDevelopment> ordered) {
    if (ordered.isEmpty()) return Optional.empty();
    var latest =
        ordered.stream()
            .max(
                Comparator.comparing(
                        (MeetingDevelopment d) ->
                            TimelineDates.latest(TimelineDates.displayDate(d.development())))
                    .thenComparing(TimelineDates.ORDER))
            .orElseThrow();
    return Optional.of(
        new IssueSummary(
            issueId,
            issueNames.get(issueId),
            ordered.size(),
            TimelineDates.displayDate(ordered.getFirst().development()),
            TimelineDates.displayDate(latest.development())));
  }

  private DevelopmentView view(MeetingDevelopment placed) {
    var development = placed.development();
    var links =
        placed.links().stream()
            .map(
                link ->
                    new LinkView(
                        link.issueId(),
                        issueNames.get(link.issueId()),
                        link.basis(),
                        Passages.assemble(link.evidence(), placed)))
            .toList();
    return new DevelopmentView(
        development.id(),
        development.description(),
        development.kind(),
        development.status(),
        development.dates(),
        placed.meetingId(),
        Passages.assemble(development.evidence(), placed),
        links);
  }
}
