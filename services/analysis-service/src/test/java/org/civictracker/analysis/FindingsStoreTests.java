package org.civictracker.analysis;

import static org.junit.jupiter.api.Assertions.*;

import java.sql.DriverManager;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.UUID;
import org.civictracker.analysis.application.FindingsStore;
import org.civictracker.analysis.application.PriorContext;
import org.civictracker.analysis.application.RevisionMetadata;
import org.civictracker.analysis.domain.Candidates;
import org.civictracker.analysis.domain.Candidates.*;
import org.civictracker.analysis.domain.CitationIndex.Span;
import org.civictracker.analysis.domain.MeetingInput;
import org.civictracker.analysis.domain.SavedFindings;
import org.civictracker.analysis.evaluation.EvaluationJson;
import org.civictracker.analysis.persistence.AnalysisRevision.Origin;
import org.civictracker.analysis.views.FindingViews;
import org.civictracker.analysis.views.Passage;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.env.Environment;
import org.springframework.jdbc.core.JdbcTemplate;

@SpringBootTest
class FindingsStoreTests {
  @Autowired FindingsStore store;
  @Autowired JdbcTemplate jdbc;
  @Autowired Environment environment;

  @Test
  void roundTripPreservesExactValuesAndAllOrdersWithUuidIdentities() {
    var f = new Fixture();
    UUID revision =
        store.save(f.input, f.candidates, PriorContext.Snapshot.empty(), f.metadata, f.kinds);
    var saved = store.read(revision);
    assertEquals(revision, saved.revisionId());
    assertEquals(f.input.meetingId(), saved.meetingId());
    assertEquals(f.input.officialId(), saved.officialId());
    assertEquals(f.input.title(), saved.title());
    assertEquals(f.input.meetingDate(), saved.meetingDate());
    assertEquals(f.metadata.contractRevision(), saved.contractRevision());
    assertEquals(f.metadata.profileFingerprint(), saved.profileFingerprint());
    assertEquals(saved, store.read(revision));
    assertEquals(2, saved.developments().size());
    for (int n = 0; n < 2; n++) {
      var expected = f.candidates.developments().get(n);
      var actual = saved.developments().get(n);
      assertEquals(4, actual.id().version());
      assertEquals(expected.description(), actual.description());
      assertEquals(f.kinds.entries().get(n).kind(), actual.kind());
      assertEquals(expected.status(), actual.status());
      assertEquals(expected.dates(), actual.dates());
      assertEquals(expected.evidenceIds().stream().map(f::evidence).toList(), actual.evidence());
      assertEquals(actual.id(), idFor("development", "local_label", expected.id(), revision));
    }
    assertNotEquals(saved.developments().get(0).id(), saved.developments().get(1).id());
    assertEquals(
        List.of("Lighting", "Road"),
        saved.issues().stream().map(SavedFindings.Issue::name).toList());
    assertTrue(saved.issues().stream().noneMatch(SavedFindings.Issue::continued));
    for (int n = 0; n < 2; n++) {
      var expected = f.candidates.associations().get(n);
      var actual = saved.associations().get(n);
      assertEquals(
          idFor("development", "local_label", expected.developmentId(), revision),
          actual.developmentId());
      assertEquals(
          idFor("issue_mention", "local_label", expected.issueId(), revision, "issue_id"),
          actual.issueId());
      assertEquals(expected.basis(), actual.basis());
      assertEquals(expected.evidenceIds().stream().map(f::evidence).toList(), actual.evidence());
    }
    for (String table : List.of("evidence_span", "development", "issue_mention", "association")) {
      var ids =
          jdbc.queryForList(
              "select id from analysis." + table + " where revision_id=?", UUID.class, revision);
      assertFalse(ids.isEmpty());
      assertTrue(ids.stream().allMatch(id -> id.version() == 4));
    }
    assertEquals(
        f.metadata.inputSha256(),
        jdbc.queryForObject(
            "select input_sha256 from analysis.meeting_analysis where id=?",
            String.class,
            revision));
    assertEquals(
        f.metadata.receiptSha256(),
        jdbc.queryForObject(
            "select receipt_sha256 from analysis.meeting_analysis where id=?",
            String.class,
            revision));
    assertEquals(
        f.hash,
        jdbc.queryForObject(
            "select profile_fingerprint from analysis.evidence_span where revision_id=? and"
                + " local_label='e9'",
            String.class,
            revision));
    var json = EvaluationJson.MAPPER.writeValueAsString(saved);
    assertEquals(saved, EvaluationJson.MAPPER.readValue(json, SavedFindings.class));
    assertFalse(json.contains("localLabel"));
    assertFalse(json.contains("isNew"));
    assertThrows(UnsupportedOperationException.class, () -> saved.developments().clear());
    assertThrows(
        UnsupportedOperationException.class,
        () -> saved.developments().getFirst().evidence().getFirst().locator().put("page", 100));
  }

