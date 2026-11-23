package org.civictracker.analysis.views;

import static org.junit.jupiter.api.Assertions.*;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.civictracker.analysis.domain.Candidates.Basis;
import org.civictracker.analysis.domain.Candidates.DateClaim;
import org.civictracker.analysis.domain.Candidates.Precision;
import org.civictracker.analysis.domain.Candidates.Role;
import org.civictracker.analysis.domain.Candidates.Status;
import org.civictracker.analysis.views.FindingViews.DevelopmentView;
import org.civictracker.analysis.views.FindingViews.IssueSummary;
import org.civictracker.analysis.views.SavedFindings.Development;
import org.civictracker.analysis.views.SavedFindings.Issue;
import org.civictracker.analysis.views.SavedFindings.Kind;
import org.civictracker.analysis.views.SavedFindings.Link;
import org.civictracker.analysis.views.SavedFindings.Span;
import org.junit.jupiter.api.Test;

class FindingViewsTests {
  static final UUID JAN = id(1), FEB = id(2);
  static final UUID AGENDA = id(10), AGENDA_V = id(11), AGENDA_SEG = id(12);
  static final UUID MINUTES = id(20), MINUTES_V = id(21), MINUTES_SEG = id(22);
  static final UUID MINUTES_ITEM = id(23);
  static final UUID NAV = id(100), PALM = id(101), IDLE = id(102);
  static final LocalDate JAN_15 = LocalDate.parse("2026-01-15");
  static final LocalDate FEB_3 = LocalDate.parse("2026-02-03");

  static final Map<UUID, Passage.Place> PLACES =
      Map.of(
          AGENDA_SEG, new Passage.Place("Agenda", new Passage.PdfPage(2, null)),
          MINUTES_SEG, new Passage.Place("Minutes", new Passage.PdfPage(3, "3")),
          MINUTES_ITEM, new Passage.Place(null, new Passage.HtmlItem("Item 4")));
  static final Passage.Locator LOCATOR = (version, segment) -> PLACES.get(segment);

  static UUID id(int n) {
    return new UUID(0, n);
  }

  static DateClaim day(Role role, String value) {
    return new DateClaim(role, Precision.DAY, value, null, false);
  }

  static DateClaim occurred(Precision precision, String start, String end) {
    return new DateClaim(Role.OCCURRENCE, precision, start, end, false);
  }

  static int length(String text) {
    return text.codePointCount(0, text.length());
  }

  static Span agenda(int start, String text) {
    return new Span(AGENDA, AGENDA_V, AGENDA_SEG, start, start + length(text), text);
  }

  static Span minutes(int start, String text) {
    return new Span(MINUTES, MINUTES_V, MINUTES_SEG, start, start + length(text), text);
  }

  static Link link(UUID issue, Basis basis, Span... evidence) {
    return new Link(issue, basis, List.of(evidence));
  }

  static Link stated(UUID issue) {
    return link(issue, Basis.SOURCE_STATED, minutes(0, "Council "));
  }

  /** A development numbered {@code n}, at position {@code order} in its meeting. */
  static Development dev(
      int n, UUID meeting, LocalDate date, int order, Kind kind, List<DateClaim> extra,
      Link... links) {
    var dates = new ArrayList<>(extra);
    dates.add(day(Role.MEETING, date.toString()));
    return new Development(
        id(1000 + n),
        meeting,
        date,
        order,
        kind,
        "Statement " + n,
        dates,
        List.of(minutes(0, "Council directed staff. ")),
        List.of(links));
  }

  static Development proposal(int n, UUID meeting, LocalDate date, int order, Link... links) {
    return dev(n, meeting, date, order, Kind.PROPOSAL, List.of(), links);
  }

  static Development action(int n, UUID meeting, LocalDate date, int order, Link... links) {
    var occurrence = day(Role.OCCURRENCE, date.toString());
    return dev(n, meeting, date, order, Kind.ACTION, List.of(occurrence), links);
  }

  static FindingViews views(Development... developments) {
    var issues =
        List.of(
            new Issue(NAV, "Navigation Center"),
            new Issue(PALM, "Palm Field property"),
            new Issue(IDLE, "Idle"));
    return new FindingViews(issues, List.of(developments), LOCATOR);
  }

  static List<String> statements(List<DevelopmentView> views) {
    return views.stream().map(DevelopmentView::statement).toList();
  }

  // Display dates

  @Test
  void actionShowsItsOccurrenceDate() {
    var display = TimelineDates.displayDate(action(1, JAN, JAN_15, 0));
    assertEquals(Role.OCCURRENCE, display.role());
  }

  @Test
  void proposalFallsBackToTheMeetingDate() {
    var display = TimelineDates.displayDate(proposal(1, JAN, JAN_15, 0));
    assertEquals(day(Role.MEETING, "2026-01-15"), display);
  }

