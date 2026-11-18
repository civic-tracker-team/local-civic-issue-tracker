package org.civictracker.analysis.evaluation;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import org.civictracker.analysis.domain.CitationIndex;
import org.civictracker.analysis.domain.MeetingInput;
import org.civictracker.analysis.model.Interpretation;
import org.civictracker.analysis.model.ModelClient;
import org.civictracker.analysis.model.ModelContract;

/** Saves provenance and retains admission whenever remote execution is uncertain. */
public final class EvaluationRunner {
  private final ModelClient model;

  public EvaluationRunner(ModelClient model) {
    this.model = model;
  }

  public ModelClient.State evaluate(
      MeetingInput input,
      int outputLimit,
      int seed,
      ModelClient.Sampling sampling,
      Path output,
      Path admission)
      throws IOException, InterruptedException {
    Files.createDirectory(output);
    var receipt = new LinkedHashMap<String, Object>();
    receipt.put("startedAt", Instant.now().toString());
    receipt.put("humanReview", "NOT_REVIEWED");
    receipt.put("meetingId", input.meetingId());
    receipt.put("contractRevision", ModelContract.REVISION);
    receipt.put("contractRevisionDetail", ModelContract.REVISION_DETAIL);
    boolean acquired = false;
    boolean dispatched = false;
    boolean reconciled = false;
    Throwable failure = null;
    try {
      Files.writeString(
          admission, output.toAbsolutePath().normalize().toString(), StandardOpenOption.CREATE_NEW);
      acquired = true;
      save(output, "input.json", input);
      receipt.put(
          "inputSha256", EvaluationJson.sha256(Files.readAllBytes(output.resolve("input.json"))));
      var index = CitationIndex.build(input);
      var request = ModelContract.request(index);
      receipt.put(
          "schemaSha256",
          EvaluationJson.sha256(EvaluationJson.MAPPER.writeValueAsString(request.schema())));
      save(output, "citation-index.json", index.snapshot());
      Files.writeString(
          output.resolve("model-input.txt"), request.inputText(), StandardOpenOption.CREATE_NEW);
      receipt.put(
          "modelInputSha256",
          EvaluationJson.sha256(Files.readAllBytes(output.resolve("model-input.txt"))));
      var messages = request.messages();
      receipt.put(
          "messagesSha256",
          EvaluationJson.sha256(EvaluationJson.MAPPER.writeValueAsString(messages)));
      save(output, "messages.json", messages);
      save(output, "schema.json", request.schema());
      var prepared = model.preflight(messages, outputLimit);
      save(output, "preflight.json", prepared);
      receipt.put("promptTokens", prepared.tokens().size());
      receipt.put("context", prepared.context());
      receipt.put("outputLimit", outputLimit);
      receipt.put("seed", seed);
      receipt.put("sampling", sampling);
      dispatched = prepared.fits();
      var attempt =
          prepared.fits()
              ? model.generate(prepared, request.schema(), seed, sampling)
              : new ModelClient.Attempt(
                  ModelClient.State.CAPACITY_REJECTED,
                  "",
                  "Prompt plus output exceeds context",
                  Map.of());
      reconciled = attempt.state() != ModelClient.State.UNCERTAIN_EXECUTION;
      save(output, "attempt.json", attempt);
      var state = attempt.state();
      if (state == ModelClient.State.SUCCESS) {
        switch (ModelContract.interpret(attempt.content(), index, input)) {
          case Interpretation.Malformed malformed -> {
            save(output, "validation.json", malformed.problems());
            state = ModelClient.State.INVALID_OUTPUT;
          }
          case Interpretation.Unresolved unresolved -> {
            save(output, "model-reply.json", unresolved.reply());
            save(output, "resolution.json", unresolved.traces());
            save(output, "validation.json", unresolved.problems());
            state = ModelClient.State.INVALID_OUTPUT;
          }
          case Interpretation.Interpreted interpreted -> {
            save(output, "model-reply.json", interpreted.reply());
            save(output, "resolution.json", interpreted.traces());
            save(output, "validation.json", interpreted.problems());
            save(output, "candidates.json", interpreted.candidates());
            receipt.put(
                "candidatesSha256",
                EvaluationJson.sha256(Files.readAllBytes(output.resolve("candidates.json"))));
            state =
                interpreted.accepted()
                    ? ModelClient.State.SUCCESS
                    : ModelClient.State.INVALID_OUTPUT;
          }
        }
      }
      receipt.put("state", state);
      return state;
    } catch (InterruptedException exception) {
      failure = exception;
      receipt.put(
          "state",
          dispatched && !reconciled
              ? ModelClient.State.UNCERTAIN_EXECUTION
              : ModelClient.State.RUNTIME_FAILED);
      receipt.put("error", "InterruptedException");
      Thread.currentThread().interrupt();
      throw exception;
    } catch (IOException | RuntimeException exception) {
      failure = exception;
      receipt.put(
          "state",
          dispatched && !reconciled
              ? ModelClient.State.UNCERTAIN_EXECUTION
              : ModelClient.State.RUNTIME_FAILED);
      receipt.put("error", exception.getClass().getSimpleName());
      throw exception;
    } finally {
      receipt.put("finishedAt", Instant.now().toString());
      receipt.put("runtimeReconciliationRequired", acquired && dispatched && !reconciled);
      Throwable cleanupFailure = failure;
      try {
        save(output, "receipt.json", receipt);
      } catch (IOException | RuntimeException exception) {
        if (cleanupFailure == null) cleanupFailure = exception;
        else cleanupFailure.addSuppressed(exception);
      }
      try {
        if (acquired && (!dispatched || reconciled)) Files.delete(admission);
      } catch (IOException | RuntimeException exception) {
        if (cleanupFailure == null) cleanupFailure = exception;
        else cleanupFailure.addSuppressed(exception);
      }
      if (failure == null) {
        if (cleanupFailure instanceof IOException io) throw io;
        if (cleanupFailure instanceof RuntimeException runtime) throw runtime;
      }
    }
  }

  private void save(Path output, String name, Object value) throws IOException {
    Files.writeString(
        output.resolve(name),
        EvaluationJson.MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(value) + "\n",
        StandardOpenOption.CREATE_NEW);
  }
}
