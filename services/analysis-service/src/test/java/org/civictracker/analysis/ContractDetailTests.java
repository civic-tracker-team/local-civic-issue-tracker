package org.civictracker.analysis;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.*;
import java.util.*;
import org.civictracker.analysis.domain.CitationIndex;
import org.civictracker.analysis.evaluation.*;
import org.civictracker.analysis.model.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import tools.jackson.databind.JsonNode;

class ContractDetailTests {
  @TempDir Path temporary;

  @Test
  void wordingRequiresSpecificMattersAndSpecificLinkPassages() {
    String system =
        ModelContract.request(CitationIndex.build(FindingsRegressionTests.input()))
            .messages()
            .getFirst()
            .get("content");
    assertTrue(
        system.contains(
            "Give each issue at most one link per development. Cite the complete sentence or clause"
                + " that names that issue."));
    assertTrue(
        system.contains(
            "A collective phrase such as 'neighborhood programs' or 'community services' is a broad"
                + " subject, not an issue; link only to the specific matters the source names."));
    assertTrue(system.contains("PROGRAM: a program the source names and describes specifically"));
    assertFalse(java.util.regex.Pattern.compile("\\bU[0-9]+").matcher(system).find());
    var lines = java.util.regex.Pattern.compile("\\bL([0-9]+)").matcher(system);
    while (lines.find()) assertTrue(Integer.parseInt(lines.group(1)) >= 90001);
  }

  @Test
  void receiptRecordsTheContractDetailAndActualMessageHash() throws Exception {
    var model =
        new ModelClient() {
          public Prepared preflight(List<Map<String, String>> messages, int limit) {
            return new Prepared("p", List.of(1), 100, limit, Map.of());
          }

          public Attempt generate(Prepared prepared, JsonNode schema, int seed, Sampling sampling) {
            return new Attempt(
                State.SUCCESS, "{\"issues\":[],\"developments\":[]}", "eos", Map.of());
          }
        };
    var output = temporary.resolve("run");
    assertEquals(
        ModelClient.State.SUCCESS,
        new EvaluationRunner(model)
            .evaluate(
                FindingsRegressionTests.input(),
                10,
                1,
                ModelClient.Sampling.baseline(),
                output,
                temporary.resolve("gate")));
    var receipt = EvaluationJson.MAPPER.readTree(Files.readString(output.resolve("receipt.json")));
    assertEquals("grounded-findings-3", receipt.path("contractRevision").asText());
    assertEquals("unit-6c-synthetic-wording", receipt.path("contractRevisionDetail").asText());
    var messages =
        EvaluationJson.MAPPER.readTree(Files.readString(output.resolve("messages.json")));
    assertEquals(
        EvaluationJson.sha256(EvaluationJson.MAPPER.writeValueAsString(messages)),
        receipt.path("messagesSha256").asText());
  }
}
