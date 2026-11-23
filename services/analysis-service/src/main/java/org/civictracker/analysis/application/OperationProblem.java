package org.civictracker.analysis.application;

/** A safe operation failure code for internal callers. */
public final class OperationProblem extends RuntimeException {
  private final int status;
  private final String code;

  public OperationProblem(int status, String code) {
    super(code);
    this.status = status;
    this.code = code;
  }

  public int status() {
    return status;
  }

  public String code() {
    return code;
  }
}
