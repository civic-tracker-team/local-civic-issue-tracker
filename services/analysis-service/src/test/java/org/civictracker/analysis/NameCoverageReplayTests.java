package org.civictracker.analysis;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import org.civictracker.analysis.evaluation.EvaluationJson;
import org.civictracker.analysis.evaluation.TrialSummary;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.junit.jupiter.api.io.TempDir;
import tools.jackson.databind.JsonNode;

@EnabledIfSystemProperty(named = "t06.replayA2bDir", matches = ".+")
class NameCoverageReplayTests {
  @TempDir Path temporary;

  @Test
  void r5NameCoverageMatchesTheFourPredeclaredValues() throws Exception {
    var output = temporary.resolve("summary");
    var args = new ArrayList<String>();
    args.add(output.toString());
    for (String profile : new String[] {"baseline", "qwen"})
      for (String meeting : new String[] {"1412", "1403"})
        for (int seed : new int[] {1, 2})
          args.add(
              Path.of(System.getProperty("t06.replayA2bDir"))
                  .resolve("a2b-" + profile + "-" + meeting + "-seed" + seed)
                  .toString());
    TrialSummary.main(args.toArray(String[]::new));
    var rows =
        EvaluationJson.MAPPER
            .readTree(Files.readString(output.resolve("summary.json")))
            .path("trials");
    check(rows, "a2b-qwen-1403-seed1", "developments[5].links[0]", "Motel Voucher Program", 0.00);
    check(
        rows,
        "a2b-qwen-1403-seed1",
        "developments[0].links[0]",
        "Homeless Solutions Action Plan",
        0.25);
    check(rows, "a2b-baseline-1403-seed2", "developments[1].links[0]", "Palm Field property", 1.00);
    check(
        rows,
        "a2b-baseline-1403-seed2",
        "developments[1].links[1]",
        "SB HOPE Campus Navigation Center",
        0.25);
    System.out.println("R5 PASS four predeclared name coverage values");
  }

  private void check(JsonNode rows, String trial, String path, String name, double expected) {
    for (var row : rows)
      if (row.path("trial").asText().equals(trial))
        for (var link : row.path("links"))
          if (link.path("at").asText().equals(path)) {
            assertEquals(name, link.path("issue").asText());
            assertEquals(expected, link.path("nameCoverage").asDouble(), trial + " " + path);
            return;
          }
    fail("Missing predeclared R5 row " + trial + " " + path);
  }
}
