package org.civictracker.analysis.model;

import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.civictracker.analysis.evaluation.EvaluationJson;
import org.civictracker.analysis.evaluation.JsonHttp;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.JsonNode;

/** Uses the same token IDs for preflight and generation. */
public final class LlamaModelClient implements ModelClient {
  private final JsonHttp http;
  private final String modelPath;
  private final String modelSha256;
  private final String runtimeBuild;

  public LlamaModelClient(
      JsonHttp http, String modelPath, String modelSha256, String runtimeBuild) {
    this.http = http;
    this.modelPath = modelPath;
    this.modelSha256 = modelSha256;
    this.runtimeBuild = runtimeBuild;
  }

  @Override
  public Prepared preflight(List<Map<String, String>> messages, int outputLimit)
      throws IOException, InterruptedException {
    if (outputLimit < 1) throw new IllegalArgumentException("Positive output reservation required");
    var properties = http.get("/props");
    if (!java.nio.file.Path.of(properties.path("model_path").asText())
            .toAbsolutePath()
            .normalize()
            .equals(java.nio.file.Path.of(modelPath).toAbsolutePath().normalize())
        || !properties.path("build_info").asText().equals(runtimeBuild)) {
      throw new IOException("Runtime identity differs from the pinned configuration");
    }
    int context = properties.path("default_generation_settings").path("n_ctx").asInt(-1);
    if (context < 1) throw new IOException("Runtime did not expose its effective context");
    var slots = http.get("/slots");
    if (!slots.isArray()
        || slots.size() != 1
        || !slots.get(0).path("is_processing").isBoolean()
        || slots.get(0).path("is_processing").asBoolean()) {
      throw new IOException("Evaluation requires one idle runtime slot");
    }
    var applied =
        http.post(
            "/apply-template",
            Map.of(
                "messages",
                messages,
                "enable_thinking",
                false,
                "chat_template_kwargs",
                Map.of("enable_thinking", false)));
    if (!applied.path("prompt").isString()) throw new IOException("Missing rendered template");
    String prompt = applied.path("prompt").asText();
    var tokenized =
        http.post(
            "/tokenize", Map.of("content", prompt, "add_special", true, "parse_special", true));
    if (!tokenized.path("tokens").isArray() || tokenized.path("tokens").isEmpty()) {
      throw new IOException("Runtime did not return exact tokens");
    }
    var tokens = new ArrayList<Integer>();
    for (var token : tokenized.path("tokens")) {
      if (!token.isIntegralNumber() || !token.canConvertToInt() || token.asInt() < 0)
        throw new IOException("Invalid token ID");
      tokens.add(token.asInt());
    }
    var identity = new LinkedHashMap<String, Object>();
    identity.put("modelSha256", modelSha256);
    identity.put("runtimeBuild", runtimeBuild);
    identity.put("properties", map(properties));
    identity.put("propertiesSha256", EvaluationJson.sha256(properties.toString()));
    identity.put("renderedPromptSha256", EvaluationJson.sha256(prompt));
    identity.put(
        "tokenIdsSha256", EvaluationJson.sha256(EvaluationJson.MAPPER.writeValueAsString(tokens)));
    identity.put("reasoning", false);
    return new Prepared(prompt, tokens, context, outputLimit, identity);
  }

