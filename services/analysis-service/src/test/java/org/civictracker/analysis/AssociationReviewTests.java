package org.civictracker.analysis;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;
import org.civictracker.analysis.domain.CandidateValidator;
import org.civictracker.analysis.domain.Candidates;
import org.civictracker.analysis.evaluation.AssociationReview;
import org.junit.jupiter.api.Test;

class AssociationReviewTests {
  private final String hash = "a".repeat(64);
  private final Candidates output =
      new Candidates(
          List.of(),
          List.of(),
          List.of(),
          List.of(
              new Candidates.Association("d1", "i1", Candidates.Basis.INFERRED, List.of("e1"))));

  private AssociationReview.ReferenceSet references(boolean approved) {
    return new AssociationReview.ReferenceSet(
        "DEVELOPMENT", hash, "first", "second", approved, List.of("r1", "r2"));
  }

  @Test
  void missingEvidenceStaysInTheSupportDenominator() {
    var metrics =
        new AssociationReview()
            .score(
                output,
                new CandidateValidator.Result(
                    List.of(
                        new org.civictracker.analysis.domain.Problem(
                            org.civictracker.analysis.domain.Problem.Code.MISSING_EVIDENCE,
                            "evidence")),
                    java.util.Set.of()),
                hash,
                references(true),
                "reviewer",
                List.of(
                    new AssociationReview.Match(
                        "d1", "i1", "r1", AssociationReview.Judgment.CORRECT, false)));
    assertEquals(1.0, metrics.precision().value());
    assertEquals(.5, metrics.recall().value());
    assertEquals(0.0, metrics.evidencePresence().value());
    assertEquals(0.0, metrics.evidenceSupport().value());
  }

  @Test
  void cannotScoreUnapprovedOrUnresolvedHumanJudgments() {
    var review = new AssociationReview();
    var validation = new CandidateValidator.Result(List.of(), java.util.Set.of());
    assertThrows(
        IllegalArgumentException.class,
        () -> review.score(output, validation, hash, references(false), "reviewer", List.of()));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            review.score(
                output,
                validation,
                hash,
                references(true),
                "reviewer",
                List.of(
                    new AssociationReview.Match(
                        "d1", "i1", null, AssociationReview.Judgment.UNRESOLVED, false))));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            review.score(
                output,
                validation,
                hash,
                references(true),
                "reviewer",
                List.of(
                    new AssociationReview.Match(
                        "d1", "i1", "r1", AssociationReview.Judgment.CORRECT, true))));
  }

  @Test
  void rejectsMultiplePredictionsMatchedToTheSameReference() {
    var two =
        new Candidates(
            List.of(),
            List.of(),
            List.of(),
            List.of(
                output.associations().getFirst(),
                new Candidates.Association("d2", "i1", Candidates.Basis.INFERRED, List.of("e1"))));
    var validation = new CandidateValidator.Result(List.of(), java.util.Set.of("e1"));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            new AssociationReview()
                .score(
                    two,
                    validation,
                    hash,
                    references(true),
                    "reviewer",
                    List.of(
                        new AssociationReview.Match(
                            "d1", "i1", "r1", AssociationReview.Judgment.CORRECT, true),
                        new AssociationReview.Match(
                            "d2", "i1", "r1", AssociationReview.Judgment.CORRECT, true))));
  }
}
