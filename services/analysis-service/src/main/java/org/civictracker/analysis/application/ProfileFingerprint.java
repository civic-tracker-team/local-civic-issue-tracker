package org.civictracker.analysis.application;

import java.util.Map;
import org.civictracker.analysis.evaluation.EvaluationJson;
import org.civictracker.analysis.model.ModelClient;
import tools.jackson.databind.JsonNode;

/** Identifies effective model settings independently of machine paths. */
public final class ProfileFingerprint {
  private ProfileFingerprint() {}

  public static String of(JsonNode receipt, JsonNode preflight) {
    var identity = TrialArtifacts.required(preflight, "identity");
    var sampling =
        EvaluationJson.MAPPER.treeToValue(
            TrialArtifacts.required(receipt, "sampling"), ModelClient.Sampling.class);
    return EvaluationJson.sha256(
        EvaluationJson.MAPPER.writeValueAsString(
            Map.of(
                "seed", TrialArtifacts.required(receipt, "seed").asInt(),
                "sampling", sampling,
                "outputLimit", TrialArtifacts.required(receipt, "outputLimit").asInt(),
                "context", TrialArtifacts.required(receipt, "context").asInt(),
                "modelSha256", TrialArtifacts.required(identity, "modelSha256").asText(),
                "runtimeBuild", TrialArtifacts.required(identity, "runtimeBuild").asText(),
                "reasoning", TrialArtifacts.required(identity, "reasoning").asBoolean())));
  }
}
