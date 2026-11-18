package org.civictracker.analysis;

import static org.junit.jupiter.api.Assertions.*;

import java.time.LocalDate;
import java.util.*;
import org.civictracker.analysis.domain.*;
import org.civictracker.analysis.evaluation.EvaluationJson;
import org.civictracker.analysis.model.*;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;

class FindingsExamplesTests {
  private JsonNode example(int number) throws Exception {
    try (var stream =
        getClass().getResourceAsStream("/evaluation/examples/EX" + number + ".json")) {
      return EvaluationJson.MAPPER.readTree(stream);
    }
  }

  private MeetingInput fixture(JsonNode example) {
    var text = new StringBuilder();
    for (var line : example.path("source").path("lines"))
      text.append(line.path("text").asText()).append('\n');
    var id = UUID.randomUUID();
    var segment = new MeetingInput.Segment(id, text.toString(), "SYNTHETIC", Map.of(), null, true);
    var source =
        new MeetingInput.Source(
            id,
            id,
            id,
            "",
            0,
            id,
            "synthetic",
            List.of(),
            List.of(new MeetingInput.Unit(0, "SYNTHETIC", "", List.of(segment), List.of())));
    return new MeetingInput(
        id,
        "synthetic",
        LocalDate.of(2026, 4, 3),
        example.path("name").asText(),
        List.of(source),
        List.of());
  }

  private Interpretation.Interpreted accepted(int number) throws Exception {
    var example = example(number);
    var input = fixture(example);
    int first = example.path("source").path("lines").get(0).path("number").asInt();
    var index = CitationIndex.build(input, first);
    var result =
        assertInstanceOf(
            Interpretation.Interpreted.class,
            ModelContract.interpret(example.path("reply").toString(), index, input));
    assertTrue(result.accepted(), result.problems().toString());
    var live =
        ModelContract.request(CitationIndex.build(FindingsRegressionTests.input()))
            .messages()
            .getFirst()
            .get("content");
    assertTrue(live.contains(ModelContract.request(index).inputText()));
    return result;
  }

  @Test
  void agendaReceiveReportRecommendationIsAProposal() throws Exception {
    var result = accepted(1);
    var dev = result.candidates().developments().getFirst();
    assertEquals(Candidates.Status.PROPOSED, dev.status());
    assertEquals(
        "Proposed for decision: receive the Maple Park Footpath and Citywide Neighborhood Programs"
            + " update and provide direction",
        dev.description());
    assertEquals(1, result.candidates().issues().size());
    assertEquals("Maple Park Footpath", result.candidates().issues().getFirst().label());
    assertEquals("PROJECT", result.reply().path("issues").get(0).path("matterType").asText());
    assertEquals(1, result.candidates().associations().size());
    assertEquals(
        Candidates.Basis.SOURCE_STATED, result.candidates().associations().getFirst().basis());
    assertEquals(
        List.of(Candidates.Role.MEETING),
        dev.dates().stream().map(Candidates.DateClaim::role).toList());
    assertEquals(
        "NOT_STATED",
        result.reply().path("developments").get(0).path("finding").path("proposedBy").asText());
  }

  @Test
  void staffProposalPreservesItsGrantCondition() throws Exception {
    var result = accepted(2);
    var dev = result.candidates().developments().getFirst();
    assertTrue(dev.description().startsWith("Proposed by city staff for decision: "));
    assertTrue(dev.description().endsWith("subject to grant award"));
  }

  @Test
  void oneRecordedActionLinksTwoDistinctMatters() throws Exception {
    var result = accepted(3);
    assertEquals(1, result.candidates().developments().size());
    assertEquals(2, result.candidates().issues().size());
    assertEquals(2, result.candidates().associations().size());
    assertEquals(
        Candidates.Status.RECORDED, result.candidates().developments().getFirst().status());
    assertEquals(
        List.of(Candidates.Role.OCCURRENCE, Candidates.Role.MEETING),
        result.candidates().developments().getFirst().dates().stream()
            .map(Candidates.DateClaim::role)
            .toList());
    assertTrue(
        result
            .candidates()
            .developments()
            .getFirst()
            .description()
            .contains("seek confirmation of a proposed county contribution"));
  }

  @Test
  void earlierActionKeepsItsEarlierDate() throws Exception {
    var result = accepted(5);
    var dates = result.candidates().developments().getFirst().dates();
    assertEquals("2025-03-03", dates.getFirst().start());
    assertEquals("2026-04-03", dates.get(1).start());
  }

  @Test
  void procedureAbstentionAndPromptHygieneRemainValid() throws Exception {
    assertTrue(accepted(4).candidates().developments().isEmpty());
    var system =
        ModelContract.request(CitationIndex.build(FindingsRegressionTests.input()))
            .messages()
            .getFirst()
            .get("content");
    assertFalse(system.contains("{{GLOSSARY}}"));
    assertFalse(java.util.regex.Pattern.compile("\\bU[0-9]+").matcher(system).find());
    var matcher = java.util.regex.Pattern.compile("\\bL([0-9]+)").matcher(system);
    int count = 0;
    while (matcher.find()) {
      assertTrue(Integer.parseInt(matcher.group(1)) >= 90001);
      count++;
    }
    assertTrue(count > 0);
  }
}
