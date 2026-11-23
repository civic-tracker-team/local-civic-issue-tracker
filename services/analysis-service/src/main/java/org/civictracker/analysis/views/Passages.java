package org.civictracker.analysis.views;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Joins saved evidence spans into readable passages. Spans that continue or overlap the previous
 * span in the same segment become one passage, and a span already covered earlier is dropped.
 * Text is only ever copied from saved spans, never retyped or joined across segments.
 */
final class Passages {
  private Passages() {}

  private record Run(
      UUID documentId, UUID versionId, UUID segmentId, int start, int end, String text) {}

  static List<Passage> assemble(
      List<SavedFindings.Span> spans, SavedFindings.Development owner, Passage.Locator locator) {
    var runs = new ArrayList<Run>();
    for (var span : spans) {
      if (covered(runs, span)) continue;
      var last = runs.isEmpty() ? null : runs.getLast();
      if (last != null
          && last.segmentId().equals(span.segmentId())
          && last.versionId().equals(span.versionId())
          && span.start() >= last.start()
          && span.start() <= last.end()) {
        String tail = suffix(span.text(), last.end() - span.start());
        runs.set(
            runs.size() - 1,
            new Run(
                last.documentId(),
                last.versionId(),
                last.segmentId(),
                last.start(),
                span.end(),
                last.text() + tail));
      } else
        runs.add(
            new Run(
                span.documentId(),
                span.versionId(),
                span.segmentId(),
                span.start(),
                span.end(),
                span.text()));
    }
    return runs.stream()
        .map(
            run -> {
              var place = locator.locate(run.versionId(), run.segmentId());
              if (place == null)
                throw new IllegalStateException("No location for segment " + run.segmentId());
              return new Passage(
                  run.documentId(),
                  run.versionId(),
                  place.recordLabel(),
                  owner.meetingId(),
                  owner.meetingDate(),
                  place.location(),
                  run.text());
            })
        .toList();
  }

  private static boolean covered(List<Run> runs, SavedFindings.Span span) {
    return runs.stream()
        .anyMatch(
            run ->
                run.segmentId().equals(span.segmentId())
                    && run.versionId().equals(span.versionId())
                    && run.start() <= span.start()
                    && span.end() <= run.end());
  }

  /** The text after its first {@code skip} code points. */
  private static String suffix(String text, int skip) {
    return text.substring(text.offsetByCodePoints(0, skip));
  }
}
