package org.civictracker.analysis.application;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.civictracker.analysis.domain.*;
import org.civictracker.analysis.evaluation.EvaluationJson;
import org.civictracker.analysis.model.*;
import org.civictracker.analysis.persistence.AnalysisRevision.Origin;
import tools.jackson.databind.JsonNode;

/** Checks an attempt snapshot before either persistence path uses it. */
public record TrialArtifacts(
    MeetingInput input,
    Candidates candidates,
    DevelopmentKinds kinds,
    Map<String, byte[]> files,
    JsonNode receipt,
    JsonNode preflight) {
  public static TrialArtifacts read(Path folder) throws IOException {
    var files = new LinkedHashMap<String, byte[]>();
    for (String name :
        List.of(
            "receipt.json",
            "input.json",
            "model-input.txt",
            "candidates.json",
            "attempt.json",
            "preflight.json")) files.put(name, Files.readAllBytes(folder.resolve(name)));
    var receipt = EvaluationJson.MAPPER.readTree(files.get("receipt.json"));
    if (!"SUCCESS".equals(receipt.path("state").asText())) reject("TRIAL_NOT_SUCCESSFUL");
    if (!ModelContract.REVISION.equals(receipt.path("contractRevision").asText()))
      reject("WRONG_CONTRACT_REVISION");
    for (var entry :
        Map.of(
                "input.json",
                "inputSha256",
                "model-input.txt",
                "modelInputSha256",
                "candidates.json",
                "candidatesSha256")
            .entrySet()) {
      if (!EvaluationJson.sha256(files.get(entry.getKey()))
          .equals(receipt.path(entry.getValue()).asText()))
        reject("ARTIFACT_HASH_MISMATCH: " + entry.getKey());
    }
    var input = EvaluationJson.MAPPER.readValue(files.get("input.json"), MeetingInput.class);
    var raw = EvaluationJson.MAPPER.readTree(files.get("attempt.json")).path("content");
    if (!raw.isString()) reject("MISSING_RAW_REPLY");
    var result = ModelContract.interpret(raw.asText(), CitationIndex.build(input), input);
    if (!(result instanceof Interpretation.Interpreted accepted) || !accepted.accepted())
      throw new SaveRejected(SaveRejected.Code.REPLY_NOT_ACCEPTED);
    var candidates =
        EvaluationJson.MAPPER.readValue(files.get("candidates.json"), Candidates.class);
    if (!accepted.candidates().equals(candidates)) reject("CANDIDATES_DIFFER_FROM_REPLY");

    return new TrialArtifacts(
        input,
        candidates,
        DevelopmentKinds.from(accepted),
        Map.copyOf(files),
        receipt,
        EvaluationJson.MAPPER.readTree(files.get("preflight.json")));
  }

  public RevisionMetadata metadata(Origin origin, java.util.UUID operationId, String key) {
    return new RevisionMetadata(
        origin,
        operationId,
        ModelContract.REVISION,
        EvaluationJson.MAPPER.writeValueAsString(
            Map.of("revisionDetail", required(receipt, "contractRevisionDetail"))),
        ProfileFingerprint.of(receipt, preflight),
        receipt.path("inputSha256").asText(),
        receipt.path("modelInputSha256").asText(),
        receipt.path("candidatesSha256").asText(),
        EvaluationJson.sha256(files.get("receipt.json")),
        required(receipt, "promptTokens").asLong(),
        key);
  }

  static JsonNode required(JsonNode node, String key) {
    var value = node.path(key);
    if (value.isMissingNode() || value.isNull()) reject("MISSING_TRIAL_METADATA: " + key);
    return value;
  }

  private static void reject(String reason) {
    throw new SaveRejected(SaveRejected.Code.valueOf(reason.split(": ", 2)[0]), reason);
  }
}