  @Test
  void unknownOccurrenceFallsBackToTheMeetingDate() {
    var unknown = occurred(Precision.UNKNOWN, null, null);
    var d = dev(1, JAN, JAN_15, 0, Kind.EARLIER_ACTION, List.of(unknown));
    assertEquals(Role.MEETING, TimelineDates.displayDate(d).role());
  }

  @Test
  void plannedDateNeverBecomesTheTimelineDate() {
    var planned = new DateClaim(Role.PLANNED, Precision.MONTH, "2026-03", null, false);
    var d = dev(1, JAN, JAN_15, 0, Kind.PROPOSAL, List.of(planned));
    assertEquals(Role.MEETING, TimelineDates.displayDate(d).role());
  }

  @Test
  void coarseOccurrenceKeepsItsPrecision() {
    var month = new DateClaim(Role.OCCURRENCE, Precision.MONTH, "2025-06", null, true);
    var d = dev(1, JAN, JAN_15, 0, Kind.EARLIER_ACTION, List.of(month));
    assertEquals(month, TimelineDates.displayDate(d));
  }

  @Test
  void dateBoundsFollowPrecision() {
    var range = occurred(Precision.RANGE, "2025-03-01", "2025-04-30");
    assertEquals(LocalDate.parse("2025-03-01"), TimelineDates.earliest(range));
    assertEquals(LocalDate.parse("2025-04-30"), TimelineDates.latest(range));
    var year = occurred(Precision.YEAR, "2024", null);
    assertEquals(LocalDate.parse("2024-01-01"), TimelineDates.earliest(year));
    assertEquals(LocalDate.parse("2024-12-31"), TimelineDates.latest(year));
  }

  // Issue timelines

  @Test
  void timelineOrdersByDisplayDateThenMeetingThenSourceOrder() {
    var later = proposal(1, FEB, FEB_3, 0, stated(NAV));
    var second = action(2, JAN, JAN_15, 1, stated(NAV));
    var first = proposal(3, JAN, JAN_15, 0, stated(NAV));
    var june = occurred(Precision.MONTH, "2025-06", null);
    var inferred = link(NAV, Basis.INFERRED, minutes(0, "Council "));
    var earlier = dev(4, FEB, FEB_3, 1, Kind.EARLIER_ACTION, List.of(june), inferred);

    var detail = views(later, second, first, earlier).issueDetail(NAV).orElseThrow();

    var order = detail.timeline().stream().map(e -> e.development().statement()).toList();
    assertEquals(List.of("Statement 4", "Statement 3", "Statement 2", "Statement 1"), order);
    assertEquals(order, detail.summary());
    assertEquals(june, detail.issue().firstDate());
    assertEquals(day(Role.MEETING, "2026-02-03"), detail.issue().latestDate());
    assertEquals(4, detail.issue().developmentCount());
  }

  @Test
  void timelineCarriesThisIssuesBasisAndLinkPassages() {
    var palm = link(PALM, Basis.SOURCE_STATED, minutes(9, "directed "));
    var nav = link(NAV, Basis.INFERRED, agenda(0, "Navigation Center report"));

    var entry = views(action(1, JAN, JAN_15, 0, palm, nav)).issueDetail(NAV).orElseThrow();

    var only = entry.timeline().getFirst();
    assertEquals(Basis.INFERRED, only.basis());
    assertEquals("Navigation Center report", only.linkPassages().getFirst().text());
    assertEquals("Agenda", only.linkPassages().getFirst().recordLabel());
    assertEquals(2, only.development().issues().size());
    assertEquals("Palm Field property", only.development().issues().getFirst().issueName());
  }

  @Test
  void issueWithoutDevelopmentsOrUnknownIssueHasNoDetail() {
    var views = views(proposal(1, JAN, JAN_15, 0, stated(NAV)));
    assertTrue(views.issueDetail(IDLE).isEmpty());
    assertTrue(views.issueDetail(id(999)).isEmpty());
    assertEquals(List.of(NAV), views.issueSummaries().stream().map(IssueSummary::id).toList());
  }

  @Test
  void issueSummariesPutTheMostRecentlyActiveFirst() {
    var views =
        views(proposal(1, JAN, JAN_15, 0, stated(PALM)), proposal(2, FEB, FEB_3, 0, stated(NAV)));
    assertEquals(
        List.of(NAV, PALM), views.issueSummaries().stream().map(IssueSummary::id).toList());
  }

  @Test
  void earlierActionKeepsItsKindAndShowsAsRecorded() {
    var november = day(Role.OCCURRENCE, "2025-11-04");
    var view =
        views(dev(1, JAN, JAN_15, 0, Kind.EARLIER_ACTION, List.of(november)))
            .meetingDevelopments(JAN)
            .getFirst();
    assertEquals(Kind.EARLIER_ACTION, view.kind());
    assertEquals(Status.RECORDED, view.status());
  }

  // Meetings and records

