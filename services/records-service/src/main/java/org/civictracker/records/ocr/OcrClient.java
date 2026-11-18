package org.civictracker.records.ocr;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Flow;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.civictracker.records.application.OperationProblem;
import org.civictracker.records.configuration.PreparationSettings;
import org.civictracker.records.domain.PreparationData.OcrResult;
import org.civictracker.records.domain.PreparationData.Placement;
import org.civictracker.records.domain.PreparationData.Profile;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

/** Separates confirmed inference completion from an uncertain remote execution. */
@Component
public class OcrClient {
  private final PreparationSettings.Settings settings;
  private final ObjectMapper json;

  public OcrClient(PreparationSettings.Settings settings, ObjectMapper json) {
    this.settings = settings;
    this.json = json;
  }

  public record Metadata(
      UUID executionId, String imageSha256, int width, int height, String fingerprint) {}

  public static final class FinishedFailure extends RuntimeException {
    private final String code;

    public FinishedFailure(String code) {
      super(code);
      this.code = code;
    }

    public String code() {
      return code;
    }
  }

  public void preflight() {
    URI uri = settings.ocrBaseUrl();
    boolean local =
        uri != null
            && uri.getHost() != null
            && List.of("127.0.0.1", "localhost", "[::1]").contains(uri.getHost());
    if (uri == null
        || uri.getHost() == null
        || uri.getUserInfo() != null
        || uri.getQuery() != null
        || uri.getFragment() != null
        || !("https".equals(uri.getScheme())
            || "http".equals(uri.getScheme()) && local && settings.allowLoopback())
        || settings.ocrToken() == null
        || settings.ocrToken().isBlank()) {
      throw new OperationProblem(503, "OCR_CONFIGURATION_REQUIRED");
    }
  }

  public OcrResult recognize(UUID execution, byte[] image, Placement placement, Profile profile)
      throws IOException, InterruptedException {
    preflight();
    String boundary = "lcit-" + UUID.randomUUID();
    String prefix =
        "--"
            + boundary
            + "\r\nContent-Disposition: form-data; name=\"metadata\"\r\n"
            + "Content-Type: application/json\r\n\r\n"
            + json.writeValueAsString(
                new Metadata(
                    execution,
                    placement.imageSha256(),
                    placement.width(),
                    placement.height(),
                    profile.ocrFingerprint()))
            + "\r\n--"
            + boundary
            + "\r\nContent-Disposition: form-data; name=\"image\"; "
            + "filename=\"input.png\"\r\nContent-Type: image/png\r\n\r\n";
    var body =
        HttpRequest.BodyPublishers.concat(
            HttpRequest.BodyPublishers.ofByteArray(prefix.getBytes(StandardCharsets.UTF_8)),
            HttpRequest.BodyPublishers.ofByteArray(image),
            HttpRequest.BodyPublishers.ofByteArray(
                ("\r\n--" + boundary + "--\r\n").getBytes(StandardCharsets.US_ASCII)));
    var request =
        HttpRequest.newBuilder(settings.ocrBaseUrl().resolve("/internal/v1/ocr"))
            .timeout(Duration.ofMillis(settings.ocrTimeoutMillis()))
            .header("Authorization", "Bearer " + settings.ocrToken())
            .header("X-OCR-Execution-Id", execution.toString())
            .header("Content-Type", "multipart/form-data; boundary=" + boundary)
            .POST(body)
            .build();
    HttpResponse<byte[]> response;
    try (var client =
        HttpClient.newBuilder()
            .followRedirects(HttpClient.Redirect.NEVER)
            .connectTimeout(Duration.ofMillis(settings.ocrTimeoutMillis()))
            .build()) {
      var future =
          client.sendAsync(request, info -> new LimitedBody(settings.maxOcrResponseBytes()));
      try {
        response = future.get(settings.ocrTimeoutMillis(), TimeUnit.MILLISECONDS);
      } catch (TimeoutException | ExecutionException error) {
        future.cancel(true);
        throw new IOException("OCR execution requires reconciliation", error);
      } catch (InterruptedException error) {
        future.cancel(true);
        throw error;
      }
    }
    var envelope = json.readTree(response.body());
    if (!envelope.path("executionFinished").asBoolean()
        || !execution.toString().equals(envelope.path("executionId").asString())) {
      throw new IOException("OCR did not confirm execution completion");
    }
    if (response.statusCode() != 200) {
      throw new FinishedFailure("OCR_EXECUTION_FAILED");
    }
    try {
      OcrResult result = json.treeToValue(envelope.get("result"), OcrResult.class);
      validate(result, execution, placement, profile);
      return result;
    } catch (RuntimeException error) {
      throw new FinishedFailure("OCR_RESULT_INVALID");
    }
  }

