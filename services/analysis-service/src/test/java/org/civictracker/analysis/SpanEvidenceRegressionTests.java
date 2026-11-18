package org.civictracker.analysis;

import static org.junit.jupiter.api.Assertions.*;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.civictracker.analysis.domain.CandidateValidator;
import org.civictracker.analysis.domain.Candidates;
import org.civictracker.analysis.domain.CitationIndex;
import org.civictracker.analysis.domain.MeetingInput;
import org.civictracker.analysis.evaluation.EvaluationJson;
import org.junit.jupiter.api.Test;

class SpanEvidenceRegressionTests {
  private MeetingInput input(String text) {
    var segment = new MeetingInput.Segment(UUID.randomUUID(), text, "OCR", Map.of(), null, true);
    var source =
        new MeetingInput.Source(
            UUID.randomUUID(),
            UUID.randomUUID(),
            UUID.randomUUID(),
            "a".repeat(64),
            1,
            UUID.randomUUID(),
            "profile",
            List.of(),
            List.of(new MeetingInput.Unit(0, "OCR", "", List.of(segment), List.of())));
    return new MeetingInput(
        UUID.randomUUID(),
        "synthetic",
        LocalDate.of(2026, 1, 15),
        "Council",
        List.of(source),
        List.of());
  }

  private Candidates.Evidence evidence(CitationIndex.Span span) {
    return EvaluationJson.MAPPER.convertValue(
        Map.of("id", "e1", "source", span), Candidates.Evidence.class);
  }

  private boolean valid(MeetingInput input, CitationIndex.Span span) {
    return new CandidateValidator()
        .validate(new Candidates(List.of(evidence(span)), List.of(), List.of(), List.of()), input)
        .valid();
  }

  @Test
  void rejectsNbspOnlyRanges() {
    var index = CitationIndex.build(input("\u00a0\u2007\u202f\n"));
    assertEquals(
        org.civictracker.analysis.domain.Problem.Code.BLANK_RANGE,
        assertInstanceOf(CitationIndex.Invalid.class, index.resolveLines(1, 1)).code());
  }

  @Test
  void rejectsTamperedSpanTextRatherThanSearchingElsewhere() {
    var input = input("approved\nrejected");
    var actual =
        assertInstanceOf(CitationIndex.Found.class, CitationIndex.build(input).resolveLines(1, 1))
            .spans()
            .getFirst();
    assertTrue(valid(input, actual));
    var tampered =
        new CitationIndex.Span(
            actual.documentId(),
            actual.versionId(),
            actual.preparationId(),
            actual.profileFingerprint(),
            actual.segmentId(),
            actual.start(),
            actual.end(),
            "rejected");
    assertFalse(valid(input, tampered));
  }

  @Test
  void repeatedTextKeepsItsSourcePositionWithoutOccurrenceFields() {
    var input = input("😀 yes\nyes\nyes\n");
    var span =
        assertInstanceOf(CitationIndex.Found.class, CitationIndex.build(input).resolveLines(3, 3))
            .spans()
            .getFirst();
    var evidence = evidence(span);
    var json = EvaluationJson.MAPPER.valueToTree(evidence);
    assertEquals(10, json.path("source").path("start").asInt());
    assertEquals(14, json.path("source").path("end").asInt());
    assertFalse(json.has("occurrence"));
    assertFalse(json.has("quote"));
    assertTrue(valid(input, span));
  }
}
