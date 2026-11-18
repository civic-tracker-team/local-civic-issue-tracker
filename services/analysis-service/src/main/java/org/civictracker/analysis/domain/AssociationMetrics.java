package org.civictracker.analysis.domain;

/** Counts come from reviewed, one-to-one association matches in the same scope. */
public record AssociationMetrics(
    int returned, int reference, int correct, int usableEvidence, int reviewed, int supported) {
  public AssociationMetrics {
    if (returned < 0
        || reference < 0
        || correct < 0
        || usableEvidence < 0
        || reviewed < 0
        || supported < 0
        || correct > returned
        || correct > reference
        || usableEvidence > returned
        || reviewed > returned
        || supported > reviewed
        || supported > usableEvidence)
      throw new IllegalArgumentException("Inconsistent review counts");
  }

  public record Ratio(int numerator, int denominator, Double value) {}

  private Ratio ratio(int numerator, int denominator) {
    return new Ratio(
        numerator, denominator, denominator == 0 ? null : (double) numerator / denominator);
  }

  public Ratio precision() {
    return ratio(correct, returned);
  }

  public Ratio recall() {
    return ratio(correct, reference);
  }

  public Ratio evidencePresence() {
    return ratio(usableEvidence, returned);
  }

  public Ratio evidenceSupport() {
    return ratio(supported, reviewed);
  }
}
