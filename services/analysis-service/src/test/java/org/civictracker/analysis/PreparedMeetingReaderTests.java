package org.civictracker.analysis;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.time.Duration;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import org.civictracker.analysis.evaluation.EvaluationJson;
import org.civictracker.analysis.evaluation.JsonHttp;
import org.civictracker.analysis.records.PreparedMeetingReader;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class PreparedMeetingReaderTests {
  @ParameterizedTest
  @ValueSource(
      strings = {
        "ok",
        "missing-page",
        "wrong-revision",
        "wrong-count",
        "duplicate-segment",
        "future-prior",
        "401",
        "403",
        "404",
        "409"
      })
  void verifiesCompletePinnedCoverage(String mode) throws Exception {
    UUID discovery = UUID.randomUUID(),
        acquisition = UUID.randomUUID(),
        operation = UUID.randomUUID();
    UUID meeting = UUID.randomUUID(),
        item = UUID.randomUUID(),
        document = UUID.randomUUID(),
        version = UUID.randomUUID();
    UUID artifact = UUID.randomUUID(), preparation = UUID.randomUUID(), segment = UUID.randomUUID();
    String sha = "a".repeat(64), fingerprint = "b".repeat(64);
    var pin =
        new PreparedMeetingReader.Pin(
            item, document, version, artifact, sha, 10, preparation, fingerprint);
    var selection =
        new PreparedMeetingReader.Selection(
            discovery,
            acquisition,
            operation,
            meeting,
            "123",
            LocalDate.of(2026, 1, 15),
            "Council",
            List.of(pin),
            mode.equals("future-prior")
                ? List.of(
                    new org.civictracker.analysis.domain.MeetingInput.PriorIssue(
                        "prior-1",
                        "A later reviewed issue",
                        LocalDate.of(2026, 2, 1),
                        "c".repeat(64)))
                : List.of());
    var pages = new AtomicInteger();
    var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    server.createContext(
        "/internal/v1",
        exchange -> {
          String path = exchange.getRequestURI().getPath();
          String query = exchange.getRequestURI().getQuery();
          int page =
              query == null
                  ? 0
                  : Integer.parseInt(query.replaceAll(".*(?:^|&)page=(\\d+).*", "$1"));
          Object body;
          if (path.endsWith("/content")) {
            pages.incrementAndGet();
            if (mode.matches("\\d+")) {
              exchange.sendResponseHeaders(Integer.parseInt(mode), -1);
              exchange.close();
              return;
            }
            UUID segmentId =
                page == 0 || mode.equals("duplicate-segment")
                    ? segment
                    : UUID.nameUUIDFromBytes("second".getBytes());
            var locator = new LinkedHashMap<String, Object>();
            locator.put("kind", "pdf");
            locator.put("page", page + 1);
            locator.put("image", null);
            var unit =
                Map.of(
                    "ordinal",
                    page,
                    "disposition",
                    "NATIVE",
                    "nativeText",
                    "page " + page,
                    "segments",
                    List.of(
                        new org.civictracker.analysis.domain.MeetingInput.Segment(
                            segmentId, "page " + page, "PDF_NATIVE", locator, null, true)),
                    "notes",
                    List.of());
            body =
                Map.of(
                    "documentId",
                    document,
                    "versionId",
                    version,
                    "preparationId",
                    mode.equals("wrong-revision") ? UUID.randomUUID() : preparation,
                    "profileFingerprint",
                    fingerprint,
                    "unitCount",
                    mode.equals("wrong-count") ? 3 : 2,
                    "units",
                    List.of(unit),
                    "page",
                    page,
                    "size",
                    1,
                    "hasNext",
                    page == 0 && !mode.equals("missing-page"));
          } else if (path.endsWith("/meetings")) {
            body =
                Map.of(
                    "items",
                    List.of(
                        Map.of(
                            "id",
                            meeting,
                            "source",
                            Map.of(
                                "officialId",
                                "123",
                                "localDate",
                                "2026-01-15",
                                "title",
                                "Council"))),
                    "page",
                    0,
                    "size",
                    1,
                    "hasNext",
                    false);
          } else if (path.endsWith("/originals")) {
            body =
                Map.of(
                    "items",
                    List.of(
                        Map.of(
                            "id",
                            item,
                            "retained",
                            Map.of(
                                "documentId",
                                document,
                                "versionId",
                                version,
                                "artifactId",
                                artifact,
                                "sha256",
                                sha,
                                "byteLength",
                                10))),
                    "page",
                    0,
                    "size",
                    1,
                    "hasNext",
                    false);
          } else if (path.endsWith("/preparations")) {
            body =
                Map.of(
                    "items",
                    List.of(
                        Map.of(
                            "acquisitionItemId",
                            item,
                            "documentId",
                            document,
                            "versionId",
                            version,
                            "preparationId",
                            preparation,
                            "profileFingerprint",
                            fingerprint)),
                    "page",
                    0,
                    "size",
                    1,
                    "hasNext",
                    false);
          } else if (path.contains("/document-versions/")) {
            body =
                Map.of(
                    "id",
                    version,
                    "documentId",
                    document,
                    "artifactId",
                    artifact,
                    "sha256",
                    sha,
                    "byteLength",
                    10,
                    "sourceUrls",
                    List.of("https://example.org/original"));
          } else {
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
    try {
      var reader =
          new PreparedMeetingReader(
              new JsonHttp(
                  URI.create("http://127.0.0.1:" + server.getAddress().getPort()),
                  Duration.ofSeconds(2),
                  null),
              1);
      if (mode.equals("ok")) {
        var input = reader.readPinned(selection);
        assertEquals(2, input.sources().getFirst().units().size());
        assertEquals(2, input.selectedSegments().size());
        assertEquals(2, pages.get());
      } else assertThrows(IOException.class, () -> reader.readPinned(selection));
    } finally {
      server.stop(0);
    }
  }
}
