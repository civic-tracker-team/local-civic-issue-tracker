package org.civictracker.analysis;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.*;
import java.util.*;
import org.civictracker.analysis.domain.*;
import org.civictracker.analysis.evaluation.EvaluationJson;
import org.civictracker.analysis.model.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

@EnabledIfSystemProperty(named = "t06.replayA2Dir", matches = ".+")
class LinkReplayTests {
  @Test
  void r4SavedRepliesKeepTheirPredeclaredAcceptanceAndExactUnion() throws Exception {
    var root = Path.of(System.getProperty("t06.replayA2Dir"));
    for (String profile : List.of("baseline", "qwen"))
      for (String meeting : List.of("1412", "1403"))
        for (int seed : List.of(1, 2)) {
          String name = "a2-" + profile + "-" + meeting + "-seed" + seed;
          var folder = root.resolve(name);
          var receipt =
              EvaluationJson.MAPPER.readTree(Files.readString(folder.resolve("receipt.json")));
          assertEquals(
              receipt.path("inputSha256").asText(),
              EvaluationJson.sha256(Files.readAllBytes(folder.resolve("input.json"))));
          var input =
              EvaluationJson.MAPPER.readValue(
                  Files.readString(folder.resolve("input.json")), MeetingInput.class);
          var index = CitationIndex.build(input);
          var attempt =
              EvaluationJson.MAPPER.readTree(Files.readString(folder.resolve("attempt.json")));
          var result =
              assertInstanceOf(
                  Interpretation.Interpreted.class,
                  ModelContract.interpret(attempt.path("content").asText(), index, input));
          assertTrue(result.accepted(), name + " " + result.problems());
          if (name.equals("a2-qwen-1403-seed1")) {
            var links =
                result.candidates().associations().stream()
                    .filter(a -> a.developmentId().equals("d2") && a.issueId().equals("i1"))
                    .toList();
            assertEquals(1, links.size());
            assertEquals(Candidates.Basis.SOURCE_STATED, links.getFirst().basis());
            var expected = new LinkedHashSet<CitationIndex.Span>();
            expected.addAll(
                assertInstanceOf(CitationIndex.Found.class, index.resolveLines(2166, 2179))
                    .spans());
            expected.addAll(
                assertInstanceOf(CitationIndex.Found.class, index.resolveLines(2180, 2182))
                    .spans());
            var byId = new LinkedHashMap<String, CitationIndex.Span>();
            result.candidates().evidence().forEach(e -> byId.put(e.id(), e.source()));
            assertEquals(
                List.copyOf(expected),
                links.getFirst().evidenceIds().stream().map(byId::get).toList());
            assertEquals(3, result.candidates().associations().size());
            assertEquals(
                List.of(new Problem(Problem.Code.LINKS_MERGED, "developments[1].links[2]")),
                result.problems());
            System.out.println(
                "R4 "
                    + name
                    + " PASS accepted=true merged=d2->i1 spans="
                    + expected.size()
                    + " notes="
                    + result.problems());
          } else {
            assertEquals("SUCCESS", receipt.path("state").asText(), name);
            assertTrue(result.problems().isEmpty(), name);
            System.out.println("R4 " + name + " PASS acceptance_unchanged=true");
          }
        }
  }
}
