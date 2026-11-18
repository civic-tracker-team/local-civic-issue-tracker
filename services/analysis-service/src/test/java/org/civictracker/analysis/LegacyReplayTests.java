package org.civictracker.analysis;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.civictracker.analysis.domain.CitationIndex;
import org.civictracker.analysis.domain.MeetingInput;
import org.civictracker.analysis.evaluation.EvaluationJson;
import org.civictracker.analysis.evaluation.TrialSummary;
import org.civictracker.analysis.model.ModelContract;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import tools.jackson.databind.JsonNode;

@EnabledIfSystemProperty(named = "t06.replayDir", matches = ".+")
class LegacyReplayTests {
  @org.junit.jupiter.api.io.TempDir Path temporary;
  private static final List<String> TRIALS =
      List.of(
          "baseline-1412-seed1",
          "baseline-1412-seed2",
          "baseline-1403-seed1",
          "baseline-1403-seed2",
          "qwen-1412-seed1",
          "qwen-1412-seed2",
          "qwen-1403-seed1",
          "qwen-1403-seed2");

  private Path trial(String name) {
    return Path.of(System.getProperty("t06.replayDir")).resolve(name);
  }

  private JsonNode read(Path path) throws Exception {
    return EvaluationJson.MAPPER.readTree(Files.readString(path));
  }

  private MeetingInput input(Path path) throws Exception {
    assertEquals(
        read(path.resolve("receipt.json")).path("inputSha256").asText(),
        EvaluationJson.sha256(Files.readAllBytes(path.resolve("input.json"))),
        "Frozen input hash");
    return EvaluationJson.MAPPER.readValue(
        Files.readString(path.resolve("input.json")), MeetingInput.class);
  }

  @Test
  void r1EveryRawCitationMatchesItsSavedSpans() throws Exception {
    for (String name : TRIALS) {
      var folder = trial(name);
      var index = CitationIndex.build(input(folder));
      var raw =
          EvaluationJson.MAPPER.readTree(
              read(folder.resolve("attempt.json")).path("content").asText());
      var citations = new ArrayList<JsonNode>();
      for (var development : raw.path("developments")) {
        development.path("citations").forEach(citations::add);
        for (var association : development.path("associations"))
          association.path("citations").forEach(citations::add);
      }
      var saved = read(folder.resolve("resolution.json"));
      assertEquals(saved.size(), citations.size(), name);
      for (int i = 0; i < citations.size(); i++) {
        var citation = citations.get(i);
        var spans =
            assertInstanceOf(
                    CitationIndex.Found.class,
                    index.resolveLines(
                        global(
                            index,
                            citation.path("unit").asText(),
                            citation.path("firstLine").asInt()),
                        global(
                            index,
                            citation.path("unit").asText(),
                            citation.path("lastLine").asInt())))
                .spans();
        assertEquals(saved.get(i).path("selection"), citation, name);
        assertEquals(saved.get(i).path("spans"), EvaluationJson.MAPPER.valueToTree(spans), name);
      }
      System.out.println("R1 " + name + " PASS citations=" + citations.size());
    }
  }

  @Test
  void globalArmExportsBoundedSchemasAndAllIndexedLines() throws Exception {
    var output = Path.of(System.getProperty("t06.replayOutput")).getParent();
    for (String name : List.of("baseline-1412-seed1", "baseline-1403-seed1")) {
      var index = CitationIndex.build(input(trial(name)));
      var request = ModelContract.request(index);
      var numbers =
          request
              .inputText()
              .lines()
              .filter(l -> l.matches("L[1-9][0-9]*(?: .*|)"))
              .map(l -> Integer.parseInt(l.split(" ", 2)[0].substring(1)))
              .toList();
      assertEquals(
          java.util.stream.IntStream.rangeClosed(1, index.lineCount()).boxed().toList(), numbers);
      assertFalse(
          java.util.regex.Pattern.compile("\\bU[0-9]+").matcher(request.inputText()).find());
      String id = index.view().officialId();
      Files.writeString(
          output.resolve(id + "-schema.json"),
          request.schema().toString(),
          java.nio.file.StandardOpenOption.CREATE_NEW);
      Files.writeString(
          output.resolve(id + "-messages.json"),
          EvaluationJson.MAPPER.writeValueAsString(request.messages()),
          java.nio.file.StandardOpenOption.CREATE_NEW);
      System.out.println("GLOBAL_ARM " + id + " indexedLines=" + index.lineCount());
    }
  }

