package org.civictracker.analysis.views;

import java.time.LocalDate;
import java.util.Objects;
import java.util.UUID;

/**
 * Exact official text supporting a statement, located in the version it was quoted from. A
 * passage joined from adjacent lines separates them with a line break.
 */
public record Passage(
    UUID recordId,
    UUID versionId,
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

  /** Where the passage starts, taken from the Records locator saved with its evidence. */
  public sealed interface Location permits PdfPage, HtmlItem {}

  /** A 1-based PDF page, with the printed page label when the source has one. */
  public record PdfPage(int page, String pageLabel) implements Location {
    public PdfPage {
      if (page < 1) throw new IllegalArgumentException("PDF pages are 1-based");
    }
  }

  /**
   * An HTML element. {@code itemId} and {@code sectionId} are the portal's agenda item and section
   * identifiers when the element sits inside one; {@code path} always locates the element.
   */
  public record HtmlItem(String itemId, String sectionId, String path) implements Location {
    public HtmlItem {
      Objects.requireNonNull(path);
    }
  }
}
