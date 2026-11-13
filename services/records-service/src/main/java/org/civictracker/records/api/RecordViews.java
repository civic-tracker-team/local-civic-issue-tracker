package org.civictracker.records.api;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Metadata contracts exclude stored text, file keys and persistence entities. */
public final class RecordViews {
  private RecordViews() {}

  public record SourceId(UUID meetingId, String family, String value, boolean primary) {}

  public record Context(
      UUID meetingId,
      String sourcePortal,
      String officialMeetingId,
      UUID itemId,
      String officialItemId,
      String itemLabel,
      String templateId,
      String outputType,
      String language) {}

  public record Document(
      UUID id,
      UUID currentVersionId,
      List<SourceId> sourceIds,
      List<Context> contexts,
      List<String> sourceUrls,
      List<UUID> versionIds) {
    public Document {
      sourceIds = List.copyOf(sourceIds);
      contexts = List.copyOf(contexts);
      sourceUrls = List.copyOf(sourceUrls);
      versionIds = List.copyOf(versionIds);
    }
  }

  public record Version(
      UUID id,
      UUID documentId,
      String sha256,
      long byteLength,
      UUID artifactId,
      String originalFilename,
      Instant firstObservedAt,
      List<String> sourceUrls,
      List<Observation> observations) {
    public Version {
      sourceUrls = List.copyOf(sourceUrls);
      observations = List.copyOf(observations);
    }
  }

  public record Observation(
      String sourceUrl,
      String resolvedUrl,
      String publicationDate,
      Instant observedAt,
      boolean currentSource) {}
}