  public static void validate(OcrResult result, UUID execution, Placement image, Profile profile) {
    if (result == null
        || !execution.equals(result.executionId())
        || !image.imageSha256().equals(result.imageSha256())
        || !profile.ocrFingerprint().equals(result.fingerprint())
        || image.width() != result.width()
        || image.height() != result.height()
        || result.regions() == null
        || result.regions().size() > profile.maxRegions()
        || result.provenance() == null
        || result.provenance().isEmpty()) {
      throw new IllegalArgumentException("OCR result identity differs");
    }
    long characters = 0;
    for (var region : result.regions()) {
      if (region == null
          || region.text() == null
          || !Double.isFinite(region.score())
          || region.score() < 0
          || region.score() > 1
          || region.polygon() == null
          || region.polygon().size() != 4) {
        throw new IllegalArgumentException("Invalid OCR region");
      }
      characters += region.text().codePointCount(0, region.text().length());
      for (int n = 0; n < region.text().length(); n++) {
        char value = region.text().charAt(n);
        if (Character.isHighSurrogate(value)) {
          if (++n == region.text().length() || !Character.isLowSurrogate(region.text().charAt(n))) {
            throw new IllegalArgumentException("Invalid Unicode text");
          }
        } else if (Character.isLowSurrogate(value)) {
          throw new IllegalArgumentException("Invalid Unicode text");
        }
      }
      if (characters > profile.maxCharactersPerUnit()) {
        throw new IllegalArgumentException("OCR text limit");
      }
      double area = 0;
      for (int n = 0; n < 4; n++) {
        var point = region.polygon().get(n);
        var next = region.polygon().get((n + 1) % 4);
        if (point == null
            || next == null
            || !Double.isFinite(point.x())
            || !Double.isFinite(point.y())
            || point.x() < 0
            || point.x() > image.width()
            || point.y() < 0
            || point.y() > image.height()) {
          throw new IllegalArgumentException("OCR polygon exceeds its image");
        }
        area += point.x() * next.y() - next.x() * point.y();
      }
      if (Math.abs(area) < 0.00001) {
        throw new IllegalArgumentException("OCR polygon has no area");
      }
    }
  }

  private static final class LimitedBody implements HttpResponse.BodySubscriber<byte[]> {
    private final CompletableFuture<byte[]> result = new CompletableFuture<>();
    private final ByteArrayOutputStream bytes = new ByteArrayOutputStream();
    private final int limit;
    private Flow.Subscription subscription;

    LimitedBody(int limit) {
      this.limit = limit;
    }

    @Override
    public CompletionStage<byte[]> getBody() {
      return result;
    }

    @Override
    public void onSubscribe(Flow.Subscription value) {
      subscription = value;
      value.request(1);
    }

    @Override
    public void onNext(List<ByteBuffer> buffers) {
      for (ByteBuffer buffer : buffers) {
        if (buffer.remaining() > limit - bytes.size()) {
          subscription.cancel();
          result.completeExceptionally(new IOException("OCR response limit"));
          return;
        }
        byte[] part = new byte[buffer.remaining()];
        buffer.get(part);
        bytes.writeBytes(part);
      }
      subscription.request(1);
    }

    @Override
    public void onError(Throwable error) {
      result.completeExceptionally(error);
    }

    @Override
    public void onComplete() {
      result.complete(bytes.toByteArray());
    }
  }
}
