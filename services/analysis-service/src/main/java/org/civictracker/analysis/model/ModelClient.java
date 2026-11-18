package org.civictracker.analysis.model;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import tools.jackson.databind.JsonNode;

public interface ModelClient {
  record Sampling(
      double temperature,
      double topP,
      int topK,
      double minP,
      double presencePenalty,
      double repeatPenalty) {
    public Sampling {
      if (!Double.isFinite(temperature)
          || temperature < 0
          || !Double.isFinite(topP)
          || topP <= 0
          || topP > 1
          || topK < 0
          || !Double.isFinite(minP)
          || minP < 0
          || minP > 1
          || !Double.isFinite(presencePenalty)
          || !Double.isFinite(repeatPenalty)
          || repeatPenalty <= 0)
        throw new IllegalArgumentException("Invalid sampling configuration");
    }

    public static Sampling baseline() {
      return new Sampling(.2, .95, 40, .05, 0, 1);
    }

    public static Sampling qwenNonThinking() {
      return new Sampling(.7, .8, 20, 0, 1.5, 1);
    }

    public Map<String, Object> requestOptions() {
      return Map.of(
          "temperature",
          temperature,
          "top_p",
          topP,
          "top_k",
          topK,
          "min_p",
          minP,
          "presence_penalty",
          presencePenalty,
          "repeat_penalty",
          repeatPenalty);
    }
  }

  record Prepared(
      String renderedPrompt,
      List<Integer> tokens,
      int context,
      int outputLimit,
      Map<String, Object> identity) {
    public Prepared {
      tokens = List.copyOf(tokens);
      identity = Map.copyOf(identity);
    }

    public boolean fits() {
      return (long) tokens.size() + outputLimit <= context;
    }
  }

  enum State {
    SUCCESS,
    CAPACITY_REJECTED,
    INVALID_OUTPUT,
    RUNTIME_FAILED,
    UNCERTAIN_EXECUTION
  }

  record Attempt(State state, String content, String reason, Map<String, Object> receipt) {
    public Attempt {
      receipt = Map.copyOf(receipt);
    }
  }

  Prepared preflight(List<Map<String, String>> messages, int outputLimit)
      throws IOException, InterruptedException;

  Attempt generate(Prepared prepared, JsonNode schema, int seed, Sampling sampling)
      throws IOException, InterruptedException;
}
