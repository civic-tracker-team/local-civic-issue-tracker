package org.civictracker.analysis;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import org.civictracker.analysis.domain.*;
import org.civictracker.analysis.model.*;
import org.junit.jupiter.api.Test;

class LinkMergeTests {
  private String link(String basis, int first, int last) {
    return "{\"issueId\":\"i1\",\"basis\":\""
        + basis
        + "\",\"evidence\":[{\"firstLine\":"
        + first
        + ",\"lastLine\":"
        + last
        + "}]}";
  }

  private String reply(String links) {
    return FindingsRegressionTests.reply(
        FindingsRegressionTests.ACTION,
        "[{\"id\":\"i1\",\"matterType\":\"PROJECT\",\"name\":\"Elm"
            + " shelter\",\"priorIssueId\":null}]",
        links);
  }

  private Interpretation.Interpreted interpreted(String raw) {
    var input = FindingsRegressionTests.input();
    return assertInstanceOf(
        Interpretation.Interpreted.class,
        ModelContract.interpret(raw, CitationIndex.build(input), input));
  }

  @Test
  void sameBasisLinksMergeWithANonInvalidatingNote() {
    var result =
        interpreted(
            reply("[" + link("SOURCE_STATED", 4, 4) + "," + link("SOURCE_STATED", 5, 5) + "]"));
    assertTrue(result.accepted());
    assertEquals(1, result.candidates().associations().size());
    assertEquals("LINKS_MERGED", result.problems().getFirst().code().name());
    assertFalse(result.problems().getFirst().code().invalidates());
    assertEquals("developments[0].links[1]", result.problems().getFirst().at());
    assertEquals(2, result.reply().path("developments").get(0).path("links").size());
    assertEquals(3, result.traces().size());
  }

  @Test
  void evidenceUnionDeduplicatesExactSpansInFirstUseOrder() {
    var result =
        interpreted(
            reply(
                "["
                    + link("SOURCE_STATED", 5, 5)
                    + ","
                    + link("SOURCE_STATED", 4, 4)
                    + ","
                    + link("SOURCE_STATED", 5, 5)
                    + "]"));
    assertTrue(result.accepted());
    var values = result.candidates();
    var ids = values.associations().getFirst().evidenceIds();
    var spans =
        ids.stream()
            .map(id ->
                values.evidence().stream()
                    .filter(e -> e.id().equals(id))
                    .findFirst()
                    .orElseThrow()
                    .source())
            .toList();
    assertEquals(
        List.of(
            "RESULT: direct a study [6-1]", "It is recommended that Council receive the report.\n"),
        spans.stream().map(CitationIndex.Span::text).toList());
    assertEquals(
        List.of("developments[0].links[1]", "developments[0].links[2]"),
        result.problems().stream().map(Problem::at).toList());
  }

  @Test
  void conflictingBasesRemainInvalid() {
    var result =
        interpreted(reply("[" + link("SOURCE_STATED", 4, 4) + "," + link("INFERRED", 5, 5) + "]"));
    assertFalse(result.accepted());
    assertEquals(2, result.candidates().associations().size());
    assertEquals(
        List.of(Problem.Code.DUPLICATE_ASSOCIATION),
        result.problems().stream().map(Problem::code).toList());
  }

  @Test
  void mergingSameBasisDoesNotAbsorbAConflictingBasis() {
    var result =
        interpreted(
            reply(
                "["
                    + link("SOURCE_STATED", 4, 4)
                    + ","
                    + link("INFERRED", 5, 5)
                    + ","
                    + link("SOURCE_STATED", 5, 5)
                    + "]"));
    assertFalse(result.accepted());
    assertEquals(2, result.candidates().associations().size());
    assertTrue(
        result.problems().stream().anyMatch(p -> p.code() == Problem.Code.DUPLICATE_ASSOCIATION));
    assertEquals(
        1, result.problems().stream().filter(p -> p.code().name().equals("LINKS_MERGED")).count());
  }

  @Test
  void separateDevelopmentsKeepSeparateAssociations() {
    String raw = reply("[" + link("SOURCE_STATED", 4, 5) + "]");
    var json = org.civictracker.analysis.evaluation.EvaluationJson.MAPPER.readTree(raw);
    var devs = (tools.jackson.databind.node.ArrayNode) json.path("developments");
    devs.add(devs.get(0).deepCopy());
    var result = interpreted(json.toString());
    assertTrue(result.accepted());
    assertEquals(
        List.of("d1", "d2"),
        result.candidates().associations().stream()
            .map(Candidates.Association::developmentId)
            .toList());
    assertTrue(result.problems().isEmpty());
  }

  @Test
  void unresolvedEvidenceIsNeverRescuedByAMerge() {
    var input = FindingsRegressionTests.input();
    String raw = reply("[" + link("SOURCE_STATED", 4, 4) + "," + link("SOURCE_STATED", 6, 6) + "]");
    var result =
        assertInstanceOf(
            Interpretation.Unresolved.class,
            ModelContract.interpret(raw, CitationIndex.build(input), input));
    assertEquals(
        List.of(
            new Problem(Problem.Code.LINE_OUT_OF_RANGE, "developments[0].links[1].evidence[0]")),
        result.problems());
  }
}
