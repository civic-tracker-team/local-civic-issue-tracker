package org.civictracker.analysis;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Files;
import java.nio.file.Path;
import org.civictracker.analysis.evaluation.EvaluationJson;
import org.civictracker.analysis.evaluation.TrialSummary;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class TrialSummaryTests {
  @TempDir Path temporary;

  @Test
  void v3OverlapUsesTheRawVerbPhraseInsteadOfTemplateWords() throws Exception {
    var output = temporary.resolve("v3-overlap");
    summarize(output, fixture("span"));
    var citation =
        EvaluationJson.MAPPER
            .readTree(Files.readString(output.resolve("summary.json")))
            .path("trials")
            .get(0)
            .path("citations")
            .get(0);
    assertEquals(0.67, citation.path("lexicalOverlap").asDouble());
    assertEquals("U1", citation.path("unit").asText());
  }

  private Path fixture(String name) throws Exception {
    return Path.of(getClass().getResource("/evaluation/trials/" + name + "/receipt.json").toURI())
        .getParent();
  }

  private void summarize(Path output, Path... inputs) throws Exception {
    var args = new java.util.ArrayList<String>();
    args.add(output.toString());
    for (var input : inputs) args.add(input.toString());
    TrialSummary.main(args.toArray(String[]::new));
  }

  @Test
  void summarizesOldAndSpanEvidenceWithoutChangingTrials() throws Exception {
    var old = fixture("old");
    var current = fixture("span");
    byte[] before = Files.readAllBytes(old.resolve("candidates.json"));
    var output = temporary.resolve("summary");
    summarize(output, old, current);
    var rows =
        EvaluationJson.MAPPER
            .readTree(Files.readString(output.resolve("summary.json")))
            .path("trials");
    assertEquals(2, rows.size());
    assertEquals(31, rows.get(0).path("promptTokens").asInt());
    assertEquals(12, rows.get(0).path("outputTokens").asInt());
    assertEquals(1, rows.get(0).path("developmentsWithoutLinks").asInt());
    assertEquals(1.0, rows.get(0).path("citations").get(0).path("lexicalOverlap").asDouble());
    assertEquals(0, rows.get(1).path("developmentsWithoutLinks").asInt());
    assertEquals(1, rows.get(1).path("kinds").path("ACTION").asInt());
    assertEquals(1, rows.get(1).path("dateRoles").path("MEETING").asInt());
    assertEquals(1, rows.get(1).path("dateRoles").path("OCCURRENCE").asInt());
    assertEquals(0.67, rows.get(1).path("citations").get(0).path("lexicalOverlap").asDouble());
    assertTrue(rows.get(0).path("rawKeyOrder").path("matchesSchema").asBoolean());
    assertTrue(rows.get(1).path("rawKeyOrder").path("matchesSchema").asBoolean());
    assertArrayEquals(before, Files.readAllBytes(old.resolve("candidates.json")));
    assertTrue(Files.readString(output.resolve("summary.md")).contains("0.67"));
  }

  @Test
  void doesNotOverwriteAnExistingSummary() throws Exception {
    var output = temporary.resolve("summary");
    summarize(output, fixture("old"));
    var before = Files.readAllBytes(output.resolve("summary.json"));
    assertThrows(
        java.nio.file.FileAlreadyExistsException.class, () -> summarize(output, fixture("old")));
    assertArrayEquals(before, Files.readAllBytes(output.resolve("summary.json")));
  }

  @Test
  void zeroOverlapAndWrongKeyOrderDoNotChangeAcceptance() throws Exception {
    var trial = temporary.resolve("trial");
    Files.createDirectory(trial);
    try (var files = Files.list(fixture("old"))) {
      for (var file : files.toList()) Files.copy(file, trial.resolve(file.getFileName()));
    }
    var candidates =
        EvaluationJson.MAPPER.readTree(Files.readString(trial.resolve("candidates.json")));
    ((tools.jackson.databind.node.ObjectNode) candidates.path("developments").get(0))
        .put("description", "Unrelated budget policy");
    Files.writeString(trial.resolve("candidates.json"), candidates.toString());
    var attempt =
        (tools.jackson.databind.node.ObjectNode)
            EvaluationJson.MAPPER.readTree(Files.readString(trial.resolve("attempt.json")));
    var raw = EvaluationJson.MAPPER.readTree(attempt.path("content").asText());
    var development = (tools.jackson.databind.node.ObjectNode) raw.path("developments").get(0);
    var description = development.remove("description");
    development.set("description", description);
    attempt.put("content", raw.toString());
    Files.writeString(trial.resolve("attempt.json"), attempt.toString());
    var output = temporary.resolve("summary");
    summarize(output, trial);
    var row =
        EvaluationJson.MAPPER
            .readTree(Files.readString(output.resolve("summary.json")))
            .path("trials")
            .get(0);
    assertEquals("SUCCESS", row.path("state").asText());
    assertEquals(0.0, row.path("citations").get(0).path("lexicalOverlap").asDouble());
    assertFalse(row.path("rawKeyOrder").path("matchesSchema").asBoolean());
  }

  @Test
  void unavailableCandidatesAreNotReportedAsValidAbstention() throws Exception {
    var trial = temporary.resolve("failed");
    Files.createDirectory(trial);
    Files.writeString(
        trial.resolve("receipt.json"),
        "{\"contractRevision\":\"grounded-ranges-2\",\"state\":\"RUNTIME_FAILED\"}");
    Files.writeString(trial.resolve("attempt.json"), "{\"content\":\"unfinished JSON\"}");
    var output = temporary.resolve("summary");
    summarize(output, trial);
    var row =
        EvaluationJson.MAPPER
            .readTree(Files.readString(output.resolve("summary.json")))
            .path("trials")
            .get(0);
    assertEquals("RUNTIME_FAILED", row.path("state").asText());
    assertTrue(row.path("developments").isNull());
    assertTrue(row.path("outputTokens").isNull());
    assertTrue(row.path("developmentsWithoutLinks").isNull());
  }

  @Test
  void globalCitationsTakeTheirUnitFromTheResolutionTrace() throws Exception {
    var trial = temporary.resolve("global");
    Files.createDirectory(trial);
    Files.writeString(
        trial.resolve("receipt.json"),
        "{\"contractRevision\":\"grounded-lines-2\",\"state\":\"SUCCESS\"}");
    Files.writeString(
        trial.resolve("attempt.json"),
        EvaluationJson.MAPPER.writeValueAsString(
            java.util.Map.of(
                "content",
                "{\"developments\":[{\"description\":\"Council"
                    + " study\",\"citations\":[{\"firstLine\":7,\"lastLine\":7}],\"associations\":[]}],\"issues\":[]}")));
    Files.writeString(
        trial.resolve("resolution.json"),
        "[{\"at\":\"developments[0].citations[0]\",\"unit\":\"U3\",\"firstLine\":7,\"lastLine\":7,\"spans\":[{\"text\":\"Council"
            + " study\"}],\"problem\":null}]");
    Files.writeString(
        trial.resolve("candidates.json"),
        "{\"developments\":[{\"id\":\"d1\",\"description\":\"Council"
            + " study\",\"dates\":[]}],\"issues\":[],\"associations\":[]}");
    var output = temporary.resolve("global-summary");
    summarize(output, trial);
    var citation =
        EvaluationJson.MAPPER
            .readTree(Files.readString(output.resolve("summary.json")))
            .path("trials")
            .get(0)
            .path("citations")
            .get(0);
    assertEquals("U3", citation.path("unit").asText());
    assertEquals(7, citation.path("firstLine").asInt());
    assertEquals(1.0, citation.path("lexicalOverlap").asDouble());
  }

  @Test
  void summaryDispatchDoesNotStartSpring() throws Exception {
    var output = temporary.resolve("dispatched");
    AnalysisServiceApplication.main(
        new String[] {"summarize", output.toString(), fixture("old").toString()});
    assertTrue(Files.exists(output.resolve("summary.json")));
    assertThrows(
        IllegalArgumentException.class,
        () -> AnalysisServiceApplication.main(new String[] {"summarize"}));
  }
}
