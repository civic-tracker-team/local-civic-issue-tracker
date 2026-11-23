package org.civictracker.analysis.views;

import java.time.LocalDate;
import java.util.Objects;
import java.util.UUID;

/** Exact official text supporting a statement, located in the version it was quoted from. */
public record Passage(
    UUID recordId,
    UUID versionId,
    String recordLabel,
    UUID meetingId,
    LocalDate meetingDate,
    Location location,
    String text) {
  public Passage {
    Objects.requireNonNull(recordId);
    Objects.requireNonNull(versionId);
    Objects.requireNonNull(meetingId);
    Objects.requireNonNull(meetingDate);
    Objects.requireNonNull(location);
    Objects.requireNonNull(text);
  }

  public sealed interface Location permits PdfPage, HtmlItem {}

  /** A 1-based PDF page, with the printed page label when the source has one. */
  public record PdfPage(int page, String pageLabel) implements Location {
    public PdfPage {
      if (page < 1) throw new IllegalArgumentException("PDF pages are 1-based");
    }
  }

  public record HtmlItem(String itemLabel) implements Location {
    public HtmlItem {
      Objects.requireNonNull(itemLabel);
    }
  }

  /** Records-owned facts about where a prepared segment sits in its source version. */
  public interface Locator {
    Place locate(UUID versionId, UUID segmentId);
  }

  /** {@code recordLabel} may be null until the records read API exposes the portal title. */
  public record Place(String recordLabel, Location location) {
    public Place {
      Objects.requireNonNull(location);
    }
  }
}
