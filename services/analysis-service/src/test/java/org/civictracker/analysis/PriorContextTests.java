package org.civictracker.analysis;

import static org.junit.jupiter.api.Assertions.*;

import java.time.LocalDate;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.UUID;
import org.civictracker.analysis.application.FindingsStore;
import org.civictracker.analysis.application.PriorContext;
import org.civictracker.analysis.domain.Candidates;
import org.civictracker.analysis.domain.Candidates.*;
import org.civictracker.analysis.domain.MeetingInput;
import org.civictracker.analysis.domain.SavedFindings;
import org.civictracker.analysis.evaluation.EvaluationJson;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

@SpringBootTest
class PriorContextTests {
  @Autowired FindingsStore store;
  @Autowired PriorContext contexts;
  @Autowired JdbcTemplate jdbc;
  final LocalDate current = LocalDate.of(2026, 1, 20);

  @Test
  void emptyContextIsDeterministicAndImmutable() {
    var context = contexts.build(List.of(), current);
    assertEquals(PriorContext.Snapshot.empty(), context);
    assertThrows(UnsupportedOperationException.class, () -> context.issues().clear());
    assertThrows(
        UnsupportedOperationException.class, () -> context.issueIds().put("p1", UUID.randomUUID()));
  }

  @Test
  void ordersFirstAppearancesByMeetingThenRevisionNotRequestOrder() {
    var a = seed("2026-01-10");
    var b = seed("2026-01-12");
    var expected =
        List.of(a, b).stream()
            .flatMap(id -> store.read(id).issues().stream())
            .map(SavedFindings.Issue::issueId)
            .toList();
    var result = contexts.build(List.of(b, a), current);
    assertEquals(
        List.of("p1", "p2", "p3", "p4"),
        result.issues().stream().map(MeetingInput.PriorIssue::id).toList());
    assertEquals(
        expected, result.issues().stream().map(i -> result.issueIds().get(i.id())).toList());
    assertEquals(result, contexts.build(List.of(a, b), current));
    assertTrue(result.issues().stream().allMatch(i -> i.snapshotSha256().matches("[0-9a-f]{64}")));
    assertEquals(
        1,
        result.issues().stream().map(MeetingInput.PriorIssue::snapshotSha256).distinct().count());
    var c = seed("2026-01-10");
    var ordered =
        List.of(a, c).stream().sorted(java.util.Comparator.comparing(UUID::toString)).toList();
    var tied = contexts.build(List.of(c, a), current);
    assertEquals(
        ordered.stream()
            .flatMap(id -> store.read(id).issues().stream())
            .map(SavedFindings.Issue::issueId)
            .toList(),
        tied.issues().stream().map(i -> tied.issueIds().get(i.id())).toList());
  }

  @Test
  void rejectsUnknownDuplicateAndNonEarlierRevisions() {
    var id = seed("2026-01-15");
    assertThrows(
        NoSuchElementException.class, () -> contexts.build(List.of(UUID.randomUUID()), current));
    assertEquals(
        "DUPLICATE_PRIOR_REVISION",
        assertThrows(IllegalArgumentException.class, () -> contexts.build(List.of(id, id), current))
            .getMessage());
    for (var date : List.of(LocalDate.of(2026, 1, 15), LocalDate.of(2026, 1, 14)))
      assertEquals(
          "PRIOR_REVISION_NOT_EARLIER",
          assertThrows(IllegalArgumentException.class, () -> contexts.build(List.of(id), date))
              .getMessage());
  }

  @Test
  void continuesUuidCreatesUnlinkedIssueAndUsesLatestMentionForNextContext() {
    var first = seed("2026-01-10");
    var earlier = store.read(first);
    var context = contexts.build(List.of(first), current);
    var f = new FindingsStoreTests.Fixture();
    var candidates =
        issues(
            f.candidates,
            List.of(
                new Issue("i4", "Lighting extension", "p1"),
                new Issue("i2", "Another road", null)));
    var second = save(f, "2026-01-20", context, candidates);
    var saved = store.read(second);
    assertEquals(earlier.issues().getFirst().issueId(), saved.issues().getFirst().issueId());
    assertTrue(saved.issues().getFirst().continued());
    assertFalse(saved.issues().getLast().continued());
    assertNotEquals(earlier.issues().getLast().issueId(), saved.issues().getLast().issueId());
    var next = contexts.build(List.of(second, first), current.plusDays(1));
    assertEquals(
        List.of("Lighting extension", "Road", "Another road"),
        next.issues().stream().map(MeetingInput.PriorIssue::description).toList());
    assertEquals(current, next.issues().getFirst().reviewedThrough());
    assertEquals(LocalDate.parse("2026-01-10"), next.issues().get(1).reviewedThrough());
    assertNotEquals(
        context.issues().getFirst().snapshotSha256(), next.issues().getFirst().snapshotSha256());
    assertEquals(next, contexts.build(List.of(first, second), current.plusDays(1)));
    var rows =
        jdbc.queryForList(
            "select prior_label, issue_id, description, reviewed_through, snapshot_sha256 from"
                + " analysis.prior_context where revision_id=? order by ordinal",
            second);
    assertEquals(context.issues().size(), rows.size());
    for (int n = 0; n < rows.size(); n++) {
      var prior = context.issues().get(n);
      assertEquals(prior.id(), rows.get(n).get("prior_label"));
      assertEquals(context.issueIds().get(prior.id()), rows.get(n).get("issue_id"));
      assertEquals(prior.description(), rows.get(n).get("description"));
      assertEquals(
          prior.reviewedThrough(),
          ((java.sql.Date) rows.get(n).get("reviewed_through")).toLocalDate());
      assertEquals(prior.snapshotSha256(), rows.get(n).get("snapshot_sha256"));
    }
  }

