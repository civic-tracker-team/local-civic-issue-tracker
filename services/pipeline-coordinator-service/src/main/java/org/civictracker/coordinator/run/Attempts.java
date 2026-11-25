package org.civictracker.coordinator.run;

public record Attempts(int acquire, int prepare, int analyze) {
  public static final Attempts FIRST = new Attempts(1, 1, 1);

  public Attempts {
    if (acquire < 1 || prepare < 1 || analyze < 1) {
      throw new IllegalArgumentException("Attempts must be positive");
    }
  }
}
