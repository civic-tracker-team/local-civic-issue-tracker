package org.civictracker.records.domain;

public final class IdentityRules {
  private IdentityRules() {}

  public static boolean canAdvance(boolean currentSource, java.time.Instant observed,
      java.time.Instant retained) {
    return currentSource && (retained == null || observed.isAfter(retained));
  }
}
