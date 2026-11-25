package org.civictracker.coordinator.owners;

/** No usable owner response was received. Reconciliation can try again. */
public final class OwnerUnreachable extends Exception {
  public OwnerUnreachable() {
    super("Owner response is unavailable or invalid");
  }
}
