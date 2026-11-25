package org.civictracker.coordinator.run;

import java.util.UUID;

public final class RunProblem extends RuntimeException {
  private final int status;
  private final String code;
  private final UUID activeRunId;

  public RunProblem(int status, String code) {
    this(status, code, null);
  }

  public RunProblem(int status, String code, UUID activeRunId) {
    super(code);
    this.status = status;
    this.code = code;
    this.activeRunId = activeRunId;
  }

  public int status() {
    return status;
  }

  public String code() {
    return code;
  }

  public UUID activeRunId() {
    return activeRunId;
  }
}
