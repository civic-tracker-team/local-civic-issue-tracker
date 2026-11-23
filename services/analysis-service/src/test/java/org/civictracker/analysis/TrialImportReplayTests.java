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
