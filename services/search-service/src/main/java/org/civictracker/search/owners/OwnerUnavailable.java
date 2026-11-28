package org.civictracker.search.owners;

/** An owner read could not supply a usable answer. The current index is left unchanged. */
public final class OwnerUnavailable extends Exception {
  private final String code;

  public OwnerUnavailable(String code, String message) {
    super(message);
    this.code = code;
  }

  public OwnerUnavailable(String code, String message, Throwable cause) {
    super(message, cause);
    this.code = code;
  }

  public String code() {
    return code;
  }
}
