package org.civictracker.analysis;

import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import org.civictracker.analysis.api.AnalysisCommands.*;
import org.civictracker.analysis.domain.MeetingInput;
import org.civictracker.analysis.evaluation.EvaluationJson;
import org.civictracker.analysis.model.ModelClient;
import org.civictracker.analysis.records.PreparedMeetingReader;
import tools.jackson.databind.JsonNode;

/** Synthetic owner receipts and model replies for operation tests. */
class OperationFixture implements AutoCloseable {
  final HttpServer server;
  final MeetingInput input;
  final UUID discovery = UUID.randomUUID(),
      acquisition = UUID.randomUUID(),
      preparationOperation = UUID.randomUUID(),
      item = UUID.randomUUID();
  final AtomicInteger badCredentials = new AtomicInteger();
  volatile boolean unavailable;

  OperationFixture() {
    try {
      input = EvaluationJson.MAPPER.readValue(resource("input.json"), MeetingInput.class);
      server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
      server.createContext(
          "/internal/v1",
          exchange -> {
            String expected =
                "Basic "
                    + java.util.Base64.getEncoder()
                        .encodeToString("reader:secret".getBytes(StandardCharsets.UTF_8));
            if (!expected.equals(exchange.getRequestHeaders().getFirst("Authorization")))
              badCredentials.incrementAndGet();
            if (unavailable) {
              exchange.sendResponseHeaders(503, -1);
              exchange.close();
              return;
            }
            String path = exchange.getRequestURI().getPath();
            var source = input.sources().getFirst();
            Object body;
            if (path.endsWith("/meetings"))
              body =
                  page(
                      List.of(
                          Map.of(
                              "id",
                              input.meetingId(),
                              "source",
                              Map.of(
                                  "officialId",
                                  input.officialId(),
                                  "localDate",
                                  input.meetingDate(),
                                  "title",
                                  input.title()))));
            else if (path.endsWith("/originals"))
              body =
                  page(
                      List.of(
                          Map.of(
                              "id",
                              item,
                              "retained",
                              Map.of(
                                  "documentId",
                                  source.documentId(),
                                  "versionId",
                                  source.versionId(),
                                  "artifactId",
                                  source.artifactId(),
                                  "sha256",
                                  source.sha256(),
                                  "byteLength",
                                  source.byteLength()))));
            else if (path.endsWith("/preparations"))
              body =
                  page(
                      List.of(
                          Map.of(
                              "acquisitionItemId",
                              item,
                              "documentId",
                              source.documentId(),
                              "versionId",
                              source.versionId(),
                              "preparationId",
                              source.preparationId(),
                              "profileFingerprint",
                              source.profileFingerprint())));
            else if (path.endsWith("/content"))
              body =
                  Map.of(
                      "documentId",
                      source.documentId(),
                      "versionId",
                      source.versionId(),
                      "preparationId",
                      source.preparationId(),
                      "profileFingerprint",
                      source.profileFingerprint(),
                      "unitCount",
                      1,
                      "units",
                      source.units(),
                      "page",
                      0,
                      "size",
                      100,
                      "hasNext",
                      false);
            else if (path.contains("/document-versions/"))
              body =
                  Map.of(
                      "id",
                      source.versionId(),
                      "documentId",
                      source.documentId(),
                      "artifactId",
                      source.artifactId(),
                      "sha256",
                      source.sha256(),
                      "byteLength",
                      source.byteLength(),
                      "sourceUrls",
                      source.sourceUrls());
            else {
              UUID id = UUID.fromString(path.substring(path.lastIndexOf('/') + 1));
              body =
                  Map.of(
                      "id",
                      id,
                      "kind",
                      id.equals(discovery)
                          ? "DISCOVERY"
                          : id.equals(acquisition) ? "ACQUISITION" : "PREPARATION",
                      "state",
                      "SUCCEEDED");
            }
            byte[] bytes = EvaluationJson.MAPPER.writeValueAsBytes(body);
            exchange.sendResponseHeaders(200, bytes.length);
            exchange.getResponseBody().write(bytes);
            exchange.close();
          });
      server.start();
    } catch (Exception error) {
      throw new IllegalStateException(error);
    }
  }

  String base() {
    return "http://127.0.0.1:" + server.getAddress().getPort();
  }

  Command command() {
    var s = input.sources().getFirst();
    return new Command(
        UUID.randomUUID(),
        new Selection(
            discovery,
            acquisition,
            preparationOperation,
            input.meetingId(),
            input.officialId(),
            input.meetingDate(),
            input.title(),
            List.of(
                new PreparedMeetingReader.Pin(
                    item,
                    s.documentId(),
                    s.versionId(),
                    s.artifactId(),
                    s.sha256(),
                    s.byteLength(),
                    s.preparationId(),
                    s.profileFingerprint()))),
        List.of());
  }

  static String resource(String name) throws Exception {
    try (var stream =
        OperationFixture.class.getResourceAsStream("/import-trial/synthetic/" + name)) {
      return new String(
          java.util.Objects.requireNonNull(stream).readAllBytes(), StandardCharsets.UTF_8);
    }
  }

  private Object page(Object items) {
    return Map.of("items", items, "page", 0, "size", 100, "hasNext", false);
  }

  public void close() {
    server.stop(0);
  }

  static class FakeModel implements ModelClient {
    volatile String mode = "SUCCESS";
    volatile String contentOverride;
    final AtomicInteger calls = new AtomicInteger();

    public Prepared preflight(List<Map<String, String>> messages, int output)
        throws java.io.IOException {
      if (mode.equals("PREFLIGHT_FAILURE"))
        throw new java.io.IOException("synthetic pre-send error");
      return new Prepared(
          "synthetic",
          java.util.Collections.nCopies(mode.equals("CAPACITY_REJECTED") ? 1000 : 12, 1),
          1000,
          output,
          Map.of(
              "modelSha256",
              "b".repeat(64),
              "runtimeBuild",
              "synthetic",
              "reasoning",
              false,
              "properties",
              Map.of("model_path", "different-machine.gguf")));
    }

    public Attempt generate(Prepared prepared, JsonNode schema, int seed, Sampling sampling)
        throws java.io.IOException {
      calls.incrementAndGet();
      if (mode.equals("LOST_RESPONSE")) throw new java.io.IOException("synthetic lost response");
      try {
        var state =
            mode.equals("RUNTIME_FAILED")
                ? State.RUNTIME_FAILED
                : mode.equals("UNCERTAIN_EXECUTION") ? State.UNCERTAIN_EXECUTION : State.SUCCESS;
        return new Attempt(
            state,
            mode.equals("INVALID_OUTPUT")
                ? "{}"
                : contentOverride != null
                    ? contentOverride
                    : EvaluationJson.MAPPER
                        .readTree(resource("attempt.json"))
                        .path("content")
                        .asText(),
            "synthetic",
            Map.of());
      } catch (Exception error) {
        throw new java.io.IOException(error);
      }
    }
  }
}
