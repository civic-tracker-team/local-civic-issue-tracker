package org.civictracker.coordinator.run;

public final class PlanRejected extends Exception {
  public final String code;

  public PlanRejected(String code) {
    super(code);
    this.code = code;
  }
}
