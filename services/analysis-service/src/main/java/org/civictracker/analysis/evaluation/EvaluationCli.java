package org.civictracker.analysis.evaluation;

import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.Base64;
import java.util.HexFormat;
import org.civictracker.analysis.model.LlamaModelClient;
import org.civictracker.analysis.model.ModelClient;
import org.civictracker.analysis.records.PreparedMeetingReader;

/** A developer command, separate from Spring HTTP startup and production processing. */
public final class EvaluationCli {
  private EvaluationCli() {}

  public record Configuration(
      URI recordsBase,
      String recordsUser,
      String recordsPasswordEnv,
      int recordsTimeoutSeconds,
      int pageSize,
      PreparedMeetingReader.Selection selection,
      URI modelBase,
      String modelTokenEnv,
      int modelTimeoutSeconds,
      String modelPath,
      String modelSha256,
      String runtimeBuild,
      int outputTokens,
      int seed,
      ModelClient.Sampling sampling) {}

  public static void main(String[] args) throws Exception {
    if (args.length != 3)
      throw new IllegalArgumentException("Usage: evaluate CONFIG OUTPUT ADMISSION");
    var configuration =
        EvaluationJson.MAPPER.readValue(Files.readString(Path.of(args[0])), Configuration.class);
    if (configuration.recordsUser().contains(":"))
      throw new IllegalArgumentException("Invalid records user");
    String secret = requiredEnvironment(configuration.recordsPasswordEnv());
    String auth =
        "Basic "
            + Base64.getEncoder()
                .encodeToString(
                    (configuration.recordsUser() + ":" + secret)
                        .getBytes(java.nio.charset.StandardCharsets.UTF_8));
    var reader =
        new PreparedMeetingReader(
            new JsonHttp(
                configuration.recordsBase(),
                Duration.ofSeconds(configuration.recordsTimeoutSeconds()),
                auth),
            configuration.pageSize());
    var input = reader.readPinned(configuration.selection());
    Path modelPath = Path.of(configuration.modelPath()).toAbsolutePath().normalize();
    var digest = MessageDigest.getInstance("SHA-256");
    try (var stream = new DigestInputStream(Files.newInputStream(modelPath), digest)) {
      stream.transferTo(java.io.OutputStream.nullOutputStream());
    }
    if (!HexFormat.of().formatHex(digest.digest()).equals(configuration.modelSha256()))
      throw new IOException("Model artifact digest differs from configuration");
    String modelAuth =
        configuration.modelTokenEnv() == null
            ? null
            : "Bearer " + requiredEnvironment(configuration.modelTokenEnv());
    var model =
        new LlamaModelClient(
            new JsonHttp(
                configuration.modelBase(),
                Duration.ofSeconds(configuration.modelTimeoutSeconds()),
                modelAuth),
            modelPath.toString(),
            configuration.modelSha256(),
            configuration.runtimeBuild());
    var state =
        new EvaluationRunner(model)
            .evaluate(
                input,
                configuration.outputTokens(),
                configuration.seed(),
                configuration.sampling(),
                Path.of(args[1]),
                Path.of(args[2]));
    System.out.println("Evaluation: " + state + ". Human source review is separate.");
    if (state != ModelClient.State.SUCCESS) System.exit(2);
  }

  private static String requiredEnvironment(String name) {
    String value = System.getenv(name);
    if (value == null || value.isBlank())
      throw new IllegalArgumentException("Missing configured credential environment variable");
    return value;
  }
}
