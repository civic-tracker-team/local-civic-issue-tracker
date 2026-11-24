package org.civictracker.analysis.application;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import org.civictracker.analysis.evaluation.EvaluationJson;
import org.civictracker.analysis.persistence.AnalysisRevision.Origin;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Verifies saved artifacts before importing their findings. */
@Service
public class TrialImporter {
  private final FindingsStore store;
  private final JdbcTemplate jdbc;
  private final Path storage;

  public TrialImporter(
      FindingsStore store, JdbcTemplate jdbc, @Value("${analysis.storage-root}") String storage) {
    this.store = store;
    this.jdbc = jdbc;
    this.storage = Path.of(storage);
  }

  @Transactional(rollbackFor = IOException.class)
  public UUID importTrial(Path folder) throws IOException {
    var trial = TrialArtifacts.read(folder);
    var input = trial.input();
    if (!input.priorIssues().isEmpty()) reject("IMPORT_REQUIRES_EMPTY_PRIOR_CONTEXT");
    var files = trial.files();
    String hash = EvaluationJson.sha256(files.get("candidates.json"));
    String inputHash = EvaluationJson.sha256(files.get("input.json"));
    String identity = inputHash + "-" + hash;
    // Serialize imports of the same artifact across service processes.
    jdbc.query("select pg_advisory_xact_lock(hashtext(?))", rs -> {}, identity);
    var existing =
        jdbc.queryForList(
            "select id from analysis.meeting_analysis where origin='IMPORTED'"
                + " and input_sha256=? and candidates_sha256=?",
            UUID.class,
            inputHash,
            hash);
    if (!existing.isEmpty()) return existing.getFirst();
    String key = "imports/" + identity;
    Path destination = storage.resolve(key);
    Files.createDirectories(destination);
    for (var file : files.entrySet()) {
      Path path = destination.resolve(file.getKey());
      if (Files.exists(path)) {
        if (!java.util.Arrays.equals(Files.readAllBytes(path), file.getValue()))
          reject("STORED_ARTIFACT_DIFFERS: " + file.getKey());
      } else Files.write(path, file.getValue(), java.nio.file.StandardOpenOption.CREATE_NEW);
    }
    return store.save(
        input,
        trial.candidates(),
        PriorContext.Snapshot.empty(),
        trial.metadata(Origin.IMPORTED, null, key),
        trial.kinds());
  }

  private static void reject(String reason) {
    throw new IllegalArgumentException(reason);
  }
}
