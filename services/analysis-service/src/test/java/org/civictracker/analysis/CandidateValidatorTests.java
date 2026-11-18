package org.civictracker.analysis;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.civictracker.analysis.domain.AssociationMetrics;
import org.civictracker.analysis.domain.CandidateValidator;
import org.civictracker.analysis.domain.Candidates;
import org.civictracker.analysis.domain.CitationIndex;
import org.civictracker.analysis.domain.MeetingInput;
import org.civictracker.analysis.evaluation.EvaluationJson;
import org.junit.jupiter.api.Test;

class CandidateValidatorTests {
  private final UUID segmentId = UUID.randomUUID();

  private MeetingInput input(String text) {
    return new MeetingInput(
        UUID.randomUUID(),
        "official",
        LocalDate.of(2026, 1, 15),
        "Council",
        List.of(
            new MeetingInput.Source(
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                "a".repeat(64),
                10,
                UUID.randomUUID(),
                "profile",
                List.of("https://example.org/original"),
                List.of(
                    new MeetingInput.Unit(
                        0,
                        "NATIVE",
                        text,
                        List.of(
                            new MeetingInput.Segment(
                                segmentId, text, "PDF_NATIVE", Map.of("page", 1), null, true)),
                        List.of())))),
        List.of());
  }

  private Candidates values(MeetingInput input) {
    var span =
        assertInstanceOf(CitationIndex.Found.class, CitationIndex.build(input).resolveLines(1, 1))
            .spans()
            .getFirst();
    return values(span);
  }

  private Candidates values(CitationIndex.Span span) {
    return new Candidates(
        List.of(new Candidates.Evidence("e1", span)),
        List.of(
            new Candidates.Development(
                "d1",
                "An amendment is proposed",
                Candidates.Status.PROPOSED,
                List.of(),
                List.of("e1"))),
        List.of(new Candidates.Issue("i1", "Nicholson Park", null)),
        List.of(new Candidates.Association("d1", "i1", Candidates.Basis.INFERRED, List.of("e1"))));
  }

  @Test
  void validatesUnicodeCodePointSlicesRatherThanUtf16Positions() {
    var input = input("😀 It is recommended.");
    var full = values(input).evidence().getFirst().source();
    var span =
        new CitationIndex.Span(
            full.documentId(),
            full.versionId(),
            full.preparationId(),
            full.profileFingerprint(),
            full.segmentId(),
            8,
            19,
            "recommended");
    var result = new CandidateValidator().validate(values(span), input);
    assertTrue(result.valid());
    assertEquals(java.util.Set.of("e1"), result.usableEvidenceIds());
  }

  @Test
  void rejectsInvalidSpanBoundsAndSourceIdentity() {
    var input = input("yes yes");
    var full = values(input).evidence().getFirst().source();
    for (var span :
        List.of(
            new CitationIndex.Span(
                full.documentId(),
                full.versionId(),
                full.preparationId(),
                full.profileFingerprint(),
                full.segmentId(),
                -1,
                3,
                "yes"),
            new CitationIndex.Span(
                full.documentId(),
                full.versionId(),
                full.preparationId(),
                full.profileFingerprint(),
                full.segmentId(),
                0,
                0,
                "yes"),
            new CitationIndex.Span(
                full.documentId(),
                full.versionId(),
                full.preparationId(),
                full.profileFingerprint(),
                full.segmentId(),
                4,
                8,
                "yes"),
            new CitationIndex.Span(
                UUID.randomUUID(),
                full.versionId(),
                full.preparationId(),
                full.profileFingerprint(),
                full.segmentId(),
                0,
                3,
                "yes"),
            new CitationIndex.Span(
                full.documentId(),
                UUID.randomUUID(),
                full.preparationId(),
                full.profileFingerprint(),
                full.segmentId(),
                0,
                3,
                "yes"),
            new CitationIndex.Span(
                full.documentId(),
                full.versionId(),
                UUID.randomUUID(),
                full.profileFingerprint(),
                full.segmentId(),
                0,
                3,
                "yes"),
            new CitationIndex.Span(
                full.documentId(),
                full.versionId(),
                full.preparationId(),
                "different",
                full.segmentId(),
                0,
                3,
                "yes")))
      assertFalse(new CandidateValidator().validate(values(span), input).valid());
    var second =
        new CitationIndex.Span(
            full.documentId(),
            full.versionId(),
            full.preparationId(),
            full.profileFingerprint(),
            full.segmentId(),
            4,
            7,
            "yes");
    assertTrue(new CandidateValidator().validate(values(second), input).valid());
  }