  @Override
  public Attempt generate(Prepared prepared, JsonNode schema, int seed, Sampling sampling)
      throws IOException, InterruptedException {
    if (!prepared.fits())
      return new Attempt(
          State.CAPACITY_REJECTED, "", "Prompt plus output exceeds context", Map.of());
    try {
      var current = http.get("/props");
      if (!EvaluationJson.sha256(current.toString())
          .equals(prepared.identity().get("propertiesSha256")))
        return new Attempt(
            State.RUNTIME_FAILED,
            "",
            "Runtime configuration changed before completion was sent",
            Map.of());
    } catch (InterruptedException exception) {
      Thread.currentThread().interrupt();
      return new Attempt(
          State.RUNTIME_FAILED,
          "",
          "Runtime check interrupted before completion was sent",
          Map.of());
    } catch (IOException | RuntimeException exception) {
      return new Attempt(
          State.RUNTIME_FAILED,
          "",
          "Runtime check failed before completion was sent: "
              + exception.getClass().getSimpleName(),
          Map.of());
    }
    var request = new LinkedHashMap<String, Object>(sampling.requestOptions());
    request.putAll(
        Map.of(
            "prompt",
            prepared.tokens(),
            "n_predict",
            prepared.outputLimit(),
            "stream",
            false,
            "cache_prompt",
            false,
            "json_schema",
            schema,
            "seed",
            seed,
            "id_slot",
            0));
    JsonNode result;
    long start = System.nanoTime();
    try {
      result = http.post("/completion", request);
    } catch (IOException exception) {
      return new Attempt(
          State.UNCERTAIN_EXECUTION,
          "",
          "Completion response lost; reconcile the runtime before retry",
          Map.of("elapsedMillis", (System.nanoTime() - start) / 1_000_000));
    }
    var receipt = new LinkedHashMap<String, Object>();
    receipt.put("response", map(result));
    receipt.put("seed", seed);
    receipt.put("sampling", sampling);
    receipt.put("elapsedMillis", (System.nanoTime() - start) / 1_000_000);
    String content = result.path("content").asText("");
    if (!result.path("stop").isBoolean() || !result.path("stop").asBoolean())
      return new Attempt(
          State.UNCERTAIN_EXECUTION, content, "Runtime did not confirm completion", receipt);
    if (!result.path("truncated").isBoolean()
        || result.path("truncated").asBoolean()
        || result.path("tokens_evaluated").asInt(-1) != prepared.tokens().size()
        || result.path("tokens_predicted").asInt(-1) < 0
        || result.path("tokens_predicted").asInt() > prepared.outputLimit())
      return new Attempt(
          State.INVALID_OUTPUT,
          content,
          "Input coverage or runtime context not preserved",
          receipt);
    try {
      if (!EvaluationJson.sha256(http.get("/props").toString())
          .equals(prepared.identity().get("propertiesSha256")))
        return new Attempt(
            State.INVALID_OUTPUT, content, "Runtime changed during generation", receipt);
    } catch (IOException exception) {
      return new Attempt(
          State.INVALID_OUTPUT,
          content,
          "Completed response could not be tied to the pinned runtime",
          receipt);
    }
    if (!result.path("stop_type").asText().equals("eos"))
      return new Attempt(
          State.INVALID_OUTPUT, content, "Generation did not terminate at EOS", receipt);
    var effective = result.path("generation_settings");
    var effectiveSeed = effective.path("seed");
    if (!effectiveSeed.isIntegralNumber()
        || !effectiveSeed.canConvertToInt()
        || effectiveSeed.asInt() != seed)
      return new Attempt(
          State.INVALID_OUTPUT, content, "Effective seed differs from the requested seed", receipt);
    for (var option : sampling.requestOptions().entrySet()) {
      var actual = effective.path(option.getKey());
      if (!actual.isNumber()
          || !Double.isFinite(actual.asDouble())
          || (option.getKey().equals("top_k")
              ? !actual.isIntegralNumber() || actual.asInt(-1) != sampling.topK()
              : Float.compare((float) actual.asDouble(), ((Number) option.getValue()).floatValue())
                  != 0))
        return new Attempt(
            State.INVALID_OUTPUT, content, "Effective sampling differs from the request", receipt);
    }
    return new Attempt(State.SUCCESS, content, "Completed without truncation", receipt);
  }

  private Map<String, Object> map(JsonNode value) {
    return EvaluationJson.MAPPER.convertValue(value, new TypeReference<Map<String, Object>>() {});
  }
}
