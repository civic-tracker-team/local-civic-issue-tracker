package org.civictracker.analysis.configuration;

import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.Base64;
import java.util.HexFormat;
import org.civictracker.analysis.evaluation.JsonHttp;
import org.civictracker.analysis.model.*;
import org.civictracker.analysis.records.PreparedMeetingReader;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import tools.jackson.databind.JsonNode;

/** Creates owner clients only when processing is requested. */
@Configuration
public class AnalysisRuntime {
  private final Environment environment;

  public AnalysisRuntime(Environment environment) {
    this.environment = environment;
  }

  public int outputTokens() {
    return environment.getProperty("analysis.model.output-tokens", Integer.class, 8192);
  }

  public int seed() {
    return environment.getProperty("analysis.model.seed", Integer.class, 1);
  }

  public ModelClient.Sampling sampling() {
    return new ModelClient.Sampling(
        number("temperature", .7),
        number("top-p", .8),
        environment.getProperty("analysis.model.top-k", Integer.class, 20),
        number("min-p", 0),
        number("presence-penalty", 1.5),
        number("repeat-penalty", 1));
  }

  private double number(String name, double fallback) {
    return environment.getProperty("analysis.model." + name, Double.class, fallback);
  }

  public PreparedMeetingReader reader() {
    String user = required("analysis.records.user"),
        password = required("analysis.records.password");
    if (user.contains(":")) throw new IllegalArgumentException("Invalid Records identity");
    String auth =
        "Basic "
            + Base64.getEncoder()
                .encodeToString(
                    (user + ":" + password).getBytes(java.nio.charset.StandardCharsets.UTF_8));
    return new PreparedMeetingReader(
        new JsonHttp(
            URI.create(required("analysis.records.base-url")),
            timeout("analysis.records.timeout-seconds"),
            auth),
        environment.getProperty("analysis.records.page-size", Integer.class, 100));
  }

  @Bean
  @ConditionalOnMissingBean(ModelClient.class)
  ModelClient modelClient() {
    return new ModelClient() {
      public Prepared preflight(java.util.List<java.util.Map<String, String>> messages, int limit)
          throws IOException, InterruptedException {
        Path path = Path.of(required("analysis.model.path"));
        try {
          var digest = MessageDigest.getInstance("SHA-256");
          try (var stream = new DigestInputStream(Files.newInputStream(path), digest)) {
            stream.transferTo(java.io.OutputStream.nullOutputStream());
          }
          if (!HexFormat.of().formatHex(digest.digest()).equals(required("analysis.model.sha256")))
            throw new IOException("Model artifact digest differs from configuration");
        } catch (java.security.NoSuchAlgorithmException error) {
          throw new IllegalStateException(error);
        }
        return client().preflight(messages, limit);
      }

      public Attempt generate(Prepared prepared, JsonNode schema, int seed, Sampling sampling)
          throws IOException, InterruptedException {
        return client().generate(prepared, schema, seed, sampling);
      }
    };
  }

  private ModelClient client() {
    String token = environment.getProperty("analysis.model.token");
    return new LlamaModelClient(
        new JsonHttp(
            URI.create(required("analysis.model.base-url")),
            timeout("analysis.model.timeout-seconds"),
            token == null || token.isBlank() ? null : "Bearer " + token),
        Path.of(required("analysis.model.path")).toAbsolutePath().normalize().toString(),
        required("analysis.model.sha256"),
        required("analysis.model.runtime-build"));
  }

  private Duration timeout(String key) {
    return Duration.ofSeconds(Integer.parseInt(required(key)));
  }

  private String required(String key) {
    String value = environment.getProperty(key);
    if (value == null || value.isBlank())
      throw new IllegalArgumentException("Missing analysis runtime configuration");
    return value;
  }
}