  private int global(CitationIndex index, String unit, int local) {
    return index.snapshot().lines().stream()
        .filter(b -> b.unit().equals(unit) && b.line() == local)
        .findFirst()
        .orElseThrow()
        .globalLine();
  }

  private void translate(JsonNode value, CitationIndex index) {
    if (value.isObject() && value.has("firstLine") && value.has("unit")) {
      var range = (tools.jackson.databind.node.ObjectNode) value;
      String unit = range.path("unit").asText();
      int first = global(index, unit, range.path("firstLine").asInt());
      int last = global(index, unit, range.path("lastLine").asInt());
      range.remove("unit");
      range.put("firstLine", first);
      range.put("lastLine", last);
    } else for (var child : value) translate(child, index);
  }

  @Test
  void r3SummaryMatchesThePredeclaredValues() throws Exception {
    var output =
        Path.of(System.getProperty("t06.replayOutput", temporary.resolve("summary").toString()));
    var args = new ArrayList<String>();
    args.add(output.toString());
    for (String name : TRIALS) args.add(trial(name).toString());
    TrialSummary.main(args.toArray(String[]::new));
    var rows = read(output.resolve("summary.json")).path("trials");
    assertEquals(8, rows.size());
    int[] outputTokens = {213, 179, 363, 363, 17, 211, 246, 701};
    int[][] counts = {
      {1, 0, 0}, {1, 0, 0}, {1, 1, 1}, {1, 1, 1}, {0, 0, 0}, {1, 0, 0}, {1, 0, 0}, {2, 1, 2}
    };
    var withoutLinks = new ArrayList<String>();
    var cover = new ArrayList<Double>();
    var result = new ArrayList<Double>();
    for (int n = 0; n < TRIALS.size(); n++) {
      String name = TRIALS.get(n);
      var row = rows.get(n);
      assertEquals(name, row.path("trial").asText());
      assertEquals(name.contains("-1412-") ? 6193 : 59568, row.path("promptTokens").asInt(), name);
      assertEquals(outputTokens[n], row.path("outputTokens").asInt(), name);
      assertEquals(counts[n][0], row.path("developments").asInt(), name);
      assertEquals(counts[n][1], row.path("issues").asInt(), name);
      assertEquals(counts[n][2], row.path("associations").asInt(), name);
      if (row.path("developmentsWithoutLinks").asInt() > 0) withoutLinks.add(name);
      if (counts[n][0] > 0) {
        assertEquals(
            List.of("description", "status", "dates", "citations", "associations"),
            EvaluationJson.MAPPER.convertValue(
                row.path("rawKeyOrder").path("keys"),
                new tools.jackson.core.type.TypeReference<List<String>>() {}),
            name);
        assertTrue(row.path("rawKeyOrder").path("matchesSchema").asBoolean(), name);
      }
      if (name.contains("-1403-"))
        for (var citation : row.path("citations")) {
          if (citation.path("unit").asText().equals("U94"))
            cover.add(citation.path("lexicalOverlap").asDouble());
          if (citation.path("unit").asText().equals("U96"))
            result.add(citation.path("lexicalOverlap").asDouble());
        }
    }
    assertEquals(
        List.of("baseline-1412-seed1", "baseline-1412-seed2", "qwen-1412-seed2", "qwen-1403-seed1"),
        withoutLinks);
    assertFalse(cover.isEmpty());
    assertFalse(result.isEmpty());
    double maximum = java.util.Collections.max(cover), minimum = java.util.Collections.min(result);
    assertTrue(maximum < minimum, "Every U94 citation must score below every U96 citation");
    System.out.println(
        "R3 PASS trials=8 outputs="
            + java.util.Arrays.toString(outputTokens)
            + " withoutLinks="
            + withoutLinks
            + " maxU94="
            + maximum
            + " minU96="
            + minimum);
  }
}
