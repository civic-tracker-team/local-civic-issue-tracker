package org.civictracker.analysis.views;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.civictracker.analysis.domain.Candidates;

/**
 * T08 views assembled deterministically from one set of saved findings: issue timelines and
 * summaries, and each meeting's developments. No model is called and nothing is persisted.
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
      String statement,
      SavedFindings.Kind kind,
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

  /** {@code summary} is the timeline's statements in timeline order (no model-written text). */
  public record IssueDetail(
      IssueSummary issue, List<String> summary, List<TimelineEntry> timeline) {
    public IssueDetail {
      summary = List.copyOf(summary);
      timeline = List.copyOf(timeline);
    }
  }

  private final Map<UUID, SavedFindings.Issue> issues = new LinkedHashMap<>();
  private final Map<UUID, SavedFindings.Development> developments = new LinkedHashMap<>();
  private final Passage.Locator locator;

  public FindingViews(
      List<SavedFindings.Issue> issues,
      List<SavedFindings.Development> developments,
      Passage.Locator locator) {
    this.locator = Objects.requireNonNull(locator);
    for (var issue : issues)
      if (this.issues.putIfAbsent(issue.id(), issue) != null)
        throw new IllegalArgumentException("Duplicate issue " + issue.id());
    var meetingOrder = new LinkedHashMap<UUID, Set<Integer>>();
    for (var development : developments) {
      if (this.developments.putIfAbsent(development.id(), development) != null)
        throw new IllegalArgumentException("Duplicate development " + development.id());
      if (!meetingOrder
          .computeIfAbsent(development.meetingId(), k -> new HashSet<>())
          .add(development.sourceOrder()))
        throw new IllegalArgumentException("Duplicate source order in one meeting");
      for (var link : development.links())
        if (!this.issues.containsKey(link.issueId()))
          throw new IllegalArgumentException("Link to unknown issue " + link.issueId());
    }
  }

  /** Issues with at least one development, most recently active first. */
  public List<IssueSummary> issueSummaries() {
    return issues.values().stream()
        .map(issue -> summary(issue, linked(issue.id())))
        .flatMap(Optional::stream)
        .sorted(
            Comparator.comparing(
                    (IssueSummary s) -> TimelineDates.latest(s.latestDate()),
                    Comparator.reverseOrder())
                .thenComparing(IssueSummary::name)
                .thenComparing(IssueSummary::id))
        .toList();
  }

  /** Empty when the issue is unknown or no saved development is linked to it. */
  public Optional<IssueDetail> issueDetail(UUID issueId) {
    var issue = issues.get(issueId);
    if (issue == null) return Optional.empty();
    var linked = linked(issueId);
    return summary(issue, linked)
        .map(
            summary -> {
              var timeline = new ArrayList<TimelineEntry>();
              for (var development : linked) {
                var link =
                    development.links().stream()
                        .filter(l -> l.issueId().equals(issueId))
                        .findFirst()
                        .orElseThrow();
                timeline.add(
                    new TimelineEntry(
                        view(development),
                        TimelineDates.displayDate(development),
                        link.basis(),
                        Passages.assemble(link.evidence(), development, locator)));
              }
              var statements =
                  timeline.stream().map(entry -> entry.development().statement()).toList();
              return new IssueDetail(summary, statements, timeline);
            });
  }

  /** The meeting's developments in source order; empty when it has none. */
  public List<DevelopmentView> meetingDevelopments(UUID meetingId) {
    return developments.values().stream()
        .filter(d -> d.meetingId().equals(meetingId))
        .sorted(Comparator.comparingInt(SavedFindings.Development::sourceOrder))
        .map(this::view)
        .toList();
  }

  /** Developments citing any version of this record, in timeline order (FR-37 allows none). */
  public List<DevelopmentView> recordDevelopments(UUID recordId) {
    return developments.values().stream()
        .filter(d -> d.evidence().stream().anyMatch(s -> s.documentId().equals(recordId)))
        .sorted(TimelineDates.ORDER)
        .map(this::view)
        .toList();
  }

  private List<SavedFindings.Development> linked(UUID issueId) {
    return developments.values().stream()
        .filter(d -> d.links().stream().anyMatch(l -> l.issueId().equals(issueId)))
        .sorted(TimelineDates.ORDER)
        .toList();
  }

  private Optional<IssueSummary> summary(
      SavedFindings.Issue issue, List<SavedFindings.Development> ordered) {
    if (ordered.isEmpty()) return Optional.empty();
    var latest =
        ordered.stream()
            .max(
                Comparator.comparing(
                        (SavedFindings.Development d) ->
                            TimelineDates.latest(TimelineDates.displayDate(d)))
                    .thenComparing(TimelineDates.ORDER))
            .orElseThrow();
    return Optional.of(
        new IssueSummary(
            issue.id(),
            issue.name(),
            ordered.size(),
            TimelineDates.displayDate(ordered.getFirst()),
            TimelineDates.displayDate(latest)));
  }

  private DevelopmentView view(SavedFindings.Development development) {
    var links =
        development.links().stream()
            .map(
                link ->
                    new LinkView(
                        link.issueId(),
                        issues.get(link.issueId()).name(),
                        link.basis(),
                        Passages.assemble(link.evidence(), development, locator)))
            .toList();
    return new DevelopmentView(
        development.id(),
        development.statement(),
        development.kind(),
        development.status(),
        development.dates(),
        development.meetingId(),
        Passages.assemble(development.evidence(), development, locator),
        links);
  }
}
