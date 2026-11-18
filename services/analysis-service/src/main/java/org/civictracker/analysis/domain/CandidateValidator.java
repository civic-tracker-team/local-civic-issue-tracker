package org.civictracker.analysis.domain;

import java.time.LocalDate;
import java.time.Year;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/** Checks reference structure. Semantic support still needs source review. */
public final class CandidateValidator {
  private record Pair(String developmentId, String issueId) {}

  public record Result(List<Problem> problems, Set<String> usableEvidenceIds) {
    public Result {
      problems = List.copyOf(problems);
      usableEvidenceIds = Set.copyOf(usableEvidenceIds);
    }

    public boolean valid() {
      return problems.stream().noneMatch(p -> p.code().invalidates());
    }
  }

  public Result validate(Candidates values, MeetingInput input) {
    var problems = new ArrayList<Problem>();
    var segments = new LinkedHashMap<UUID, CitationIndex.Span>();
    for (var source : input.sources())
      for (var unit : source.units())
        for (var segment : unit.segments()) {
          if (!segment.selected()) continue;
          var span =
              new CitationIndex.Span(
                  source.documentId(),
                  source.versionId(),
                  source.preparationId(),
                  source.profileFingerprint(),
                  segment.id(),
                  0,
                  segment.text().codePointCount(0, segment.text().length()),
                  segment.text());
          if (segments.putIfAbsent(segment.id(), span) != null)
            throw new IllegalArgumentException("Duplicate input segment identity");
        }
    var usable = new HashSet<String>();
    var evidenceIds = new HashSet<String>();
    for (int n = 0; n < values.evidence().size(); n++) {
      var evidence = values.evidence().get(n);
      String at = "evidence[" + n + "]";
      if (!id(evidence.id(), evidenceIds, problems, at + ".id")) {
        usable.remove(evidence.id());
        continue;
      }
      var span = evidence.source();
      var selected = span == null ? null : segments.get(span.segmentId());
      if (!exactSlice(span, selected))
        problems.add(new Problem(Problem.Code.EVIDENCE_TEXT_MISMATCH, at + ".source"));
      else usable.add(evidence.id());
    }
    var developmentIds = new HashSet<String>();
    for (int n = 0; n < values.developments().size(); n++) {
      var development = values.developments().get(n);
      String at = "developments[" + n + "]";
      id(development.id(), developmentIds, problems, at + ".id");
      if (blank(development.description()))
        problems.add(new Problem(Problem.Code.EMPTY_TEXT, at + ".description"));
      if (development.status() == null)
        problems.add(new Problem(Problem.Code.MALFORMED_REPLY, at + ".status"));
      references(development.evidenceIds(), usable, problems, at + ".evidenceIds");
      for (int d = 0; d < development.dates().size(); d++) {
        var date = development.dates().get(d);
        String location = at + ".dates[" + d + "]";
        var invalid = dateProblem(date);
        if (invalid != null) problems.add(new Problem(invalid, location));
        if (invalid == null && date.role() == Candidates.Role.OCCURRENCE) {
          LocalDate earliest =
              switch (date.precision()) {
                case DAY, RANGE -> LocalDate.parse(date.start());
                case MONTH -> YearMonth.parse(date.start()).atDay(1);
                case YEAR -> Year.parse(date.start()).atDay(1);
                case UNKNOWN -> null;
              };
          if (earliest != null && earliest.isAfter(input.meetingDate()))
            problems.add(new Problem(Problem.Code.OCCURRENCE_AFTER_MEETING, location));
        }
        if (date.role() == Candidates.Role.OCCURRENCE
            && development.status() != Candidates.Status.RECORDED)
          problems.add(new Problem(Problem.Code.OCCURRENCE_WITHOUT_RECORD, location));
        if (date.role() == Candidates.Role.MEETING
            && (date.precision() != Candidates.Precision.DAY
                || !input.meetingDate().toString().equals(date.start())
                || date.end() != null))
          problems.add(new Problem(Problem.Code.MEETING_DATE_MISMATCH, location));
      }
    }
    var priorIds = new HashSet<String>();
    input.priorIssues().forEach(i -> priorIds.add(i.id()));
    var issueIds = new HashSet<String>();
    for (int n = 0; n < values.issues().size(); n++) {
      var issue = values.issues().get(n);
      String at = "issues[" + n + "]";
      id(issue.id(), issueIds, problems, at + ".id");
      if (blank(issue.label())) problems.add(new Problem(Problem.Code.EMPTY_TEXT, at + ".label"));
      if (issue.priorIssueId() != null && !priorIds.contains(issue.priorIssueId()))
        problems.add(new Problem(Problem.Code.UNKNOWN_PRIOR_ISSUE, at + ".priorIssueId"));
    }
    var pairs = new HashSet<Pair>();
    for (int n = 0; n < values.associations().size(); n++) {
      var association = values.associations().get(n);
      String at = "associations[" + n + "]";
      if (!developmentIds.contains(association.developmentId()))
        problems.add(new Problem(Problem.Code.MALFORMED_REPLY, at + ".developmentId"));
      if (!issueIds.contains(association.issueId()))
        problems.add(new Problem(Problem.Code.UNKNOWN_ISSUE, at + ".issueId"));
      if (association.basis() == null)
        problems.add(new Problem(Problem.Code.MALFORMED_REPLY, at + ".basis"));
      if (!pairs.add(new Pair(association.developmentId(), association.issueId())))
        problems.add(new Problem(Problem.Code.DUPLICATE_ASSOCIATION, at));
      references(association.evidenceIds(), usable, problems, at + ".evidenceIds");
    }
    return new Result(problems, usable);
  }

