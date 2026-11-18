package org.civictracker.analysis.evaluation;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.civictracker.analysis.domain.AssociationMetrics;
import org.civictracker.analysis.domain.CandidateValidator;
import org.civictracker.analysis.domain.Candidates;

/** Scores completed human judgments without treating unresolved cases as negatives. */
public final class AssociationReview {
  private record Pair(String developmentId, String issueId) {}

  public enum Judgment {
    CORRECT,
    INCORRECT,
    UNRESOLVED
  }

  public record ReferenceSet(
      String split,
      String inputSha256,
      String reviewer,
      String secondReviewer,
      boolean approved,
      List<String> associationIds) {
    public ReferenceSet {
      associationIds = List.copyOf(associationIds);
    }
  }

  public record Match(
      String developmentId,
      String issueId,
      String referenceId,
      Judgment judgment,
      boolean evidenceSupported) {}

  public AssociationMetrics score(
      Candidates output,
      CandidateValidator.Result validation,
      String inputSha256,
      ReferenceSet reference,
      String reviewer,
      List<Match> matches) {
    if (!reference.approved()
        || blank(reviewer)
        || blank(reference.reviewer())
        || blank(reference.secondReviewer())
        || reference.secondReviewer().equals(reference.reviewer())
        || !reference.inputSha256().equals(inputSha256)
        || !(reference.split().equals("DEVELOPMENT") || reference.split().equals("FINAL"))) {
      throw new IllegalArgumentException(
          "Approved, independently reviewed references must match the frozen input");
    }
    Set<String> references = new HashSet<>(reference.associationIds());
    if (references.size() != reference.associationIds().size()
        || references.stream().anyMatch(this::blank))
      throw new IllegalArgumentException("Invalid reference identities");
    var seen = new HashSet<Pair>();
    var matched = new HashSet<String>();
    int correct = 0;
    int supported = 0;
    int usable =
        (int)
            output.associations().stream()
                .filter(a ->
                    !a.evidenceIds().isEmpty()
                        && new HashSet<>(a.evidenceIds()).size() == a.evidenceIds().size()
                        && validation.usableEvidenceIds().containsAll(a.evidenceIds()))
                .count();
    for (var match : matches) {
      var key = new Pair(match.developmentId(), match.issueId());
      if (!seen.add(key)
          || match.judgment() == null
          || match.judgment() == Judgment.UNRESOLVED
          || output.associations().stream()
              .noneMatch(a ->
                  a.developmentId().equals(match.developmentId())
                      && a.issueId().equals(match.issueId())))
        throw new IllegalArgumentException(
            "Every returned association requires one resolved judgment");
      if (match.judgment() == Judgment.CORRECT) {
        if (!references.contains(match.referenceId()) || !matched.add(match.referenceId()))
          throw new IllegalArgumentException(
              "Correct matches must be one-to-one reference associations");
        correct++;
      } else if (match.referenceId() != null) {
        throw new IllegalArgumentException("Incorrect matches have no matched reference");
      }
      if (match.evidenceSupported()) {
        var association =
            output.associations().stream()
                .filter(a ->
                    a.developmentId().equals(match.developmentId())
                        && a.issueId().equals(match.issueId()))
                .findFirst()
                .orElseThrow();
        if (association.evidenceIds().isEmpty()
            || !validation.usableEvidenceIds().containsAll(association.evidenceIds()))
          throw new IllegalArgumentException("Unusable evidence cannot be scored as supported");
        supported++;
      }
    }
    if (matches.size() != output.associations().size())
      throw new IllegalArgumentException("Incomplete association review");
    return new AssociationMetrics(
        output.associations().size(),
        references.size(),
        correct,
        usable,
        matches.size(),
        supported);
  }

  private boolean blank(String value) {
    return value == null || value.isBlank();
  }
}
