package org.civictracker.analysis.views;

import java.time.LocalDate;
import java.time.Year;
import java.time.YearMonth;
import java.util.Comparator;
import org.civictracker.analysis.domain.Candidates;
import org.civictracker.analysis.domain.SavedFindings;

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
  static final Comparator<MeetingDevelopment> ORDER =
      Comparator.<MeetingDevelopment, LocalDate>comparing(
              d -> earliest(displayDate(d.development())))
          .thenComparing(d -> latest(displayDate(d.development())))
          .thenComparing(MeetingDevelopment::meetingDate)
          .thenComparing(MeetingDevelopment::meetingId)
          .thenComparingInt(MeetingDevelopment::sourceOrder);

  static LocalDate earliest(Candidates.DateClaim date) {
    return switch (date.precision()) {
      case YEAR -> Year.parse(date.start()).atDay(1);
      case MONTH -> YearMonth.parse(date.start()).atDay(1);
      case DAY, RANGE -> LocalDate.parse(date.start());
      case UNKNOWN -> throw new IllegalArgumentException("A date of unknown precision has no day");
    };
  }

  static LocalDate latest(Candidates.DateClaim date) {
    return switch (date.precision()) {
      case YEAR -> Year.parse(date.start()).atMonth(12).atEndOfMonth();
      case MONTH -> YearMonth.parse(date.start()).atEndOfMonth();
      case DAY -> LocalDate.parse(date.start());
      case RANGE -> LocalDate.parse(date.end());
      case UNKNOWN -> throw new IllegalArgumentException("A date of unknown precision has no day");
    };
  }
}
