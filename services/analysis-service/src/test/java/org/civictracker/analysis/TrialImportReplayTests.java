package org.civictracker.analysis;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Path;
import org.civictracker.analysis.application.FindingsStore;
import org.civictracker.analysis.application.TrialImporter;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

@SpringBootTest
@EnabledIfSystemProperty(named = "t07.importReplayDir", matches = ".+")
class TrialImportReplayTests {
  @Autowired TrialImporter importer;
  @Autowired FindingsStore store;
  @Autowired JdbcTemplate jdbc;

  @Test
  void importsFrozenA2cSeedOneTrialsWithExpectedCounts() throws Exception {
    for (var meeting : java.util.List.of("1403", "1412")) {
      var folder =
          Path.of(System.getProperty("t07.importReplayDir"))
              .resolve("a2c-baseline-" + meeting + "-seed1");
      var id = importer.importTrial(folder);
      assertEquals(id, importer.importTrial(folder));
      var saved = store.read(id);
      int dev = meeting.equals("1403") ? 2 : 1;
      int assoc = meeting.equals("1403") ? 3 : 1;
      int evidence = meeting.equals("1403") ? 30 : 3;
      assertEquals(dev, saved.developments().size());
      if (meeting.equals("1403")) {
        assertEquals(
            org.civictracker.analysis.domain.FindingKind.ACTION,
            saved.developments().get(1).kind());
        var spans = saved.developments().get(1).evidence();
        assertEquals(27, spans.size());
        assertTrue(spans.stream().allMatch(span -> span.unitOrdinal() == 2));
        assertEquals(1, spans.stream().map(span -> span.versionId()).distinct().count());
        var input =
            org.civictracker.analysis.evaluation.EvaluationJson.MAPPER.readValue(
                java.nio.file.Files.readString(folder.resolve("input.json")),
                org.civictracker.analysis.domain.MeetingInput.class);
        var source =
            input.sources().stream()
                .filter(s -> s.versionId().equals(spans.getFirst().versionId()))
                .findFirst()
                .orElseThrow();
        var unit = source.units().stream().filter(u -> u.ordinal() == 2).findFirst().orElseThrow();
        var selectedIds =
            unit.segments().stream().filter(s -> s.selected()).map(s -> s.id()).toList();
        var expected = spans.stream().map(s -> selectedIds.indexOf(s.segmentId())).toList();
        assertTrue(expected.stream().allMatch(position -> position >= 0));
        assertEquals(
            java.util.stream.IntStream.range(expected.getFirst(), expected.getFirst() + 27)
                .boxed()
                .toList(),
            expected);
        assertEquals(expected, spans.stream().map(span -> span.segmentOrdinal()).toList());
        var rawIds = unit.segments().stream().map(s -> s.id()).toList();
        System.out.println(
            "T07 SEGMENTS 1403 council action PASS unit=2 selected positions="
                + expected.getFirst()
                + ".."
                + expected.getLast()
                + " spans=27 raw positions="
                + rawIds.indexOf(spans.getFirst().segmentId())
                + ".."
                + rawIds.indexOf(spans.getLast().segmentId()));
        System.out.println(
            "T07 KIND 1403 council action PASS kind=" + saved.developments().get(1).kind());
      }
      assertEquals(dev, saved.issues().size());
      assertEquals(assoc, saved.associations().size());
      assertEquals(
          evidence,
          jdbc.queryForObject(
              "select count(*) from analysis.evidence_span where revision_id=?",
              Integer.class,
              id));
      System.out.printf(
          "T07 IMPORT %s PASS developments=%d issues=%d associations=%d evidence=%d%n",
          meeting, dev, dev, assoc, evidence);
    }
  }
}
