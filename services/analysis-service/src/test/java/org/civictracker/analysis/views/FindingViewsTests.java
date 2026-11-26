package org.civictracker.analysis.views;

import static org.junit.jupiter.api.Assertions.*;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.civictracker.analysis.domain.Candidates.Basis;
import org.civictracker.analysis.domain.Candidates.DateClaim;
import org.civictracker.analysis.domain.Candidates.Precision;
import org.civictracker.analysis.domain.Candidates.Role;
import org.civictracker.analysis.domain.Candidates.Status;
import org.civictracker.analysis.domain.FindingKind;
import org.civictracker.analysis.domain.IssueHistory;
import org.civictracker.analysis.domain.SavedFindings;
import org.civictracker.analysis.domain.SavedFindings.Association;
import org.civictracker.analysis.domain.SavedFindings.Development;
import org.civictracker.analysis.domain.SavedFindings.EvidenceSpan;
import org.civictracker.analysis.domain.SavedFindings.Issue;
import org.civictracker.analysis.views.FindingViews.DevelopmentView;
import org.civictracker.analysis.views.FindingViews.IssueSummary;
import org.junit.jupiter.api.Test;

class FindingViewsTests {
  static final UUID JAN = id(1), FEB = id(2);
  static final UUID AGENDA = id(10), AGENDA_V = id(11), AGENDA_SEG = id(12);
  static final UUID MINUTES = id(20), MINUTES_V = id(21), MINUTES_SEG = id(22);
  static final UUID HTML = id(30), HTML_V = id(31), HTML_SEG = id(32);
  static final UUID NAV = id(100), PALM = id(101), IDLE = id(102);
  static final LocalDate JAN_15 = LocalDate.parse("2026-01-15");
  static final LocalDate FEB_3 = LocalDate.parse("2026-02-03");

  static final Map<String, Object> PAGE_2 = pdf(2, null);
  static final Map<String, Object> PAGE_3 = pdf(3, "3");

  static UUID id(int n) {
    return new UUID(0, n);
  }

