package org.civictracker.analysis.domain;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Maps model-facing lines to exact slices of immutable prepared text. */
public final class CitationIndex {
  public sealed interface Lookup permits Found, Invalid {}

  public record Found(List<Span> spans) implements Lookup {
    public Found {
      spans = List.copyOf(spans);
    }
  }

  public record Invalid(Problem.Code code) implements Lookup {}

  public record Line(int number, String text) {}

  public record Unit(
      String unit, String document, int ordinal, String disposition, List<Line> lines) {
    public Unit {
      lines = List.copyOf(lines);
    }
  }

  public record View(
      String officialId,
      LocalDate meetingDate,
      String title,
      List<Unit> units,
      List<MeetingInput.PriorIssue> priorIssues) {
    public View {
      units = List.copyOf(units);
      priorIssues = List.copyOf(priorIssues);
    }
  }

  public record Span(
      UUID documentId,
      UUID versionId,
      UUID preparationId,
      String profileFingerprint,
      UUID segmentId,
      int start,
      int end,
      String text) {}

  public record Binding(String unit, int line, int globalLine, Span source) {}

  public record Snapshot(List<Span> selectedSegments, List<Binding> lines) {
    public Snapshot {
      selectedSegments = List.copyOf(selectedSegments);
      lines = List.copyOf(lines);
    }
  }

  private final View view;
  private final Snapshot snapshot;
  private final int firstGlobalLine;
  private final Map<UUID, String> texts;

  private CitationIndex(
      View view, Snapshot snapshot, int firstGlobalLine, Map<UUID, String> texts) {
    this.view = view;
    this.snapshot = snapshot;
    this.firstGlobalLine = firstGlobalLine;
    this.texts = Map.copyOf(texts);
  }

  public static CitationIndex build(MeetingInput input) {
    return build(input, 1);
  }

  /** Uses a separate address space for synthetic prompt examples. */
  public static CitationIndex build(MeetingInput input, int firstGlobalLine) {
    if (firstGlobalLine < 1) throw new IllegalArgumentException("Positive line origin required");
    var views = new ArrayList<Unit>();
    var segments = new ArrayList<Span>();
    var bindings = new ArrayList<Binding>();
    var texts = new LinkedHashMap<UUID, String>();
    int document = 0;
    for (var source : input.sources()) {
      String documentRef = "D" + Math.addExact(firstGlobalLine, document++);
      for (var unit : source.units()) {
        String unitRef = "U" + (views.size() + 1);
        var lines = new ArrayList<Line>();
        for (var segment : unit.segments()) {
          if (!segment.selected()) continue;
          if (segment.id() == null
              || segment.text() == null
              || texts.putIfAbsent(segment.id(), segment.text()) != null)
            throw new IllegalArgumentException("Invalid or duplicate selected segment");
          String text = segment.text();
          segments.add(span(source, segment.id(), 0, text.codePointCount(0, text.length()), text));
          int start = 0, startPoint = 0;
          while (start < text.length()) {
            int newline = text.indexOf('\n', start);
            int end = newline < 0 ? text.length() : newline + 1;
            String line = text.substring(start, end);
            int endPoint = startPoint + line.codePointCount(0, line.length());
            var slice = span(source, segment.id(), startPoint, endPoint, line);
            int global = Math.addExact(firstGlobalLine, bindings.size());
            lines.add(new Line(global, line));
            bindings.add(new Binding(unitRef, lines.size(), global, slice));
            start = end;
            startPoint = endPoint;
          }
        }
        views.add(new Unit(unitRef, documentRef, unit.ordinal(), unit.disposition(), lines));
      }
    }
    return new CitationIndex(
        new View(
            input.officialId(), input.meetingDate(), input.title(), views, input.priorIssues()),
        new Snapshot(segments, bindings),
        firstGlobalLine,
        texts);
  }

  private static Span span(
      MeetingInput.Source source, UUID segment, int start, int end, String text) {
    return new Span(
        source.documentId(),
        source.versionId(),
        source.preparationId(),
        source.profileFingerprint(),
        segment,
        start,
        end,
        text);
  }

  public View view() {
    return view;
  }

  public List<Unit> units() {
    return view.units();
  }

  public Snapshot snapshot() {
    return snapshot;
  }

  public String selectedText(UUID segmentId) {
    return texts.get(segmentId);
  }

  public int lineCount() {
    return snapshot.lines().size();
  }

  public String unitForLine(int globalLine) {
    long offset = (long) globalLine - firstGlobalLine;
    return offset >= 0 && offset < lineCount() ? snapshot.lines().get((int) offset).unit() : null;
  }

  public Lookup resolveLines(int first, int last) {
    if (last < first) return new Invalid(Problem.Code.REVERSED_RANGE);
    long start = (long) first - firstGlobalLine, end = (long) last - firstGlobalLine;
    if (start < 0 || end >= lineCount()) return new Invalid(Problem.Code.LINE_OUT_OF_RANGE);
    if (!unitForLine(first).equals(unitForLine(last)))
      return new Invalid(Problem.Code.RANGE_CROSSES_UNITS);
    var result = new ArrayList<Span>();
    for (var binding : snapshot.lines().subList((int) start, (int) end + 1)) {
      var next = binding.source();
      var previous = result.isEmpty() ? null : result.getLast();
      if (previous != null
          && previous.segmentId().equals(next.segmentId())
          && previous.end() == next.start()) {
        result.set(
            result.size() - 1,
            new Span(
                previous.documentId(),
                previous.versionId(),
                previous.preparationId(),
                previous.profileFingerprint(),
                previous.segmentId(),
                previous.start(),
                next.end(),
                previous.text() + next.text()));
      } else result.add(next);
    }
    if (result.stream().allMatch(s -> isBlank(s.text())))
      return new Invalid(Problem.Code.BLANK_RANGE);
    return new Found(result);
  }

  public static boolean isBlank(String text) {
    return text.codePoints()
        .allMatch(
            c -> Character.isWhitespace(c) || Character.getType(c) == Character.SPACE_SEPARATOR);
  }
}
