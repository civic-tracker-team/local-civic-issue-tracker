package org.civictracker.analysis.evaluation;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.List;
import java.util.Map;
import org.civictracker.analysis.domain.CandidateValidator;
import org.civictracker.analysis.domain.Candidates;
import org.civictracker.analysis.domain.MeetingInput;

public final class ReviewCli {
  private ReviewCli() {}

  public record Judgments(
      String inputSha256, String reviewer, List<AssociationReview.Match> matches) {}

  public static void main(String[] args) throws Exception {
    if (args.length != 4)
      throw new IllegalArgumentException("Usage: score RUN REFERENCES JUDGMENTS OUTPUT");
    var run = Path.of(args[0]);
    var receipt = EvaluationJson.MAPPER.readTree(Files.readString(run.resolve("receipt.json")));
    String state = receipt.path("state").asText();
    if (!(state.equals("SUCCESS") || state.equals("INVALID_OUTPUT")))
      throw new IllegalArgumentException(
          "Failed or uncertain generations cannot be scored as findings");
    String inputSha = EvaluationJson.sha256(Files.readAllBytes(run.resolve("input.json")));
    if (!inputSha.equals(receipt.path("inputSha256").asText()))
      throw new IllegalArgumentException("Run input changed");
    var input =
        EvaluationJson.MAPPER.readValue(
            Files.readString(run.resolve("input.json")), MeetingInput.class);
    var output =
        EvaluationJson.MAPPER.readValue(
            Files.readString(run.resolve("candidates.json")), Candidates.class);
    String candidatesSha =
        EvaluationJson.sha256(Files.readAllBytes(run.resolve("candidates.json")));
    if (!candidatesSha.equals(receipt.path("candidatesSha256").asText()))
      throw new IllegalArgumentException("Run candidates changed");
    var references =
        EvaluationJson.MAPPER.readValue(
            Files.readString(Path.of(args[1])), AssociationReview.ReferenceSet.class);
    var review =
        EvaluationJson.MAPPER.readValue(Files.readString(Path.of(args[2])), Judgments.class);
    if (!inputSha.equals(review.inputSha256()))
      throw new IllegalArgumentException("Judgments address another input");
    var metrics =
        new AssociationReview()
            .score(
                output,
                new CandidateValidator().validate(output, input),
                inputSha,
                references,
                review.reviewer(),
                review.matches());
    var result =
        Map.of(
            "inputSha256",
            inputSha,
            "generationState",
            state,
            "provenance",
            Map.of(
                "reviewer",
                review.reviewer(),
                "candidatesSha256",
                candidatesSha,
                "referencesSha256",
                EvaluationJson.sha256(Files.readAllBytes(Path.of(args[1]))),
                "judgmentsSha256",
                EvaluationJson.sha256(Files.readAllBytes(Path.of(args[2])))),
            "counts",
            metrics,
            "precision",
            metrics.precision(),
            "recall",
            metrics.recall(),
            "evidencePresence",
            metrics.evidencePresence(),
            "evidenceSupport",
            metrics.evidenceSupport());
    Files.writeString(
        Path.of(args[3]),
        EvaluationJson.MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(result) + "\n",
        StandardOpenOption.CREATE_NEW);
  }
}
