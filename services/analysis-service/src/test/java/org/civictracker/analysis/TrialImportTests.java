package org.civictracker.analysis;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;
import org.civictracker.analysis.application.FindingsStore;
import org.civictracker.analysis.application.TrialImporter;
import org.civictracker.analysis.evaluation.EvaluationJson;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import tools.jackson.databind.node.ObjectNode;

@SpringBootTest
class TrialImportTests {
  @Autowired TrialImporter importer;
  @Autowired FindingsStore store;
  @Autowired JdbcTemplate jdbc;
  @TempDir Path temporary;

  Path fixture() throws Exception {
    Path target = Files.createTempDirectory(temporary, "trial-");
    for (String name :
        List.of(
            "receipt.json",
            "input.json",
            "model-input.txt",
            "candidates.json",
            "attempt.json",
            "preflight.json")) {
      try (var stream = getClass().getResourceAsStream("/import-trial/synthetic/" + name)) {
        Files.copy(java.util.Objects.requireNonNull(stream), target.resolve(name));
      }
    }
    return target;
  }

  @Test
  void importsAcceptedTrialIdempotentlyWithRetainedArtifacts() throws Exception {
    var folder = fixture();
    UUID id = importer.importTrial(folder);
    assertEquals(id, importer.importTrial(folder));
    var saved = store.read(id);
    assertEquals(
        org.civictracker.analysis.application.ProfileFingerprint.of(
            EvaluationJson.MAPPER.readTree(Files.readString(folder.resolve("receipt.json"))),
            EvaluationJson.MAPPER.readTree(Files.readString(folder.resolve("preflight.json")))),
        saved.profileFingerprint());
    assertEquals(1, saved.developments().size());
    assertEquals("Elm bridge repair", saved.issues().getFirst().name());
    assertEquals(
        "Staff proposes the Elm bridge repair.",
        saved.associations().getFirst().evidence().getFirst().text());
    assertEquals(
        "IMPORTED",
        jdbc.queryForObject(
            "select origin from analysis.meeting_analysis where id=?", String.class, id));
    var key =
        jdbc.queryForObject(
            "select artifact_key from analysis.meeting_analysis where id=?", String.class, id);
    var root = Path.of(System.getenv("ANALYSIS_STORAGE_ROOT")).resolve(key);
    for (var name :
        List.of(
            "receipt.json",
            "input.json",
            "model-input.txt",
            "candidates.json",
            "attempt.json",
            "preflight.json"))
      assertArrayEquals(
          Files.readAllBytes(folder.resolve(name)), Files.readAllBytes(root.resolve(name)));
  }

  @Test
  void rejectsEveryHashMismatchBeforeSaving() throws Exception {
    for (String name : List.of("input.json", "model-input.txt", "candidates.json")) {
      var folder = fixture();
      Files.writeString(folder.resolve(name), "changed");
      rejects(folder, "ARTIFACT_HASH_MISMATCH: " + name);
    }
  }

  @Test
  void rejectsNonSuccessAndWrongContract() throws Exception {
    for (var entry : java.util.Map.of("state", "FAILED", "contractRevision", "old").entrySet()) {
      var folder = fixture();
      var receipt =
          (ObjectNode)
              EvaluationJson.MAPPER.readTree(Files.readString(folder.resolve("receipt.json")));
      receipt.put(entry.getKey(), entry.getValue());
      Files.writeString(folder.resolve("receipt.json"), receipt.toString());
      rejects(
          folder,
          entry.getKey().equals("state") ? "TRIAL_NOT_SUCCESSFUL" : "WRONG_CONTRACT_REVISION");
    }
  }

  @Test
  void rejectsChangedOrUnacceptedRawReplyEvenWhenCandidateHashesMatch() throws Exception {
    for (String reply : List.of("{}", "{\"issues\":[],\"developments\":[]}")) {
      var folder = fixture();
      var attempt =
          (ObjectNode)
              EvaluationJson.MAPPER.readTree(Files.readString(folder.resolve("attempt.json")));
      attempt.put("content", reply);
      Files.writeString(folder.resolve("attempt.json"), attempt.toString());
      rejects(folder, reply.equals("{}") ? "REPLY_NOT_ACCEPTED" : "CANDIDATES_DIFFER_FROM_REPLY");
    }
  }

  private void rejects(Path folder, String reason) {
    long before = jdbc.queryForObject("select count(*) from analysis.meeting_analysis", Long.class);
    var error = assertThrows(IllegalArgumentException.class, () -> importer.importTrial(folder));
    assertEquals(reason, error.getMessage());
    assertEquals(
        before, jdbc.queryForObject("select count(*) from analysis.meeting_analysis", Long.class));
  }

  @Test
  void emptyFindingsFromDifferentMeetingsRemainSeparateAndIdempotent() throws Exception {
    var first = emptyTrial(UUID.randomUUID());
    var second = emptyTrial(UUID.randomUUID());
    var firstId = importer.importTrial(first);
    var secondId = importer.importTrial(second);
    assertNotEquals(firstId, secondId);
    for (var pair : java.util.Map.of(first, firstId, second, secondId).entrySet()) {
      var input =
          EvaluationJson.MAPPER.readTree(Files.readString(pair.getKey().resolve("input.json")));
      assertEquals(
          input.path("meetingId").asText(), store.read(pair.getValue()).meetingId().toString());
      assertEquals(pair.getValue(), importer.importTrial(pair.getKey()));
      var receipt =
          EvaluationJson.MAPPER.readTree(Files.readString(pair.getKey().resolve("receipt.json")));
      assertEquals(
          "imports/"
              + receipt.path("inputSha256").asText()
              + "-"
              + receipt.path("candidatesSha256").asText(),
          jdbc.queryForObject(
              "select artifact_key from analysis.meeting_analysis where id=?",
              String.class,
              pair.getValue()));
    }
  }

  private Path emptyTrial(UUID meeting) throws Exception {
    var folder = fixture();
    var input =
        (ObjectNode) EvaluationJson.MAPPER.readTree(Files.readString(folder.resolve("input.json")));
    input.put("meetingId", meeting.toString());
    Files.writeString(folder.resolve("input.json"), input.toString());
    var attempt =
        (ObjectNode)
            EvaluationJson.MAPPER.readTree(Files.readString(folder.resolve("attempt.json")));
    attempt.put("content", "{\"issues\":[],\"developments\":[]}");
    Files.writeString(folder.resolve("attempt.json"), attempt.toString());
    Files.writeString(
        folder.resolve("candidates.json"),
        "{\"evidence\":[],\"developments\":[],\"issues\":[],\"associations\":[]}");
    var receipt =
        (ObjectNode)
            EvaluationJson.MAPPER.readTree(Files.readString(folder.resolve("receipt.json")));
    receipt.put(
        "inputSha256", EvaluationJson.sha256(Files.readAllBytes(folder.resolve("input.json"))));
    receipt.put(
        "candidatesSha256",
        EvaluationJson.sha256(Files.readAllBytes(folder.resolve("candidates.json"))));
    Files.writeString(folder.resolve("receipt.json"), receipt.toString());
    return folder;
  }
}
