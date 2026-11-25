package org.civictracker.coordinator.run;

import java.time.LocalDate;
import java.util.Objects;

public record DateRange(LocalDate from, LocalDate to) {
  public DateRange {
    Objects.requireNonNull(from);
    Objects.requireNonNull(to);
    if (to.isBefore(from)) {
      throw new IllegalArgumentException("Reversed date range");
    }
  }

  public boolean overlaps(DateRange other) {
    return !to.isBefore(other.from) && !other.to.isBefore(from);
  }
}
