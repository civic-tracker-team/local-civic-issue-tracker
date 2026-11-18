package org.civictracker.analysis;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.civictracker.analysis.domain.CitationIndex;
import org.civictracker.analysis.domain.MeetingInput;
import org.junit.jupiter.api.Test;

class CitationIndexTests {
  private MeetingInput input(List<MeetingInput.Segment> segments) {
    var source =
        new MeetingInput.Source(
            UUID.randomUUID(),
            UUID.randomUUID(),
            UUID.randomUUID(),
            "a".repeat(64),
            1,
            UUID.randomUUID(),
            "profile",
            List.of(),
            List.of(new MeetingInput.Unit(0, "NATIVE", "", segments, List.of())));
    return new MeetingInput(
        UUID.randomUUID(), "1", LocalDate.of(2026, 1, 15), "Council", List.of(source), List.of());
  }

  private MeetingInput.Segment segment(String text, boolean selected) {
    return new MeetingInput.Segment(
        UUID.randomUUID(), text, "PDF_NATIVE", Map.of("page", 1), null, selected);
  }

  @Test
  void preservesEverySelectedCharacterIncludingBlankLinesAndCrLf() {
    var selected = segment("😀 first\r\n\nlast\n", true);
    var input = input(List.of(selected, segment("not selected", false), segment("", true)));
    var index = CitationIndex.build(input);
    assertEquals(2, index.snapshot().selectedSegments().size());
    assertEquals(
        selected.text(),
        index.view().units().getFirst().lines().stream()
            .map(CitationIndex.Line::text)
            .collect(java.util.stream.Collectors.joining()));
    assertEquals(
        List.of(1, 2, 3),
        index.view().units().getFirst().lines().stream().map(CitationIndex.Line::number).toList());
    assertFalse(index.view().toString().contains("not selected"));
    var span =
        assertInstanceOf(CitationIndex.Found.class, index.resolveLines(3, 3)).spans().getFirst();
    assertEquals(10, span.start());
    assertEquals(15, span.end());
    assertEquals("last\n", span.text());
  }

  @Test
  void joinsNativeLinesWithoutChangingTheirBytes() {
    var selected = segment("one\r\ntwo\nthree", true);
    var index = CitationIndex.build(input(List.of(selected)));
    var resolved = assertInstanceOf(CitationIndex.Found.class, index.resolveLines(1, 3)).spans();
    assertEquals(1, resolved.size());
    assertEquals(selected.text(), resolved.getFirst().text());
    assertEquals(selected.id(), resolved.getFirst().segmentId());
  }

  @Test
  void keepsSeparateOcrSegmentsAsSeparateExactReferences() {
    var first = segment("Council directed staff", true);
    var second = segment("to study the property.", true);
    var index = CitationIndex.build(input(List.of(first, second)));
    var resolved = assertInstanceOf(CitationIndex.Found.class, index.resolveLines(1, 2)).spans();
    assertEquals(
        List.of(first.text(), second.text()),
        resolved.stream().map(CitationIndex.Span::text).toList());
    assertEquals(
        List.of(first.id(), second.id()),
        resolved.stream().map(CitationIndex.Span::segmentId).toList());
  }

  @Test
  void resolvesRepeatedTextByItsSelectedLocationRatherThanGuessing() {
    var index = CitationIndex.build(input(List.of(segment("yes\nyes\n", true))));
    var first =
        assertInstanceOf(CitationIndex.Found.class, index.resolveLines(1, 1)).spans().getFirst();
    var second =
        assertInstanceOf(CitationIndex.Found.class, index.resolveLines(2, 2)).spans().getFirst();
    assertEquals(0, first.start());
    assertEquals(first.text(), second.text());
    assertEquals(4, second.start());
    assertEquals(8, second.end());
  }

  @Test
  void rejectsInvalidAndWhitespaceOnlyRanges() {
    var index = CitationIndex.build(input(List.of(segment("one\n\n", true))));
    var cases =
        Map.of(
            List.of(0, 1), org.civictracker.analysis.domain.Problem.Code.LINE_OUT_OF_RANGE,
            List.of(2, 1), org.civictracker.analysis.domain.Problem.Code.REVERSED_RANGE,
            List.of(1, 3), org.civictracker.analysis.domain.Problem.Code.LINE_OUT_OF_RANGE,
            List.of(2, 2), org.civictracker.analysis.domain.Problem.Code.BLANK_RANGE);
    cases.forEach((range, code) ->
        assertEquals(
            code,
            assertInstanceOf(
                    CitationIndex.Invalid.class, index.resolveLines(range.get(0), range.get(1)))
                .code()));
  }

  @Test
  void aRangeCannotExtendIntoAnotherDocument() {
    var first = input(List.of(segment("first document", true)));
    var second = input(List.of(segment("second document", true)));
    var index =
        CitationIndex.build(
            new MeetingInput(
                first.meetingId(),
                "1",
                first.meetingDate(),
                "Council",
                List.of(first.sources().getFirst(), second.sources().getFirst()),
                List.of()));
    var a =
        assertInstanceOf(CitationIndex.Found.class, index.resolveLines(1, 1)).spans().getFirst();
    var b =
        assertInstanceOf(CitationIndex.Found.class, index.resolveLines(2, 2)).spans().getFirst();
    assertEquals("second document", b.text());
    assertFalse(a.documentId().equals(b.documentId()));
    assertEquals(
        org.civictracker.analysis.domain.Problem.Code.RANGE_CROSSES_UNITS,
        assertInstanceOf(CitationIndex.Invalid.class, index.resolveLines(1, 2)).code());
  }
}
