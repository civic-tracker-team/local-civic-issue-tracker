package org.civictracker.records.domain;

import java.net.URI;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/** Owner values separate source observations from operation execution. */
public final class AcquisitionData {
  private AcquisitionData() {}

  public enum Kind {
    DISCOVERY,
    ACQUISITION
  }

  public enum State {
    ACCEPTED,
    RUNNING,
    SUCCEEDED,
    FAILED
  }

  public enum Format {
    HTML,
    PDF,
    UNSUPPORTED
  }

  public record Profile(
      URI baseUrl,
      List<String> allowedHosts,
      String revision,
      long requestIntervalMillis,
      long connectTimeoutMillis,
      long transferTimeoutMillis,
      long maxBytes,
      int maxRedirects,
      boolean allowLoopback) {
    public Profile {
      allowedHosts = List.copyOf(allowedHosts);
    }
  }

  public record Selection(
      String sourceId, Profile profile, LocalDate from, LocalDate to, List<String> committeeIds) {
    public Selection {
      committeeIds = List.copyOf(committeeIds);
    }
  }

  public record Acquisition(UUID discoveryId, UUID meetingId, UUID runId) {}

  public record Target(
      String family,
      String officialId,
      URI url,
      Format format,
      String itemId,
      String itemLabel,
      String templateId,
      String outputType,
      String language,
      String title,
      String publicationDate) {}

  public record Item(String officialId, String label) {}

  public record Meeting(
      String officialId,
      String committeeId,
      String title,
      String sourceDate,
      LocalDate localDate,
      String publicationDate,
      String videoUrl,
      String cancellationEvidence,
      List<Target> documents) {
    public Meeting {
      documents = List.copyOf(documents);
    }
  }

  public record Packet(List<Item> items, List<Target> attachments) {
    public Packet {
      items = List.copyOf(items);
      attachments = List.copyOf(attachments);
    }
  }

  public record Receipt(
      UUID documentId,
      UUID versionId,
      UUID artifactId,
      UUID observationId,
      UUID currentVersionId,
      String sha256,
      long byteLength,
      boolean currentChanged) {}

  public record Observation(String portal, Meeting meeting, Instant observedAt) {
    public Observation {
      observedAt = observedAt.truncatedTo(java.time.temporal.ChronoUnit.MICROS);
    }
  }
}