  @Test
  void aDatabaseFailureAfterTheRevisionInsertRollsBackEveryRow() throws Exception {
    var f = new Fixture();
    var before = counts();
    String suffix = UUID.randomUUID().toString().replace("-", "");
    String function = "analysis.fail_" + suffix;
    String trigger = "fail_" + suffix;
    try (var c =
            DriverManager.getConnection(
                environment.getProperty("ANALYSIS_DB_URL"),
                environment.getProperty("ANALYSIS_MIGRATION_USER"),
                environment.getProperty("ANALYSIS_MIGRATION_PASSWORD"));
        var s = c.createStatement()) {
      s.execute(
          "create function "
              + function
              + "() returns trigger language plpgsql as $$ begin if exists(select 1 from"
              + " analysis.meeting_analysis where id=NEW.revision_id and meeting_id='"
              + f.input.meetingId()
              + "') then raise exception 'synthetic failure after revision insert'; end if; return"
              + " NEW; end $$");
      s.execute(
          "create trigger "
              + trigger
              + " before insert on analysis.development for each row execute function "
              + function
              + "()");
      try {
        var error =
            assertThrows(
                RuntimeException.class,
                () ->
                    store.save(
                        f.input, f.candidates, PriorContext.Snapshot.empty(), f.metadata, f.kinds));
        assertTrue(causes(error).contains("synthetic failure after revision insert"));
        assertEquals(before, counts());
      } finally {
        s.execute("drop trigger " + trigger + " on analysis.development");
        s.execute("drop function " + function + "()");
      }
    }
  }

  @Test
  void unknownSegmentRejectsTheRevisionWithoutPartialRows() {
    var f = new Fixture();
    var first = f.candidates.evidence().getFirst();
    var s = first.source();
    var unknown =
        new Candidates.Evidence(
            first.id(),
            new Span(
                s.documentId(),
                s.versionId(),
                s.preparationId(),
                s.profileFingerprint(),
                UUID.randomUUID(),
                s.start(),
                s.end(),
                s.text()));
    var bad =
        new Candidates(
            List.of(unknown),
            f.candidates.developments(),
            f.candidates.issues(),
            f.candidates.associations());
    var before = counts();
    var error =
        assertThrows(
            IllegalArgumentException.class,
            () -> store.save(f.input, bad, PriorContext.Snapshot.empty(), f.metadata, f.kinds));
    assertTrue(error.getMessage().startsWith("Unknown evidence segment:"));
    assertEquals(before, counts());
  }

  @Test
  void missingLabelsAlsoRollBackAndUnknownRevisionIsExplicit() {
    var f = new Fixture();
    var bad =
        new Candidates(
            f.candidates.evidence(),
            f.candidates.developments(),
            f.candidates.issues(),
            List.of(new Association("absent", "i2", Basis.INFERRED, List.of("e2"))));
    var before = counts();
    assertThrows(
        IllegalArgumentException.class,
        () -> store.save(f.input, bad, PriorContext.Snapshot.empty(), f.metadata, f.kinds));
    assertEquals(before, counts());
    assertThrows(NoSuchElementException.class, () -> store.read(UUID.randomUUID()));
  }

