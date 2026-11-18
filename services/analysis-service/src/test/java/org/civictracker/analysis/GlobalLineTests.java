package org.civictracker.analysis;

import static org.junit.jupiter.api.Assertions.*;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.civictracker.analysis.domain.*;
import org.civictracker.analysis.model.*;
import org.junit.jupiter.api.Test;

class GlobalLineTests {
  @Test
  void syntheticDocumentAliasesUseTheirReservedOriginWithoutChangingSources() {
    var input = input();
    var live = CitationIndex.build(input);
    var synthetic = CitationIndex.build(input, 90001);
    assertEquals("D1", live.units().getFirst().document());
    assertEquals("D90001", synthetic.units().getFirst().document());
    assertEquals(live.snapshot().selectedSegments(), synthetic.snapshot().selectedSegments());
  }

  private MeetingInput input() {
    var units =
        List.of("one\n\nlast\r\n", "😀 other").stream()
            .map(text ->
                new MeetingInput.Unit(
                    0,
                    "OCR",
                    "",
                    List.of(
                        new MeetingInput.Segment(
                            UUID.randomUUID(), text, "OCR", Map.of(), null, true)),
                    List.of()))
            .toList();
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
            units);
    return new MeetingInput(
        UUID.randomUUID(),
        "synthetic",
        LocalDate.of(2026, 1, 15),
        "Council",
        List.of(source),
        List.of());
  }

  private CitationIndex.Lookup lookup(CitationIndex index, int first, int last) throws Exception {
    return index.resolveLines(first, last);
  }

  @Test
  void globalNumberingCoversEveryIndexedLineInOrder() {
    var index = CitationIndex.build(input());
    String text = ModelContract.request(index).inputText();
    assertTrue(text.contains("L1 one\nL2\nL3 last\n"));
    assertTrue(text.contains("L4 😀 other\n"));
    assertTrue(text.contains("--- section 2 of D1 ---"));
    assertFalse(java.util.regex.Pattern.compile("\\bU[0-9]+").matcher(text).find());
  }

  @Test
  void crossSectionRangesAreRejectedWithoutSplitting() throws Exception {
    var result =
        assertInstanceOf(CitationIndex.Invalid.class, lookup(CitationIndex.build(input()), 3, 4));
    assertEquals("RANGE_CROSSES_UNITS", result.code().name());
    var input = input();
    String raw =
        """
        {"developments":[{"finding":{"kind":"PROPOSAL","proposedBy":"NOT_STATED","planned":[]},"does":"study",
        "evidence":[{"firstLine":3,"lastLine":4}],"links":[]}],"issues":[]}
        """;
    var outcome =
        assertInstanceOf(
            Interpretation.Unresolved.class,
            ModelContract.interpret(raw, CitationIndex.build(input), input));
    assertEquals(
        List.of(new Problem(Problem.Code.RANGE_CROSSES_UNITS, "developments[0].evidence[0]")),
        outcome.problems());
    assertTrue(outcome.traces().getFirst().spans().isEmpty());
  }

  @Test
  void invalidAndBlankRangesAreNotClampedOrRepaired() throws Exception {
    var index = CitationIndex.build(input());
    assertEquals(
        Problem.Code.LINE_OUT_OF_RANGE,
        assertInstanceOf(CitationIndex.Invalid.class, lookup(index, 0, 1)).code());
    assertEquals(
        Problem.Code.LINE_OUT_OF_RANGE,
        assertInstanceOf(CitationIndex.Invalid.class, lookup(index, 4, 5)).code());
    assertEquals(
        Problem.Code.REVERSED_RANGE,
        assertInstanceOf(CitationIndex.Invalid.class, lookup(index, 3, 1)).code());
    assertEquals(
        Problem.Code.BLANK_RANGE,
        assertInstanceOf(CitationIndex.Invalid.class, lookup(index, 2, 2)).code());
  }

  @Test
  void schemaUsesOnlyGlobalBoundsAndNoUnitField() {
    var schema = ModelContract.request(CitationIndex.build(input())).schema();
    var cite =
        schema
            .path("properties")
            .path("developments")
            .path("items")
            .path("properties")
            .path("evidence")
            .path("items");
    assertFalse(cite.path("properties").has("unit"));
    for (String field : List.of("firstLine", "lastLine")) {
      assertEquals(1, cite.path("properties").path(field).path("minimum").asInt());
      assertEquals(4, cite.path("properties").path(field).path("maximum").asInt());
    }
  }

  @Test
  void promptExamplesCannotPrimeLiveUnitOrLineNumbers() {
    String system =
        ModelContract.request(CitationIndex.build(input())).messages().get(0).get("content");
    assertFalse(java.util.regex.Pattern.compile("\\bU[0-9]+").matcher(system).find());
    var matcher = java.util.regex.Pattern.compile("\\bL([0-9]+)").matcher(system);
    int lines = 0;
    while (matcher.find()) {
      assertTrue(Integer.parseInt(matcher.group(1)) >= 90001);
      lines++;
    }
    assertTrue(lines > 0);
    assertFalse(system.contains("\"unit\""));
  }
}
