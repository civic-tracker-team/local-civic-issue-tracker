package org.civictracker.records.application;

/** Carries a safe application code without exposing source or database errors. */
public class OperationProblem extends RuntimeException {
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
