package org.civictracker.analysis.views;

import java.time.LocalDate;
import java.time.Year;
import java.time.YearMonth;
import java.util.Comparator;
import org.civictracker.analysis.domain.Candidates;

/**
 * Timeline date rules (ADR-0020, FR-29 to FR-32). A known OCCURRENCE date is shown; otherwise the
 * source meeting date is shown with the MEETING role, which readers see as "reported or
 * discussed on". PLANNED and PUBLICATION dates never become the timeline date.
 */
public final class TimelineDates {
  private TimelineDates() {}

  public static Candidates.DateClaim displayDate(SavedFindings.Development development) {
    return development.dates().stream()
            .filter(d -> d.role() == Candidates.Role.OCCURRENCE)
            .filter(d -> d.precision() != Candidates.Precision.UNKNOWN)
            .findFirst()
            .or(
                () ->
                    development.dates().stream()
                        .filter(d -> d.role() == Candidates.Role.MEETING)
                        .findFirst())
            .orElseThrow();
  }

  /**
   * Orders by the earliest day a date can mean, then the latest, then meeting date and source
   * order. This is a stable display order only: a month or range is not claimed to precede a day
   * inside it.
   */
  static final Comparator<SavedFindings.Development> ORDER =
      Comparator.<SavedFindings.Development, LocalDate>comparing(d -> earliest(displayDate(d)))
          .thenComparing(d -> latest(displayDate(d)))
          .thenComparing(SavedFindings.Development::meetingDate)
          .thenComparing(SavedFindings.Development::meetingId)
          .thenComparingInt(SavedFindings.Development::sourceOrder);

  static LocalDate earliest(Candidates.DateClaim date) {
    return first(date.start());
  }

  static LocalDate latest(Candidates.DateClaim date) {
    return last(date.precision() == Candidates.Precision.RANGE ? date.end() : date.start());
  }

  private static LocalDate first(String value) {
    return switch (value.length()) {
      case 4 -> Year.parse(value).atDay(1);
      case 7 -> YearMonth.parse(value).atDay(1);
      default -> LocalDate.parse(value);
    };
  }

  private static LocalDate last(String value) {
    return switch (value.length()) {
      case 4 -> Year.parse(value).atMonth(12).atEndOfMonth();
      case 7 -> YearMonth.parse(value).atEndOfMonth();
      default -> LocalDate.parse(value);
    };
  }
}
