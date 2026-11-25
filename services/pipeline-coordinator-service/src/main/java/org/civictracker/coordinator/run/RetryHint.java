package org.civictracker.coordinator.run;

public enum RetryHint {
  LIKELY_TRANSIENT,
  MAY_REPEAT,
  DETERMINISTIC,
  UNKNOWN
}
