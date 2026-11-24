package org.civictracker.analysis;

import static org.junit.jupiter.api.Assertions.*;

import org.civictracker.analysis.application.ProfileFingerprint;
import org.civictracker.analysis.evaluation.EvaluationJson;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.node.ObjectNode;

class ProfileFingerprintTests {
  @Test
  void machinePathAndJsonPropertyOrderDoNotChangeTheFingerprint() throws Exception {
    var receipt = EvaluationJson.MAPPER.readTree(OperationFixture.resource("receipt.json"));
    var preflight = EvaluationJson.MAPPER.readTree(OperationFixture.resource("preflight.json"));
    String expected = ProfileFingerprint.of(receipt, preflight);
    ((ObjectNode) preflight.path("identity").path("properties"))
        .put("model_path", "another-machine.gguf");
    assertEquals(expected, ProfileFingerprint.of(receipt, preflight));
    var sampling = (ObjectNode) receipt.path("sampling");
    var reordered = EvaluationJson.MAPPER.createObjectNode();
    for (String key :
        java.util.List.of(
            "repeatPenalty", "presencePenalty", "minP", "topK", "topP", "temperature"))
      reordered.set(key, sampling.get(key));
    ((ObjectNode) receipt).set("sampling", reordered);
    assertEquals(expected, ProfileFingerprint.of(receipt, preflight));
  }

  @Test
  void eachIncludedSettingChangesTheFingerprint() throws Exception {
    var receipt = EvaluationJson.MAPPER.readTree(OperationFixture.resource("receipt.json"));
    var preflight = EvaluationJson.MAPPER.readTree(OperationFixture.resource("preflight.json"));
    String before = ProfileFingerprint.of(receipt, preflight);
    for (String key : java.util.List.of("seed", "outputLimit", "context")) {
      var changed = (ObjectNode) receipt.deepCopy();
      changed.put(key, receipt.path(key).asInt() + 1);
      assertNotEquals(before, ProfileFingerprint.of(changed, preflight), key);
    }
    var changed = (ObjectNode) receipt.deepCopy();
    ((ObjectNode) changed.path("sampling")).put("temperature", .4);
    assertNotEquals(before, ProfileFingerprint.of(changed, preflight));
    for (String key : java.util.List.of("modelSha256", "runtimeBuild", "reasoning")) {
      var identity = (ObjectNode) preflight.deepCopy();
      var node = (ObjectNode) identity.path("identity");
      if (key.equals("reasoning")) node.put(key, true);
      else node.put(key, "changed");
      assertNotEquals(before, ProfileFingerprint.of(receipt, identity), key);
    }
  }
}
