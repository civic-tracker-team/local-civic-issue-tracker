package org.civictracker.analysis;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.civictracker.analysis.domain.MeetingInput;
import org.civictracker.analysis.evaluation.EvaluationRunner;
import org.civictracker.analysis.evaluation.ReviewCli;
import org.civictracker.analysis.model.ModelClient;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import tools.jackson.databind.JsonNode;

class ReviewCliTests {
  @TempDir Path temporary;

  @Test
  void changedCandidatesCannotBeScoredAgainstAnEarlierReceipt() throws Exception {
    var model =
        new ModelClient() {
          public Prepared preflight(List<Map<String, String>> messages, int limit) {
            return new Prepared("p", List.of(1), 100, limit, Map.of());
          }

          public Attempt generate(Prepared prepared, JsonNode schema, int seed, Sampling sampling) {
            return new Attempt(
                State.SUCCESS, "{\"developments\":[],\"issues\":[]}", "eos", Map.of());
          }
        };
    var run = temporary.resolve("run");
    var input =
        new MeetingInput(
            UUID.randomUUID(),
            "1",
            LocalDate.of(2026, 1, 15),
            "Council",
            List.of(
                new MeetingInput.Source(
                    UUID.randomUUID(),
                    UUID.randomUUID(),
                    UUID.randomUUID(),
                    "a".repeat(64),
                    1,
                    UUID.randomUUID(),
                    "profile",
                    List.of(),
                    List.of(
                        new MeetingInput.Unit(
                            0,
                            "OCR",
                            "",
                            List.of(
                                new MeetingInput.Segment(
                                    UUID.randomUUID(),
                                    "Synthetic source.",
                                    "OCR",
                                    Map.of(),
                                    null,
                                    true)),
                            List.of())))),
            List.of());
    assertEquals(
        ModelClient.State.SUCCESS,
        new EvaluationRunner(model)
            .evaluate(
                input, 10, 1, ModelClient.Sampling.baseline(), run, temporary.resolve("gate")));
    Files.writeString(
        run.resolve("candidates.json"),
        "{\"evidence\":[],\"developments\":[],\"issues\":[{\"id\":\"new\",\"label\":\"changed\",\"priorIssueId\":null}],\"associations\":[]}");
    var error =
        assertThrows(
            IllegalArgumentException.class,
            () ->
                ReviewCli.main(
                    new String[] {
                      run.toString(),
                      "unused",
                      "unused",
                      temporary.resolve("metrics.json").toString()
                    }));
    assertEquals("Run candidates changed", error.getMessage());
    assertEquals(false, Files.exists(temporary.resolve("metrics.json")));
  }
}