  @Test
  void keepsBothMentionsAndOneIssueEntryAndReadsHistoryAcrossRevisions() {
    var first = seed("2026-01-10");
    var context = contexts.build(List.of(first), current);
    var f = new FindingsStoreTests.Fixture();
    var candidates =
        issues(
            f.candidates,
            List.of(new Issue("i4", "First name", "p1"), new Issue("i2", "Latest name", "p1")));
    var second = save(f, "2026-01-20", context, candidates);
    var saved = store.read(second);
    var issueId = context.issueIds().get("p1");
    assertEquals(List.of(new SavedFindings.Issue(issueId, "First name", true)), saved.issues());
    assertEquals(
        2,
        jdbc.queryForObject(
            "select count(*) from analysis.issue_mention where revision_id=? and issue_id=?",
            Integer.class,
            second,
            issueId));
    var history = store.issueHistory(issueId);
    assertEquals("Latest name", history.issue().name());
    assertEquals(
        List.of(first, second), history.revisions().stream().map(r -> r.revisionId()).toList());
    var latest = history.revisions().getLast();
    assertEquals(saved.meetingId(), latest.meetingId());
    assertEquals(saved.meetingDate(), latest.meetingDate());
    assertEquals(List.of("First name", "Latest name"), latest.mentionNames());
    assertEquals(saved.developments(), latest.developments());
    assertEquals(saved.associations(), latest.associations());
    assertEquals(1, history.revisions().getFirst().developments().size());
    assertEquals(
        Basis.SOURCE_STATED, history.revisions().getFirst().associations().getFirst().basis());
    assertEquals(
        "😀B",
        history.revisions().getFirst().associations().getFirst().evidence().getFirst().text());
    assertEquals(
        history,
        EvaluationJson.MAPPER.readValue(
            EvaluationJson.MAPPER.writeValueAsString(history),
            org.civictracker.analysis.domain.IssueHistory.class));
    var next = contexts.build(List.of(first, second), current.plusDays(1));
    assertEquals("Latest name", next.issues().getFirst().description());
    assertThrows(NoSuchElementException.class, () -> store.issueHistory(UUID.randomUUID()));
  }

  @Test
  void mergesSameBasisLinksAfterResolvingLocalIssuesWithOrderedUniqueEvidence() {
    var first = seed("2026-01-10");
    var context = contexts.build(List.of(first), current);
    var f = new FindingsStoreTests.Fixture();
    var candidates = doubleLink(f, Basis.SOURCE_STATED);
    var second = save(f, "2026-01-20", context, candidates);
    var saved = store.read(second);
    assertEquals(1, saved.associations().size());
    var link = saved.associations().getFirst();
    assertEquals(context.issueIds().get("p1"), link.issueId());
    assertEquals(Basis.SOURCE_STATED, link.basis());
    assertEquals(List.of("e9", "e2", "e1").stream().map(f::evidence).toList(), link.evidence());
    assertEquals(1, saved.issues().size());
    var history = store.issueHistory(link.issueId());
    assertEquals(List.of(link), history.revisions().getLast().associations());
    assertEquals(1, history.revisions().getLast().developments().size());
  }

  @Test
  void conflictingResolvedLinkBasesRejectTheEntireSaveWithClearCode() {
    var first = seed("2026-01-10");
    var context = contexts.build(List.of(first), current);
    var f = new FindingsStoreTests.Fixture();
    var before = counts();
    var error =
        assertThrows(
            IllegalArgumentException.class,
            () -> save(f, "2026-01-20", context, doubleLink(f, Basis.INFERRED)));
    assertEquals("CONFLICTING_LINK_BASIS", error.getMessage());
    assertEquals(before, counts());
  }

  @Test
  void rejectsUnknownPriorLabelWithoutSaving() {
    var f = new FindingsStoreTests.Fixture();
    var bad = issues(f.candidates, List.of(new Issue("i4", "Lighting", "p999")));
    var before = counts();
    assertThrows(
        IllegalArgumentException.class,
        () -> save(f, "2026-01-20", PriorContext.Snapshot.empty(), bad));
    assertEquals(before, counts());
  }

  private Candidates doubleLink(FindingsStoreTests.Fixture f, Basis second) {
    return new Candidates(
        f.candidates.evidence(),
        f.candidates.developments(),
        List.of(new Issue("i4", "First name", "p1"), new Issue("i2", "Second name", "p1")),
        List.of(
            new Association("d9", "i4", Basis.SOURCE_STATED, List.of("e9", "e2")),
            new Association("d9", "i2", second, List.of("e2", "e1"))));
  }

  private Candidates issues(Candidates c, List<Issue> issues) {
    return new Candidates(c.evidence(), c.developments(), issues, c.associations());
  }

  private UUID seed(String date) {
    var f = new FindingsStoreTests.Fixture();
    return save(f, date, PriorContext.Snapshot.empty(), f.candidates);
  }

  private UUID save(
      FindingsStoreTests.Fixture f,
      String date,
      PriorContext.Snapshot context,
      Candidates candidates) {
    var input =
        new MeetingInput(
            UUID.randomUUID(),
            f.input.officialId(),
            LocalDate.parse(date),
            f.input.title(),
            f.input.sources(),
            context.issues());
    return store.save(input, candidates, context, f.metadata, f.kinds);
  }

  private List<Long> counts() {
    return AnalysisFoundationTests.FINDINGS.stream()
        .map(t -> jdbc.queryForObject("select count(*) from analysis." + t, Long.class))
        .toList();
  }
}