  @Test
  void meetingDevelopmentsAreInSourceOrderAndOtherMeetingsAreEmpty() {
    var views = views(action(1, JAN, JAN_15, 1), proposal(2, JAN, JAN_15, 0));
    assertEquals(
        List.of("Statement 2", "Statement 1"), statements(views.meetingDevelopments(JAN)));
    assertTrue(views.meetingDevelopments(FEB).isEmpty());
  }

  @Test
  void recordDevelopmentsListOnlyDevelopmentsCitingTheRecord() {
    var views = views(proposal(1, JAN, JAN_15, 0));
    assertEquals(1, views.recordDevelopments(MINUTES).size());
    assertTrue(views.recordDevelopments(AGENDA).isEmpty());
  }

  // Passages

  static List<String> texts(Span... spans) {
    return Passages.assemble(List.of(spans), proposal(1, JAN, JAN_15, 0), LOCATOR).stream()
        .map(Passage::text)
        .toList();
  }

  @Test
  void contiguousSpansInOneSegmentBecomeOnePassage() {
    var spans = List.of(minutes(0, "Motion by "), minutes(10, "Mayor Pro-Tem."));
    var passages = Passages.assemble(spans, proposal(1, JAN, JAN_15, 0), LOCATOR);
    assertEquals(1, passages.size());
    var p = passages.getFirst();
    assertEquals("Motion by Mayor Pro-Tem.", p.text());
    assertEquals(new Passage.PdfPage(3, "3"), p.location());
    assertEquals("Minutes", p.recordLabel());
    assertEquals(MINUTES, p.recordId());
    assertEquals(MINUTES_V, p.versionId());
    assertEquals(JAN, p.meetingId());
  }

  @Test
  void overlapIsJoinedByCodePointsWithoutRepeatingText() {
    // The second span repeats "🏠 " (two code points, three chars) and continues.
    assertEquals(List.of("Café 🏠 site"), texts(minutes(0, "Café 🏠 "), minutes(5, "🏠 site")));
  }

  @Test
  void coveredAndRepeatedSpansAreDropped() {
    var whole = minutes(0, "Motion by Mayor.");
    assertEquals(List.of("Motion by Mayor."), texts(whole, minutes(7, "by"), whole));
  }

  @Test
  void gapsAndOtherSegmentsStaySeparate() {
    var item = new Span(MINUTES, MINUTES_V, MINUTES_ITEM, 0, 6, "Item 4");
    var spans = List.of(minutes(0, "Motion "), minutes(20, "seconded"), item);
    var passages = Passages.assemble(spans, proposal(1, JAN, JAN_15, 0), LOCATOR);
    assertEquals(
        List.of("Motion ", "seconded", "Item 4"), passages.stream().map(Passage::text).toList());
    assertNull(passages.get(2).recordLabel());
    assertEquals(new Passage.HtmlItem("Item 4"), passages.get(2).location());
  }

  @Test
  void missingLocationFailsInsteadOfGuessing() {
    var unknown = new Span(MINUTES, MINUTES_V, id(77), 0, 4, "Text");
    assertThrows(IllegalStateException.class, () -> texts(unknown));
  }

  // Invariants from the T06 host rules

  static Development withDates(LocalDate meetingDate, DateClaim... dates) {
    return new Development(
        id(1),
        JAN,
        meetingDate,
        0,
        Kind.ACTION,
        "s",
        List.of(dates),
        List.of(minutes(0, "Council ")),
        List.of());
  }

  @Test
  void rejectsDatesThatBreakTheHostRules() {
    var meeting = day(Role.MEETING, "2026-01-15");
    var occurrence = day(Role.OCCURRENCE, "2026-01-15");
    var another = day(Role.OCCURRENCE, "2026-01-14");
    assertThrows(IllegalArgumentException.class, () -> withDates(JAN_15, meeting, meeting));
    assertThrows(IllegalArgumentException.class, () -> withDates(FEB_3, meeting));
    assertThrows(
        IllegalArgumentException.class, () -> withDates(JAN_15, meeting, occurrence, another));
    assertDoesNotThrow(() -> withDates(JAN_15, meeting, occurrence));
  }

  @Test
  void rejectsInconsistentFindings() {
    var inferred = link(NAV, Basis.INFERRED, minutes(0, "Council "));
    var unknownIssue = link(id(999), Basis.INFERRED, minutes(0, "Council "));
    assertThrows(
        IllegalArgumentException.class, () -> proposal(1, JAN, JAN_15, 0, stated(NAV), inferred));
    assertThrows(
        IllegalArgumentException.class, () -> views(proposal(1, JAN, JAN_15, 0, unknownIssue)));
    assertThrows(
        IllegalArgumentException.class,
        () -> views(proposal(1, JAN, JAN_15, 0), proposal(2, JAN, JAN_15, 0)));
    assertThrows(
        IllegalArgumentException.class,
        () -> new Span(MINUTES, MINUTES_V, MINUTES_SEG, 0, 3, "Café"));
  }
}
