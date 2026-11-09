package org.civictracker.records;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.civictracker.records.application.RecordsFoundation;
import org.civictracker.records.domain.SourceInput;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;

/** Test-only child process used to check persisted data across separate service JVMs. */
public final class RecordsRestartProbe {
  private RecordsRestartProbe() {}

  public static void main(String[] args) throws Exception {
    try (var context = new SpringApplicationBuilder(RecordsServiceApplication.class)
        .web(WebApplicationType.NONE).run()) {
      var records = context.getBean(RecordsFoundation.class);
      if (args[0].equals("seed")) {
        String portal = "restart-test-" + UUID.randomUUID();
        var first = records.retain(input(portal, "old-id", "2026-11-08T12:00:00Z"), bytes("first"));
        records.retain(input(portal, "new-id", "2026-11-08T12:00:01Z"), bytes("first"));
        var latest = records.retain(
            input(portal, "new-id", "2026-11-08T12:00:02Z"), bytes("latest"));
        System.out.println("FOUNDATION_PROBE " + first.documentId() + " " + first.versionId()
            + " " + latest.versionId());
      } else {
        UUID documentId = UUID.fromString(args[1]);
        UUID firstVersionId = UUID.fromString(args[2]);
        UUID latestVersionId = UUID.fromString(args[3]);
        var current = records.inspect(documentId);
        require(current.versionId().equals(latestVersionId), "Current version changed");
        require(current.versionCount() == 2, "Version history lost");
        require(current.primaryIds().equals(List.of("new-id")), "Primary alias lost");
        require(current.historicalIds().equals(List.of("old-id")), "Historical alias lost");
        require(records.inspectVersion(firstVersionId).documentId().equals(documentId),
            "Version ownership changed");
        try (var original = records.openVersion(firstVersionId);
            var latest = records.openOriginal(documentId)) {
          require(new String(original.readAllBytes(), StandardCharsets.UTF_8).equals("first"),
              "Retained historical bytes differ");
          require(new String(latest.readAllBytes(), StandardCharsets.UTF_8).equals("latest"),
              "Current original bytes differ");
        }
        System.out.println("FOUNDATION_RESTART_OK");
      }
    }
  }

  private static ByteArrayInputStream bytes(String content) {
    return new ByteArrayInputStream(content.getBytes(StandardCharsets.UTF_8));
  }

  private static SourceInput input(String portal, String id, String at) {
    return new SourceInput(portal, "meeting", "Restart fixture", "2026-11-01", "item",
        "Item 1", null, null, "en", "attachment-history", id,
        java.net.URI.create("https://example.invalid/restart"), "fixture.pdf",
        Instant.parse(at), true, null);
  }

  private static void require(boolean condition, String message) {
    if (!condition) {
      throw new IllegalStateException(message);
    }
  }
}
