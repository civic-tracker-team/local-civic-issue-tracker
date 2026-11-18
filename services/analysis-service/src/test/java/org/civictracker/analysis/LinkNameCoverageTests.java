package org.civictracker.analysis;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import org.civictracker.analysis.evaluation.EvaluationJson;
import org.civictracker.analysis.evaluation.TrialSummary;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import tools.jackson.databind.JsonNode;

class LinkNameCoverageTests {
  @TempDir Path temporary;

  private JsonNode summarize(String revision, String name, String links, String traces)
      throws Exception {
    var trial = Files.createDirectory(temporary.resolve("trial"));
    Files.writeString(
        trial.resolve("receipt.json"),
        EvaluationJson.MAPPER.writeValueAsString(
            Map.of("contractRevision", revision, "state", "SUCCESS")));
    String raw =
        "{\"developments\":[{\"does\":\"study\",\"evidence\":[],\"links\":"
            + links
            + "}],\"issues\":[{\"id\":\"i1\",\"name\":\""
            + name
            + "\"}]}";
    Files.writeString(
        trial.resolve("attempt.json"),
        EvaluationJson.MAPPER.writeValueAsString(Map.of("content", raw)));
    Files.writeString(trial.resolve("resolution.json"), traces);
    var output = temporary.resolve("summary");
    TrialSummary.main(new String[] {output.toString(), trial.toString()});
    return EvaluationJson.MAPPER
        .readTree(Files.readString(output.resolve("summary.json")))
        .path("trials")
        .get(0);
  }

  private String links(int count) {
    return "["
        + String.join(
            ",",
            java.util.Collections.nCopies(
                count, "{\"issueId\":\"i1\",\"evidence\":[{\"firstLine\":1,\"lastLine\":1}]}"))
        + "]";
  }

  @Test
  void reportsEveryRawLinkBeforeMergeAndExcludesShortNameTokens() throws Exception {
    var row =
        summarize(
            "grounded-findings-3",
            "SB HOPE Campus Navigation Center",
            links(2),
            """
            [{"at":"developments[0].links[0].evidence[0]","spans":[{"text":"Navigation"}]},
             {"at":"developments[0].links[1].evidence[0]","spans":[{"text":"Nothing related"}]}]
            """);
    assertEquals("SUCCESS", row.path("state").asText());
    var links = row.path("links");
    assertEquals(2, links.size());
    assertEquals(0.25, links.get(0).path("nameCoverage").asDouble());
    assertEquals(0.0, links.get(1).path("nameCoverage").asDouble());
    assertEquals("developments[0].links[1]", links.get(1).path("at").asText());
    assertEquals("SB HOPE Campus Navigation Center", links.get(0).path("issue").asText());
    var markdown = Files.readString(temporary.resolve("summary/summary.md"));
    assertTrue(
        markdown.contains("| Trial | Development index | Link path | Issue | Name coverage |"));
    assertTrue(markdown.contains("| 0.25 |"));
    assertTrue(markdown.contains("| 0.00 |"));
    assertTrue(markdown.indexOf("## Links") > markdown.indexOf("## Citations"));
    assertTrue(markdown.contains("does not establish support"));
  }

  @Test
  void tokenizesEachSpanSeparatelyBeforeTakingTheUnion() throws Exception {
    var row =
        summarize(
            "grounded-findings-3",
            "Maple Park",
            links(1),
            """
            [{"at":"developments[0].links[0].evidence[0]","spans":[{"text":"Map"},{"text":"le Park"}]}]
            """);
    assertEquals(0.50, row.path("links").get(0).path("nameCoverage").asDouble());
  }

  @Test
  void anyUnresolvedEvidenceMakesTheWholeLinkUnavailable() throws Exception {
    var row =
        summarize(
            "grounded-findings-3",
            "Maple Park",
            """
            [{"issueId":"i1","evidence":[{"firstLine":1,"lastLine":1},{"firstLine":2,"lastLine":2}]},
             {"issueId":"i1","evidence":[{"firstLine":3,"lastLine":3}]}]
            """,
            """
            [{"at":"developments[0].links[0].evidence[0]","spans":[{"text":"Maple Park"}]},
             {"at":"developments[0].links[0].evidence[1]","problem":"LINE_OUT_OF_RANGE","spans":[]}]
            """);
    assertEquals(2, row.path("links").size());
    for (var link : row.path("links")) assertTrue(link.path("nameCoverage").isNull());
    assertTrue(Files.readString(temporary.resolve("summary/summary.md")).contains("| n/a |"));
  }

  @Test
  void emptyNameTokensAreUnavailable() throws Exception {
    var row =
        summarize(
            "grounded-findings-3",
            "SB 2",
            links(1),
            """
            [{"at":"developments[0].links[0].evidence[0]","spans":[{"text":"SB 2"}]}]
            """);
    assertEquals(1, row.path("links").size());
    assertTrue(row.path("links").get(0).path("nameCoverage").isNull());
  }

  @Test
  void oldContractsHaveNoNameCoverageRows() throws Exception {
    var row = summarize("grounded-lines-2", "Maple Park", links(1), "[]");
    assertTrue(row.path("links").isArray());
    assertTrue(row.path("links").isEmpty());
  }
}
