package org.civictracker.coordinator.owners;

/** The owner rejected a request without accepting new work. */
public final class OwnerRejected extends Exception {
  public final int status;
  public final String code;

  public OwnerRejected(int status, String code) {
    super("Owner rejected request: " + code);
    this.status = status;
    this.code = code;
  }
}