  @Test
  void savedFindingsBuildViewsAfterTheirLocatorsRoundTripThroughJson() {
    var f = new Fixture();
    Map<String, Object> pdf =
        Map.of(
            "kind", "pdf", "page", 3, "pageLabel", "iii", "width", 612.0, "height", 792.0,
            "polygon", List.of(72.0, 90.5, 540.0, 90.5, 540.0, 130.0, 72.0, 130.0));
    Map<String, Object> html =
        Map.of("kind", "html", "itemId", "item-9", "sectionId", "section-2", "path", "/div[2]/p");
    String first = "Council approved\n", second = "the lighting contract.";
    String award = "Lighting contract award";
    var input =
        new MeetingInput(
            UUID.randomUUID(),
            "synthetic-views",
            LocalDate.of(2026, 1, 15),
            "Synthetic meeting",
            List.of(
                f.source(f.d1, f.v1, f.p1, f.s1, 7, first + second, pdf),
                f.source(f.d2, f.v2, f.p2, f.s2, 0, award, html)),
            List.of());
    int split = first.length(), end = split + second.length();
    var candidates =
        new Candidates(
            List.of(
                new Candidates.Evidence(
                    "e1", new Span(f.d1, f.v1, f.p1, f.hash, f.s1, 0, split, first)),
                new Candidates.Evidence(
                    "e2", new Span(f.d1, f.v1, f.p1, f.hash, f.s1, split, end, second)),
                new Candidates.Evidence(
                    "e3", new Span(f.d2, f.v2, f.p2, f.hash, f.s2, 0, award.length(), award))),
            List.of(
                new Development(
                    "d1",
                    "Council approved the lighting contract",
                    Status.RECORDED,
                    List.of(
                        new DateClaim(Role.MEETING, Precision.DAY, "2026-01-15", null, false),
                        new DateClaim(Role.OCCURRENCE, Precision.DAY, "2026-01-15", null, false)),
                    List.of("e1", "e2"))),
            List.of(new Issue("i1", "Street lighting", null)),
            List.of(new Association("d1", "i1", Basis.SOURCE_STATED, List.of("e3"))));
    var kinds =
        new org.civictracker.analysis.application.DevelopmentKinds(
            List.of(
                new org.civictracker.analysis.application.DevelopmentKinds.Entry(
                    candidates.developments().getFirst(),
                    org.civictracker.analysis.domain.FindingKind.ACTION)));
    var saved =
        store.read(
            store.save(input, candidates, PriorContext.Snapshot.empty(), f.metadata, kinds));

    var views = new FindingViews(List.of(saved));

    var development = views.meetingDevelopments(input.meetingId()).getFirst();
    assertEquals(Status.RECORDED, development.status());
    var passage = development.passages().getFirst();
    assertEquals(List.of(passage), development.passages());
    assertEquals(first + second, passage.text());
    assertEquals(f.d1, passage.recordId());
    assertEquals(new Passage.PdfPage(3, "iii"), passage.location());
    var link = development.issues().getFirst();
    assertEquals("Street lighting", link.issueName());
    assertEquals(
        new Passage.HtmlItem("item-9", "section-2", "/div[2]/p"),
        link.passages().getFirst().location());
    assertEquals(1, views.recordDevelopments(f.d1).size());
    assertTrue(views.recordDevelopments(f.d2).isEmpty());
  }

  @Test
  void emptyFindingsRemainACompleteReadableRevision() {
    var f = new Fixture();
    var id =
        store.save(
            f.input,
            new Candidates(List.of(), List.of(), List.of(), List.of()),
            PriorContext.Snapshot.empty(),
            f.metadata,
            new org.civictracker.analysis.application.DevelopmentKinds(List.of()));
    var saved = store.read(id);
    assertEquals(List.of(), saved.developments());
    assertEquals(List.of(), saved.issues());
    assertEquals(List.of(), saved.associations());
  }

  @Test
  void mismatchedPriorContextIsNotSilentlyDropped() {
    var f = new Fixture();
    var before = counts();
    var prior = new MeetingInput.PriorIssue("p1", "Road", LocalDate.of(2025, 1, 1), f.hash);
    assertThrows(
        IllegalArgumentException.class,
        () ->
            store.save(
                f.input,
                f.candidates,
                new PriorContext.Snapshot(List.of(prior), Map.of("p1", UUID.randomUUID())),
                f.metadata,
                f.kinds));
    assertEquals(before, counts());
  }

  private UUID idFor(String table, String column, String label, UUID revision) {
    return idFor(table, column, label, revision, "id");
  }

  private UUID idFor(String table, String column, String label, UUID revision, String idColumn) {
    return jdbc.queryForObject(
        "select "
            + idColumn
            + " from analysis."
            + table
            + " where revision_id=? and "
            + column
            + "=?",
        UUID.class,
        revision,
        label);
  }

  private List<Long> counts() {
    return AnalysisFoundationTests.FINDINGS.stream()
        .map(t -> jdbc.queryForObject("select count(*) from analysis." + t, Long.class))
        .toList();
  }

  private static String causes(Throwable e) {
    var text = new StringBuilder();
    for (var t = e; t != null; t = t.getCause()) text.append(t.getMessage()).append('\n');
    return text.toString();
  }