  @Test
  void rejectsUnknownSegmentsAndInvalidAssociationReferences() {
    var input = input("approved");
    var original = values(input);
    var wrong =
        new Candidates(
            original.evidence(),
            original.developments(),
            original.issues(),
            List.of(
                new Candidates.Association(
                    "missing", "i1", Candidates.Basis.INFERRED, List.of("e1"))));
    assertFalse(new CandidateValidator().validate(wrong, input).valid());
    assertFalse(new CandidateValidator().validate(original, input("different source")).valid());
  }

  @Test
  void abstentionIsValidAndDoesNotMeanPerfectPrecision() {
    assertTrue(
        new CandidateValidator()
            .validate(new Candidates(List.of(), List.of(), List.of(), List.of()), input("none"))
            .valid());
    assertNull(new AssociationMetrics(0, 2, 0, 0, 0, 0).precision().value());
    assertEquals(0.0, new AssociationMetrics(0, 2, 0, 0, 0, 0).recall().value());
  }

  @Test
  void evidenceSupportDenominatorIncludesMissingEvidence() {
    var metrics = new AssociationMetrics(4, 3, 2, 3, 4, 2);
    assertEquals(.5, metrics.evidenceSupport().value());
    assertEquals(.75, metrics.evidencePresence().value());
    assertThrows(IllegalArgumentException.class, () -> new AssociationMetrics(1, 1, 1, 0, 1, 1));
  }

  @Test
  void preservesCoarseDatesAndRejectsInventedPrecision() {
    var input = input("planned");
    var v = values(input);
    for (var date :
        List.of(
            new Candidates.DateClaim(
                Candidates.Role.PLANNED, Candidates.Precision.MONTH, "2027-05", null, true),
            new Candidates.DateClaim(
                Candidates.Role.PLANNED, Candidates.Precision.UNKNOWN, null, null, false))) {
      var changed =
          new Candidates(
              v.evidence(),
              List.of(
                  new Candidates.Development(
                      "d1", "Plan", Candidates.Status.PROPOSED, List.of(date), List.of("e1"))),
              v.issues(),
              v.associations());
      assertTrue(new CandidateValidator().validate(changed, input).valid());
    }
    var invalid =
        new Candidates(
            v.evidence(),
            List.of(
                new Candidates.Development(
                    "d1",
                    "Plan",
                    Candidates.Status.PROPOSED,
                    List.of(
                        new Candidates.DateClaim(
                            Candidates.Role.PLANNED,
                            Candidates.Precision.DAY,
                            "2027-05",
                            null,
                            false)),
                    List.of("e1"))),
            v.issues(),
            v.associations());
    assertFalse(new CandidateValidator().validate(invalid, input).valid());
  }

  @Test
  void strictCandidateParsingRejectsExtraAndMissingFields() {
    assertThrows(
        RuntimeException.class,
        () ->
            EvaluationJson.MAPPER.readValue(
                "{\"evidence\":[],\"developments\":[],\"issues\":[],\"associations\":[],\"sql\":\"DROP"
                    + " TABLE\"}",
                Candidates.class));
    assertThrows(
        RuntimeException.class, () -> EvaluationJson.MAPPER.readValue("{}", Candidates.class));
  }

  @Test
  void duplicateEvidenceCannotBeCountedAsUsable() {
    var input = input("approved");
    var original = values(input);
    var duplicate =
        new Candidates(
            List.of(original.evidence().getFirst(), original.evidence().getFirst()),
            original.developments(),
            original.issues(),
            original.associations());
    var result = new CandidateValidator().validate(duplicate, input);
    assertFalse(result.valid());
    assertTrue(result.usableEvidenceIds().isEmpty());
  }
}