  static Map<String, Object> pdf(int page, String label) {
    var locator = new LinkedHashMap<String, Object>();
    locator.put("kind", "pdf");
    locator.put("page", page);
    locator.put("pageLabel", label);
    locator.put("width", 612.0);
    locator.put("height", 792.0);
    return locator;
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

  static EvidenceSpan span(
      UUID document,
      UUID version,
      UUID segment,
      int unit,
      int line,
      int start,
      String text,
      Map<String, Object> locator) {
    return new EvidenceSpan(
        document, version, id(0), segment, start, start + length(text), text, unit, line, locator);
  }

  static EvidenceSpan agenda(int start, String text) {
    return span(AGENDA, AGENDA_V, AGENDA_SEG, 1, 0, start, text, PAGE_2);
  }

  static EvidenceSpan minutes(int start, String text) {
    return span(MINUTES, MINUTES_V, MINUTES_SEG, 2, 0, start, text, PAGE_3);
  }

  /** A whole OCR line of the minutes: page 3 is unit 2, and each line is its own segment. */
  static EvidenceSpan line(int line, String text) {
    return span(MINUTES, MINUTES_V, id(500 + line), 2, line, 0, text, PAGE_3);
  }

  /** A test-only association before it is attached to its development. */
  record Link(UUID issue, Basis basis, List<EvidenceSpan> evidence) {}

  record Dev(Development development, List<Link> links) {}

  static Link link(UUID issue, Basis basis, EvidenceSpan... evidence) {
    return new Link(issue, basis, List.of(evidence));
  }

  static Link stated(UUID issue) {
    return link(issue, Basis.SOURCE_STATED, minutes(0, "Council "));
  }

  static Development development(
      int n, FindingKind kind, Status status, List<DateClaim> dates, List<EvidenceSpan> evidence) {
    return new Development(id(1000 + n), kind, "Description " + n, status, dates, evidence);
  }

  /** Development {@code n} of a meeting held on {@code date}. */
  static Dev dev(int n, LocalDate date, FindingKind kind, List<DateClaim> extra, Link... links) {
    var dates = new ArrayList<>(extra);
    dates.add(day(Role.MEETING, date.toString()));
    var evidence = List.of(minutes(0, "Council directed staff. "));
    return new Dev(
        development(n, kind, MeetingDevelopment.status(kind), dates, evidence), List.of(links));
  }

  static Dev proposal(int n, LocalDate date, Link... links) {
    return dev(n, date, FindingKind.PROPOSAL, List.of(), links);
  }

  static Dev action(int n, LocalDate date, Link... links) {
    return dev(n, date, FindingKind.ACTION, List.of(day(Role.OCCURRENCE, date.toString())), links);
  }

  /** A saved revision whose developments are in the order given. */
  static SavedFindings meeting(UUID meetingId, LocalDate date, Dev... devs) {
    var issues = new LinkedHashMap<UUID, Issue>();
    var associations = new ArrayList<Association>();
    for (var dev : devs)
      for (var link : dev.links()) {
        issues.putIfAbsent(link.issue(), new Issue(link.issue(), NAMES.get(link.issue()), false));
        associations.add(
            new Association(
                dev.development().id(), link.issue(), link.basis(), link.evidence()));
      }
    return revision(
        meetingId,
        date,
        Arrays.stream(devs).map(Dev::development).toList(),
        List.copyOf(issues.values()),
        associations);
  }

  static SavedFindings revision(
      UUID meetingId,
      LocalDate date,
      List<Development> developments,
      List<Issue> issues,
      List<Association> associations) {
    return new SavedFindings(
        UUID.randomUUID(),
        meetingId,
        "official",
        date,
        "Regular meeting",
        "contract",
        "fingerprint",
        developments,
        issues,
        associations);
  }

  static final Map<UUID, String> NAMES =
      Map.of(NAV, "Navigation Center", PALM, "Palm Field property", IDLE, "Idle");

  static IssueHistory.Revision mentioned(SavedFindings revision, String... names) {
    return new IssueHistory.Revision(
        revision.revisionId(),
        revision.meetingId(),
        revision.meetingDate(),
        List.of(names),
        List.of(),
        List.of());
  }

  /**
   * Each known issue's history over these revisions. Its own name is deliberately stale, as it
   * would be when a later revision outside the publication renamed the issue.
   */
  static List<IssueHistory> histories(List<SavedFindings> revisions) {
    var sorted =
        revisions.stream().sorted(Comparator.comparing(SavedFindings::meetingDate)).toList();
    return NAMES.keySet().stream()
        .map(
            issue ->
                new IssueHistory(
                    new Issue(issue, "Unpublished name", false),
                    sorted.stream()
                        .flatMap(
                            r ->
                                r.issues().stream()
                                    .filter(i -> i.issueId().equals(issue))
                                    .map(i -> mentioned(r, i.name())))
                        .toList()))
        .toList();
  }

  static FindingViews views(SavedFindings... revisions) {
    return new FindingViews(List.of(revisions), histories(List.of(revisions)));
  }

  static FindingViews views(Dev dev) {
    return views(meeting(JAN, JAN_15, dev));
  }

  static List<String> descriptions(List<DevelopmentView> views) {
    return views.stream().map(DevelopmentView::description).toList();
  }

  static MeetingDevelopment placed(Dev dev) {
    return MeetingDevelopment.of(meeting(JAN, JAN_15, dev)).getFirst();
  }

  // Display dates

  @Test
  void actionShowsItsOccurrenceDate() {
    var display = TimelineDates.displayDate(action(1, JAN_15).development());
    assertEquals(Role.OCCURRENCE, display.role());
  }

  @Test
  void proposalFallsBackToTheMeetingDate() {
    var display = TimelineDates.displayDate(proposal(1, JAN_15).development());
    assertEquals(day(Role.MEETING, "2026-01-15"), display);
  }

  @Test
  void unknownOccurrenceFallsBackToTheMeetingDate() {
    var unknown = occurred(Precision.UNKNOWN, null, null);
    var d = dev(1, JAN_15, FindingKind.EARLIER_ACTION, List.of(unknown));
    assertEquals(Role.MEETING, TimelineDates.displayDate(d.development()).role());
  }

  @Test
  void plannedDateNeverBecomesTheTimelineDate() {
    var planned = new DateClaim(Role.PLANNED, Precision.MONTH, "2026-03", null, false);
    var d = dev(1, JAN_15, FindingKind.PROPOSAL, List.of(planned));
    assertEquals(Role.MEETING, TimelineDates.displayDate(d.development()).role());
  }

  @Test
  void coarseOccurrenceKeepsItsPrecision() {
    var month = new DateClaim(Role.OCCURRENCE, Precision.MONTH, "2025-06", null, true);
    var d = dev(1, JAN_15, FindingKind.EARLIER_ACTION, List.of(month));
    assertEquals(month, TimelineDates.displayDate(d.development()));
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
    var june = occurred(Precision.MONTH, "2025-06", null);
    var inferred = link(NAV, Basis.INFERRED, minutes(0, "Council "));
    var feb =
        meeting(
            FEB,
            FEB_3,
            proposal(1, FEB_3, stated(NAV)),
            dev(4, FEB_3, FindingKind.EARLIER_ACTION, List.of(june), inferred));
    var jan =
        meeting(JAN, JAN_15, proposal(3, JAN_15, stated(NAV)), action(2, JAN_15, stated(NAV)));

    var detail = views(feb, jan).issueDetail(NAV).orElseThrow();

    var order = detail.timeline().stream().map(e -> e.development().description()).toList();
    assertEquals(
        List.of("Description 4", "Description 3", "Description 2", "Description 1"), order);
    assertEquals(order, detail.summary());
    assertEquals(june, detail.issue().firstDate());
    assertEquals(day(Role.MEETING, "2026-02-03"), detail.issue().latestDate());
    assertEquals(4, detail.issue().developmentCount());
  }

  @Test
  void timelineCarriesThisIssuesBasisAndLinkPassages() {
    var palm = link(PALM, Basis.SOURCE_STATED, minutes(9, "directed "));
    var nav = link(NAV, Basis.INFERRED, agenda(0, "Navigation Center report"));

    var detail = views(action(1, JAN_15, palm, nav)).issueDetail(NAV).orElseThrow();

    var only = detail.timeline().getFirst();
    assertEquals(Basis.INFERRED, only.basis());
    var passage = only.linkPassages().getFirst();
    assertEquals("Navigation Center report", passage.text());
    assertEquals(new Passage.PdfPage(2, null), passage.location());
    assertEquals(2, only.development().issues().size());
    assertEquals("Palm Field property", only.development().issues().getFirst().issueName());
  }

  @Test
  void issueIsNamedByItsLatestMentionInTheGivenRevisionsOnly() {
    var jan = meeting(JAN, JAN_15, proposal(1, JAN_15, stated(NAV)));
    var feb = meeting(FEB, FEB_3, proposal(2, FEB_3, stated(NAV)));
    var unpublished = meeting(id(3), LocalDate.parse("2026-03-03"));
    var history =
        new IssueHistory(
            new Issue(NAV, "Unpublished name", false),
            List.of(
                mentioned(jan, "Nav Center"),
                mentioned(feb, "Navigation Ctr", "Navigation Center"),
                mentioned(unpublished, "Unpublished name")));

    var views = new FindingViews(List.of(jan, feb), List.of(history));

    assertEquals("Navigation Center", views.issueSummaries().getFirst().name());
    var link = views.meetingDevelopments(JAN).getFirst().issues().getFirst();
    assertEquals("Navigation Center", link.issueName());
  }

  @Test
  void issueMentionedOnlyOutsideTheGivenRevisionsIsUnknown() {
    var jan = meeting(JAN, JAN_15, proposal(1, JAN_15, stated(NAV)));
    var other = meeting(FEB, FEB_3);
    var history = new IssueHistory(new Issue(NAV, "x", false), List.of(mentioned(other, "x")));
    assertThrows(
        IllegalArgumentException.class, () -> new FindingViews(List.of(jan), List.of(history)));
  }

  @Test
  void issueWithoutDevelopmentsOrUnknownIssueHasNoDetail() {
    var views = views(proposal(1, JAN_15, stated(NAV)));
    assertTrue(views.issueDetail(IDLE).isEmpty());
    assertTrue(views.issueDetail(id(999)).isEmpty());
    assertEquals(List.of(NAV), views.issueSummaries().stream().map(IssueSummary::id).toList());
  }

  @Test
  void issueSummariesPutTheMostRecentlyActiveFirst() {
    var views =
        views(
            meeting(JAN, JAN_15, proposal(1, JAN_15, stated(PALM))),
            meeting(FEB, FEB_3, proposal(2, FEB_3, stated(NAV))));
    assertEquals(
        List.of(NAV, PALM), views.issueSummaries().stream().map(IssueSummary::id).toList());
  }

  @Test
  void earlierActionKeepsItsKindAndShowsAsRecorded() {
    var november = day(Role.OCCURRENCE, "2025-11-04");
    var view =
        views(dev(1, JAN_15, FindingKind.EARLIER_ACTION, List.of(november)))
            .meetingDevelopments(JAN)
            .getFirst();
    assertEquals(FindingKind.EARLIER_ACTION, view.kind());
    assertEquals(Status.RECORDED, view.status());
  }

  // Meetings and records

  @Test
  void meetingDevelopmentsFollowTheRevisionsOrderAndOtherMeetingsAreEmpty() {
    var views = views(meeting(JAN, JAN_15, proposal(2, JAN_15), action(1, JAN_15)));
    assertEquals(
        List.of("Description 2", "Description 1"), descriptions(views.meetingDevelopments(JAN)));
    assertTrue(views.meetingDevelopments(FEB).isEmpty());
  }

  @Test
  void recordDevelopmentsListOnlyDevelopmentsCitingTheRecord() {
    var views = views(proposal(1, JAN_15));
    assertEquals(1, views.recordDevelopments(MINUTES).size());
    assertTrue(views.recordDevelopments(AGENDA).isEmpty());
  }

  // Passages

  static List<Passage> passages(EvidenceSpan... spans) {
    return Passages.assemble(List.of(spans), placed(proposal(1, JAN_15)));
  }

  static List<String> texts(EvidenceSpan... spans) {
    return passages(spans).stream().map(Passage::text).toList();
  }

  @Test
  void contiguousSpansInOneSegmentBecomeOnePassage() {
    var passages = passages(minutes(0, "Motion by "), minutes(10, "Mayor Pro-Tem."));
    assertEquals(1, passages.size());
    var p = passages.getFirst();
    assertEquals("Motion by Mayor Pro-Tem.", p.text());
    assertEquals(new Passage.PdfPage(3, "3"), p.location());
    assertEquals(MINUTES, p.recordId());
    assertEquals(MINUTES_V, p.versionId());
    assertEquals(JAN, p.meetingId());
    assertEquals(JAN_15, p.meetingDate());
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
  void gapsInOneSegmentStaySeparate() {
    assertEquals(
        List.of("Motion ", "seconded"), texts(minutes(0, "Motion "), minutes(20, "seconded")));
  }

  @Test
  void adjacentLinesOnOnePageJoinWithLineBreaks() {
    var passages = passages(line(4, "MOTION: Council"), line(5, "approved the"), line(6, "lease."));
    assertEquals(1, passages.size());
    assertEquals("MOTION: Council\napproved the\nlease.", passages.getFirst().text());
    assertEquals(new Passage.PdfPage(3, "3"), passages.getFirst().location());
  }

  @Test
  void aCitedLineRepeatedInsideAJoinedPassageIsDropped() {
    assertEquals(
        List.of("one\ntwo\nthree"),
        texts(line(1, "one"), line(2, "two"), line(2, "two"), line(3, "three")));
  }

  @Test
  void separateRangesOnOnePageStaySeparate() {
    // Lines 1-2 and 7-8 of the same page are two cited ranges, not one continuous passage.
    assertEquals(
        List.of("a\nb", "g\nh"), texts(line(1, "a"), line(2, "b"), line(7, "g"), line(8, "h")));
    // Citation order matters: an earlier line cited later does not join backwards.
    assertEquals(List.of("b", "a"), texts(line(2, "b"), line(1, "a")));
  }

  /** Lines of a native PDF segment on page 3, which also has OCR line segments after it. */
  static EvidenceSpan nativeText(int start, String text) {
    return span(MINUTES, MINUTES_V, id(700), 2, 0, start, text, PAGE_3);
  }

  static EvidenceSpan ocrAfterNative(String text) {
    return span(MINUTES, MINUTES_V, id(701), 2, 1, 0, text, PAGE_3);
  }

  @Test
  void citationEndingBeforeItsSegmentsLastLineDoesNotJoinTheNextSegment() {
    // Lines 3-4 of a native segment were cited, then the first OCR line. Line 5 sits between
    // them, so joining would hide it. Only a segment's last line lacks a trailing line break.
    var linesThreeAndFour = nativeText(20, "Line three.\nLine four.\n");
    assertEquals(
        List.of("Line three.\nLine four.\n", "Caption"),
        texts(linesThreeAndFour, ocrAfterNative("Caption")));
  }

  @Test
  void citationReachingItsSegmentsEndJoinsTheNextSegment() {
    var lastLine = nativeText(40, "Line six.");
    assertEquals(List.of("Line six.\nCaption"), texts(lastLine, ocrAfterNative("Caption")));
  }

  @Test
  void nextLineMustStartAtItsBeginning() {
    var midLine = span(MINUTES, MINUTES_V, id(502), 2, 2, 3, "tail", PAGE_3);
    assertEquals(List.of("one", "tail"), texts(line(1, "one"), midLine));
  }

  @Test
  void linesOnAnotherPageOrVersionDoNotJoin() {
    var nextPage = span(MINUTES, MINUTES_V, id(600), 3, 1, 0, "top", pdf(4, "4"));
    var otherVersion = span(MINUTES, id(99), id(601), 2, 1, 0, "other", PAGE_3);
    var passages = passages(line(0, "end"), nextPage);
    assertEquals(List.of("end", "top"), passages.stream().map(Passage::text).toList());
    assertEquals(new Passage.PdfPage(4, "4"), passages.get(1).location());
    assertEquals(List.of("end", "other"), texts(line(0, "end"), otherVersion));
  }

  @Test
  void htmlLocatorKeepsItsItemSectionAndPath() {
    var locator = new HashMap<String, Object>();
    locator.put("kind", "html");
    locator.put("itemId", "4");
    locator.put("sectionId", null);
    locator.put("path", "div.item > p");
    var item = span(HTML, HTML_V, HTML_SEG, 0, 0, 0, "Item 4", locator);
    assertEquals(
        new Passage.HtmlItem("4", null, "div.item > p"), passages(item).getFirst().location());
  }

  @Test
  void unreadableLocatorFailsInsteadOfGuessing() {
    var missingPage = span(MINUTES, MINUTES_V, MINUTES_SEG, 2, 0, 0, "Text", Map.of("kind", "pdf"));
    var unknownKind = span(MINUTES, MINUTES_V, MINUTES_SEG, 2, 0, 0, "Text", Map.of("kind", "x"));
    assertThrows(IllegalStateException.class, () -> passages(missingPage));
    assertThrows(IllegalStateException.class, () -> passages(unknownKind));
  }

  // Input checks T07 does not apply when reading

  static Dev withDates(DateClaim... dates) {
    return new Dev(
        development(
            1,
            FindingKind.ACTION,
            Status.RECORDED,
            List.of(dates),
            List.of(minutes(0, "Council "))),
        List.of());
  }

  @Test
  void rejectsDatesThatBreakTheHostRules() {
    var meeting = day(Role.MEETING, "2026-01-15");
    var otherDay = day(Role.MEETING, "2026-02-03");
    var occurrence = day(Role.OCCURRENCE, "2026-01-15");
    var another = day(Role.OCCURRENCE, "2026-01-14");
    assertThrows(IllegalArgumentException.class, () -> placed(withDates(meeting, meeting)));
    assertThrows(IllegalArgumentException.class, () -> placed(withDates(otherDay)));
    assertThrows(
        IllegalArgumentException.class, () -> placed(withDates(meeting, occurrence, another)));
    assertDoesNotThrow(() -> placed(withDates(meeting, occurrence)));
  }

  @Test
  void rejectsASavedStatusThatDisagreesWithTheKind() {
    var dates = List.of(day(Role.MEETING, "2026-01-15"));
    var evidence = List.of(minutes(0, "Council "));
    var mismatched = development(1, FindingKind.EARLIER_ACTION, Status.PROPOSED, dates, evidence);
    assertThrows(
        IllegalArgumentException.class, () -> placed(new Dev(mismatched, List.of())));
  }

  @Test
  void rejectsMissingEvidenceAndBadOffsets() {
    var dates = List.of(day(Role.MEETING, "2026-01-15"));
    var none = development(1, FindingKind.ACTION, Status.RECORDED, dates, List.of());
    assertThrows(IllegalArgumentException.class, () -> placed(new Dev(none, List.of())));
    var bad = new EvidenceSpan(MINUTES, MINUTES_V, id(0), MINUTES_SEG, 0, 3, "Café", 2, 0, PAGE_3);
    var badSpan = development(1, FindingKind.ACTION, Status.RECORDED, dates, List.of(bad));
    assertThrows(IllegalArgumentException.class, () -> placed(new Dev(badSpan, List.of())));
    var emptyLink = link(NAV, Basis.INFERRED);
    assertThrows(IllegalArgumentException.class, () -> placed(proposal(1, JAN_15, emptyLink)));
  }

  @Test
  void rejectsInconsistentFindings() {
    var inferred = link(NAV, Basis.INFERRED, minutes(0, "Council "));
    assertThrows(
        IllegalArgumentException.class, () -> views(proposal(1, JAN_15, stated(NAV), inferred)));
    var noHistory = link(id(999), Basis.INFERRED, minutes(0, "Council "));
    assertThrows(IllegalArgumentException.class, () -> views(proposal(1, JAN_15, noHistory)));
    var dev = proposal(1, JAN_15);
    var stray =
        new Association(id(4242), NAV, Basis.INFERRED, List.of(minutes(0, "Council ")));
    var orphan = revision(JAN, JAN_15, List.of(dev.development()), List.of(), List.of(stray));
    assertThrows(IllegalArgumentException.class, () -> views(orphan));
    var twice =
        revision(
            JAN,
            JAN_15,
            List.of(dev.development(), dev.development()),
            List.of(),
            List.of());
    assertThrows(IllegalArgumentException.class, () -> views(twice));
  }

  @Test
  void rejectsTwoRevisionsOfOneMeeting() {
    var first = meeting(JAN, JAN_15, proposal(1, JAN_15));
    var second = meeting(JAN, JAN_15, proposal(2, JAN_15));
    assertThrows(IllegalArgumentException.class, () -> views(first, second));
  }

  @Test
  void rejectsADevelopmentSavedInTwoRevisions() {
    var dev = proposal(1, JAN_15);
    var jan = meeting(JAN, JAN_15, dev);
    var feb =
        revision(
            FEB,
            FEB_3,
            List.of(
                development(
                    1,
                    FindingKind.PROPOSAL,
                    Status.PROPOSED,
                    List.of(day(Role.MEETING, "2026-02-03")),
                    dev.development().evidence())),
            List.of(),
            List.of());
    assertThrows(IllegalArgumentException.class, () -> views(jan, feb));
  }
}