  static class Fixture {
    final String hash = "a".repeat(64);
    final UUID d1 = UUID.randomUUID(),
        v1 = UUID.randomUUID(),
        p1 = UUID.randomUUID(),
        s1 = UUID.randomUUID();
    final UUID d2 = UUID.randomUUID(),
        v2 = UUID.randomUUID(),
        p2 = UUID.randomUUID(),
        s2 = UUID.randomUUID();
    final Map<String, Object> pdf = Map.of("kind", "pdf", "page", 3, "pageLabel", "iii");
    final Map<String, Object> html = Map.of("kind", "html", "itemLabel", "A");
    final MeetingInput input =
        new MeetingInput(
            UUID.randomUUID(),
            "synthetic-42",
            LocalDate.of(2026, 1, 15),
            "Synthetic meeting",
            List.of(
                source(d1, v1, p1, s1, 7, "A😀B\nC", pdf), source(d2, v2, p2, s2, 0, "Beta", html)),
            List.of());
    final Candidates candidates =
        new Candidates(
            List.of(
                new Candidates.Evidence("e2", new Span(d2, v2, p2, hash, s2, 0, 4, "Beta")),
                new Candidates.Evidence("e9", new Span(d1, v1, p1, hash, s1, 1, 3, "😀B")),
                new Candidates.Evidence("e1", new Span(d1, v1, p1, hash, s1, 4, 5, "C"))),
            List.of(
                new Development(
                    "d9",
                    "Recorded lighting action",
                    Status.RECORDED,
                    List.of(
                        new DateClaim(Role.MEETING, Precision.DAY, "2026-01-15", null, false),
                        new DateClaim(
                            Role.PLANNED, Precision.RANGE, "2026-02-01", "2026-02-10", true),
                        new DateClaim(Role.PLANNED, Precision.UNKNOWN, null, null, false)),
                    List.of("e9", "e2")),
                new Development(
                    "d1",
                    "Road proposal",
                    Status.PROPOSED,
                    List.of(
                        new DateClaim(Role.PUBLICATION, Precision.MONTH, "2026-01", null, false)),
                    List.of("e1", "e2"))),
            List.of(new Issue("i4", "Lighting", null), new Issue("i2", "Road", null)),
            List.of(
                new Association("d1", "i2", Basis.INFERRED, List.of("e2", "e1")),
                new Association("d9", "i4", Basis.SOURCE_STATED, List.of("e9", "e2"))));
    final org.civictracker.analysis.application.DevelopmentKinds kinds =
        new org.civictracker.analysis.application.DevelopmentKinds(
            List.of(
                new org.civictracker.analysis.application.DevelopmentKinds.Entry(
                    candidates.developments().get(0),
                    org.civictracker.analysis.domain.FindingKind.ACTION),
                new org.civictracker.analysis.application.DevelopmentKinds.Entry(
                    candidates.developments().get(1),
                    org.civictracker.analysis.domain.FindingKind.PROPOSAL)));
    final RevisionMetadata metadata =
        new RevisionMetadata(
            Origin.IMPORTED,
            null,
            "grounded-findings-3",
            "{}",
            hash,
            "b".repeat(64),
            "c".repeat(64),
            "d".repeat(64),
            "e".repeat(64),
            123,
            "synthetic/" + UUID.randomUUID());

    MeetingInput.Source source(
        UUID d, UUID v, UUID p, UUID s, int ordinal, String text, Map<String, Object> locator) {
      return new MeetingInput.Source(
          d,
          v,
          UUID.randomUUID(),
          hash,
          100,
          p,
          hash,
          List.of("https://example.invalid/record"),
          List.of(
              new MeetingInput.Unit(
                  ordinal,
                  "NATIVE",
                  text,
                  List.of(new MeetingInput.Segment(s, text, "NATIVE", locator, null, true)),
                  List.of())));
    }

    SavedFindings.EvidenceSpan evidence(String id) {
      var e =
          candidates.evidence().stream()
              .filter(x -> x.id().equals(id))
              .findFirst()
              .orElseThrow()
              .source();
      return new SavedFindings.EvidenceSpan(
          e.documentId(),
          e.versionId(),
          e.preparationId(),
          e.segmentId(),
          e.start(),
          e.end(),
          e.text(),
          e.segmentId().equals(s1) ? 7 : 0,
          0,
          e.segmentId().equals(s1) ? pdf : html);
    }
  }
}
