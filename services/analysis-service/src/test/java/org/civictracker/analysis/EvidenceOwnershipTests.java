package org.civictracker.analysis;

import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.civictracker.analysis.application.FindingsStore;
import org.civictracker.analysis.application.PriorContext;
import org.civictracker.analysis.domain.Candidates;
import org.civictracker.analysis.domain.CitationIndex.Span;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

@SpringBootTest
class EvidenceOwnershipTests {
  @Autowired FindingsStore store;
  @Autowired JdbcTemplate jdbc;

  @ParameterizedTest
  @ValueSource(strings = {"document", "version", "preparation"})
  void rejectsEvidenceWhoseOwnerDiffersFromItsInputSegment(String field) {
    var f = new FindingsStoreTests.Fixture();
    var evidence = new ArrayList<>(f.candidates.evidence());
    var first = evidence.getFirst();
    var span = first.source();
    evidence.set(
        0,
        new Candidates.Evidence(
            first.id(),
            new Span(
                field.equals("document") ? UUID.randomUUID() : span.documentId(),
                field.equals("version") ? UUID.randomUUID() : span.versionId(),
                field.equals("preparation") ? UUID.randomUUID() : span.preparationId(),
                span.profileFingerprint(),
                span.segmentId(),
                span.start(),
                span.end(),
                span.text())));
    var bad =
        new Candidates(
            evidence,
            f.candidates.developments(),
            f.candidates.issues(),
            f.candidates.associations());
    var before = counts();
    var error =
        assertThrows(
            IllegalArgumentException.class,
            () -> store.save(f.input, bad, PriorContext.Snapshot.empty(), f.metadata, f.kinds));
    assertEquals("EVIDENCE_SOURCE_MISMATCH", error.getMessage());
    assertEquals(before, counts());
  }

  private List<Long> counts() {
    return AnalysisFoundationTests.FINDINGS.stream()
        .map(t -> jdbc.queryForObject("select count(*) from analysis." + t, Long.class))
        .toList();
  }
}
