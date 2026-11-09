package org.civictracker.records.domain;

import java.net.URI;
import java.time.Instant;
import java.util.Objects;

/** A trusted caller's source observation; source text cannot establish currentness. */
public record SourceInput(
    String portal, String meetingId, String meetingTitle, String meetingDate,
    String itemId, String itemLabel, String templateId, String outputType, String language,
    String identifierFamily, String portalId, URI sourceUrl, String originalFilename,
    Instant observedAt, boolean currentSource, String cancellationEvidence) {
  public SourceInput {
    Objects.requireNonNull(portal);
    Objects.requireNonNull(meetingId);
    Objects.requireNonNull(identifierFamily);
    Objects.requireNonNull(sourceUrl);
    Objects.requireNonNull(observedAt);
    observedAt = observedAt.truncatedTo(java.time.temporal.ChronoUnit.MICROS);
    if (portal.isBlank() || meetingId.isBlank() || identifierFamily.isBlank()
        || !sourceUrl.isAbsolute() || sourceUrl.getHost() == null
        || !(sourceUrl.getScheme().equalsIgnoreCase("https")
            || sourceUrl.getScheme().equalsIgnoreCase("http"))) {
      throw new IllegalArgumentException("Invalid source context or absolute HTTP source URL");
    }
    if (portalId != null && portalId.isBlank()) {
      throw new IllegalArgumentException("Empty portal identifier");
    }
    for (String value : new String[] {itemId, templateId, outputType, language}) {
      if (value != null && value.isBlank()) {
        throw new IllegalArgumentException("Empty source scope field; use null when unavailable");
      }
    }
  }
}
