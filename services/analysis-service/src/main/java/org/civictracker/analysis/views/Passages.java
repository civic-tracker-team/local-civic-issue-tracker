package org.civictracker.analysis.views;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.civictracker.analysis.domain.SavedFindings.EvidenceSpan;

/**
 * Joins saved evidence spans, in citation order, into readable passages. A span joins the
 * passage before it when it continues or overlaps that passage's last span in the same segment,
 * or when it starts the next segment: same version and page or item ({@code unitOrdinal}), the
 * next {@code segmentOrdinal}, from offset 0, and the passage so far reached the end of its
 * segment. Segments joined this way are separated by a line break. A span already covered
 * earlier is dropped. Text is only ever copied from saved spans.
 *
 * <p>Evidence always covers whole model lines (ADR-0039), and {@code CitationIndex} keeps the
 * trailing line break on every line except a segment's last. So a passage that does not end in a
 * line break reached its segment's end, and one that does would skip the segment's remaining
 * lines if joined. A segment whose own text ends in a line break is never joined across, which
 * errs toward separate passages.
 */
final class Passages {
  private Passages() {}

  /** The part of one segment a passage covers. */
  private record Piece(UUID segmentId, int segmentOrdinal, int start, int end) {}

  private static final class Run {
    final EvidenceSpan first;
    final List<Piece> pieces = new ArrayList<>();
    final StringBuilder text;

    Run(EvidenceSpan span) {
      first = span;
      pieces.add(piece(span));
      text = new StringBuilder(span.text());
    }

    Piece last() {
      return pieces.getLast();
    }

    boolean covers(EvidenceSpan span) {
      return first.versionId().equals(span.versionId())
          && pieces.stream()
              .anyMatch(
                  p ->
                      p.segmentId().equals(span.segmentId())
                          && p.start() <= span.start()
                          && span.end() <= p.end());
    }

    /** True when the span continues or overlaps the last piece in its own segment. */
    boolean continues(EvidenceSpan span) {
      return first.versionId().equals(span.versionId())
          && last().segmentId().equals(span.segmentId())
          && span.start() >= last().start()
          && span.start() <= last().end();
    }

    /**
     * True when the span starts the segment after the last piece, on the same page or item, and
     * the last piece reached the end of its own segment.
     */
    boolean nextSegment(EvidenceSpan span) {
      return first.versionId().equals(span.versionId())
          && first.unitOrdinal() == span.unitOrdinal()
          && span.segmentOrdinal() == last().segmentOrdinal() + 1
          && span.start() == 0
          && !text.isEmpty()
          && text.charAt(text.length() - 1) != '\n';
    }
  }

  static List<Passage> assemble(List<EvidenceSpan> spans, MeetingDevelopment owner) {
    var runs = new ArrayList<Run>();
    for (var span : spans) {
      if (runs.stream().anyMatch(run -> run.covers(span))) continue;
      var run = runs.isEmpty() ? null : runs.getLast();
      if (run != null && run.continues(span)) {
        var last = run.last();
        run.text.append(suffix(span.text(), last.end() - span.start()));
        run.pieces.set(
            run.pieces.size() - 1,
            new Piece(last.segmentId(), last.segmentOrdinal(), last.start(), span.end()));
      } else if (run != null && run.nextSegment(span)) {
        run.text.append('\n').append(span.text());
        run.pieces.add(piece(span));
      } else runs.add(new Run(span));
    }
    return runs.stream()
        .map(
            run ->
                new Passage(
                    run.first.documentId(),
                    run.first.versionId(),
                    owner.meetingId(),
                    owner.meetingDate(),
                    location(run.first.locator()),
                    run.text.toString()))
        .toList();
  }

  /** Reads the Records locator saved with the span; an unknown shape fails instead of guessing. */
  static Passage.Location location(Map<String, Object> locator) {
    var kind = locator.get("kind");
    if ("pdf".equals(kind) && locator.get("page") instanceof Number page)
      return new Passage.PdfPage(page.intValue(), string(locator, "pageLabel"));
    if ("html".equals(kind) && locator.get("path") instanceof String path)
      return new Passage.HtmlItem(string(locator, "itemId"), string(locator, "sectionId"), path);
    throw new IllegalStateException("Unreadable evidence locator of kind " + kind);
  }

  private static String string(Map<String, Object> locator, String key) {
    var value = locator.get(key);
    if (value == null || value instanceof String) return (String) value;
    throw new IllegalStateException("Locator " + key + " is not text");
  }

  private static Piece piece(EvidenceSpan span) {
    return new Piece(span.segmentId(), span.segmentOrdinal(), span.start(), span.end());
  }

  /** The text after its first {@code skip} code points. */
  private static String suffix(String text, int skip) {
    return text.substring(text.offsetByCodePoints(0, skip));
  }
}
