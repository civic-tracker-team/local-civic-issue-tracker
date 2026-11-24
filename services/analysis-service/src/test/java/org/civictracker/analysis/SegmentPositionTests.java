package org.civictracker.analysis;

import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.civictracker.analysis.application.DevelopmentKinds;
import org.civictracker.analysis.application.FindingsStore;
import org.civictracker.analysis.application.PriorContext;
import org.civictracker.analysis.application.SaveRejected;
import org.civictracker.analysis.domain.Candidates;
import org.civictracker.analysis.domain.CitationIndex.Span;
import org.civictracker.analysis.domain.FindingKind;
import org.civictracker.analysis.domain.MeetingInput;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

@SpringBootTest
class SegmentPositionTests {
  @Autowired FindingsStore store;
  @Autowired JdbcTemplate jdbc;

  record Fixture(
      FindingsStoreTests.Fixture base,
      MeetingInput input,
      Candidates candidates,
      DevelopmentKinds kinds,
      UUID alternative) {}

  Fixture fixture() {
    var f = new FindingsStoreTests.Fixture();
    var source = f.input.sources().getFirst();
    var unit = source.units().getFirst();
    UUID alternative = UUID.randomUUID(), last = UUID.randomUUID();
    var segments =
        List.of(
            unit.segments().getFirst(),
            new MeetingInput.Segment(alternative, "Alternative", "OCR", f.pdf, null, false),
            new MeetingInput.Segment(last, "Next line", "OCR", f.pdf, null, true));
    var replacement =
        new MeetingInput.Source(
            source.documentId(),
            source.versionId(),
            source.artifactId(),
            source.sha256(),
            source.byteLength(),
            source.preparationId(),
            source.profileFingerprint(),
            source.sourceUrls(),
            List.of(
                new MeetingInput.Unit(
                    unit.ordinal(),
                    unit.disposition(),
                    unit.nativeText(),
                    segments,
                    unit.notes())));
    var input =
        new MeetingInput(
            f.input.meetingId(),
            f.input.officialId(),
            f.input.meetingDate(),
            f.input.title(),
            List.of(replacement, f.input.sources().getLast()),
            List.of());
    var evidence = new ArrayList<>(f.candidates.evidence());
    evidence.add(
        new Candidates.Evidence("e3", new Span(f.d1, f.v1, f.p1, f.hash, last, 0, 9, "Next line")));
    var first = f.candidates.developments().getFirst();
    var developments =
        List.of(
            new Candidates.Development(
                first.id(),
                first.description(),
                first.status(),
                first.dates(),
                List.of("e9", "e2", "e3")),
            f.candidates.developments().getLast());
    var candidates =
        new Candidates(evidence, developments, f.candidates.issues(), f.candidates.associations());
    var kinds =
        new DevelopmentKinds(
            List.of(
                new DevelopmentKinds.Entry(developments.getFirst(), FindingKind.ACTION),
                new DevelopmentKinds.Entry(developments.getLast(), FindingKind.PROPOSAL)));
    return new Fixture(f, input, candidates, kinds, alternative);
  }

  @Test
  void rejectsEvidenceForUnselectedSegmentWithoutSavingAnything() {
    var f = fixture();
    var evidence = new ArrayList<>(f.candidates().evidence());
    evidence.set(
        evidence.size() - 1,
        new Candidates.Evidence(
            "e3",
            new Span(
                f.base().d1,
                f.base().v1,
                f.base().p1,
                f.base().hash,
                f.alternative(),
                0,
                11,
                "Alternative")));
    var bad =
        new Candidates(
            evidence,
            f.candidates().developments(),
            f.candidates().issues(),
            f.candidates().associations());
    var before = counts();
    var error =
        assertThrows(
            SaveRejected.class,
            () ->
                store.save(
                    f.input(), bad, PriorContext.Snapshot.empty(), f.base().metadata, f.kinds()));
    assertEquals(SaveRejected.Code.UNKNOWN_EVIDENCE_SEGMENT, error.code());
    assertEquals(before, counts());
  }

  private List<Long> counts() {
    return AnalysisFoundationTests.FINDINGS.stream()
        .map(t -> jdbc.queryForObject("select count(*) from analysis." + t, Long.class))
        .toList();
  }

  @Test
  void roundTripCountsOnlySelectedSegmentsAndResetsForEachUnit() {
    var f = fixture();
    var id =
        store.save(
            f.input(), f.candidates(), PriorContext.Snapshot.empty(), f.base().metadata, f.kinds());
    var saved = store.read(id);
    var spans = saved.developments().getFirst().evidence();
    assertEquals(List.of(7, 0, 7), spans.stream().map(s -> s.unitOrdinal()).toList());
    assertEquals(List.of(0, 0, 1), spans.stream().map(s -> s.segmentOrdinal()).toList());
    assertEquals(List.of("😀B", "Beta", "Next line"), spans.stream().map(s -> s.text()).toList());
    assertEquals(0, saved.developments().getLast().evidence().getFirst().segmentOrdinal());
    assertEquals(saved, store.read(id));
    for (var issue : saved.issues()) {
      var history = store.issueHistory(issue.issueId());
      for (var development : history.revisions().getFirst().developments())
        assertEquals(
            saved.developments().stream()
                .filter(d -> d.id().equals(development.id()))
                .findFirst()
                .orElseThrow(),
            development);
    }
  }

  @Test
  void databaseRejectsMissingOrNegativeSegmentPosition() {
    var f = fixture();
    var revision =
        store.save(
            f.input(), f.candidates(), PriorContext.Snapshot.empty(), f.base().metadata, f.kinds());
    var before = counts();
    for (Integer position : new Integer[] {null, -1}) {
      assertThrows(
          org.springframework.dao.DataIntegrityViolationException.class,
          () ->
              jdbc.update(
                  "insert into"
                      + " analysis.evidence_span(id,revision_id,local_label,ordinal,document_id,version_id,preparation_id,profile_fingerprint,segment_id,start,\"end\",text,unit_ordinal,segment_ordinal,locator_json)"
                      + " select"
                      + " ?,revision_id,'invalid-position',99,document_id,version_id,preparation_id,profile_fingerprint,segment_id,start,\"end\",text,unit_ordinal,?,locator_json"
                      + " from analysis.evidence_span where revision_id=? order by ordinal limit 1",
                  UUID.randomUUID(),
                  position,
                  revision));
      assertEquals(before, counts());
    }
  }
}