  private boolean exactSlice(CitationIndex.Span span, CitationIndex.Span selected) {
    if (selected == null
        || span.text() == null
        || CitationIndex.isBlank(span.text())
        || !Objects.equals(span.documentId(), selected.documentId())
        || !Objects.equals(span.versionId(), selected.versionId())
        || !Objects.equals(span.preparationId(), selected.preparationId())
        || !Objects.equals(span.profileFingerprint(), selected.profileFingerprint())
        || span.start() < 0
        || span.start() >= span.end()
        || span.end() > selected.end()) return false;
    var text = selected.text();
    return text.substring(
            text.offsetByCodePoints(0, span.start()), text.offsetByCodePoints(0, span.end()))
        .equals(span.text());
  }

  private boolean id(String id, Set<String> seen, List<Problem> problems, String at) {
    if (blank(id)) {
      problems.add(new Problem(Problem.Code.EMPTY_TEXT, at));
      return false;
    }
    if (!seen.add(id)) {
      problems.add(new Problem(Problem.Code.DUPLICATE_ID, at));
      return false;
    }
    return true;
  }

  private boolean blank(String value) {
    return value == null || CitationIndex.isBlank(value);
  }

  private void references(List<String> ids, Set<String> known, List<Problem> problems, String at) {
    if (ids.isEmpty()) problems.add(new Problem(Problem.Code.MISSING_EVIDENCE, at));
    else if (new HashSet<>(ids).size() != ids.size() || !known.containsAll(ids))
      problems.add(new Problem(Problem.Code.UNRESOLVED_EVIDENCE_REFERENCE, at));
  }

  private Problem.Code dateProblem(Candidates.DateClaim date) {
    if (date.role() == null || date.precision() == null) return Problem.Code.INVALID_CALENDAR_DATE;
    try {
      if (date.precision() == Candidates.Precision.RANGE
          && LocalDate.parse(date.end()).isBefore(LocalDate.parse(date.start())))
        return Problem.Code.REVERSED_DATE_RANGE;
      boolean valid =
          switch (date.precision()) {
            case UNKNOWN -> date.start() == null && date.end() == null;
            case DAY ->
                date.end() == null && LocalDate.parse(date.start()).toString().equals(date.start());
            case MONTH ->
                date.end() == null && YearMonth.parse(date.start()).toString().equals(date.start());
            case YEAR ->
                date.end() == null && Year.parse(date.start()).toString().equals(date.start());
            case RANGE -> {
              var start = LocalDate.parse(date.start());
              var end = LocalDate.parse(date.end());
              yield !end.isBefore(start);
            }
          };
      return valid ? null : Problem.Code.INVALID_CALENDAR_DATE;
    } catch (RuntimeException exception) {
      return Problem.Code.INVALID_CALENDAR_DATE;
    }
  }
}
