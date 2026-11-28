package org.civictracker.search.owners;

import java.time.LocalDate;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** The owner data one index build reads, restricted to the pinned publication. */
public final class Published {
  private Published() {}

  public record Range(LocalDate from, LocalDate to) {}

  public record SourceRef(UUID documentId, UUID versionId) {}

  /** A manifest meeting. {@code revisionId} is null unless the disposition is ANALYZED. */
  public record Meeting(
      UUID meetingId,
      String officialId,
      LocalDate date,
      String title,
      String disposition,
      UUID revisionId,
      List<SourceRef> sources) {
    public Meeting {
      sources = List.copyOf(sources);
    }
  }

  /**
   * A pinned publication. {@code preparations} holds the preparation the manifest pins for a
   * source version, when the coordinator states one.
   */
  public record Manifest(
      UUID publicationId,
      List<Range> coverage,
      List<Meeting> meetings,
      Map<SourceRef, UUID> preparations) {
    public Manifest {
      coverage = List.copyOf(coverage);
      meetings = List.copyOf(meetings);
      preparations = Map.copyOf(preparations);
    }

    public Manifest(UUID publicationId, List<Range> coverage, List<Meeting> meetings) {
      this(publicationId, coverage, meetings, Map.of());
    }
  }

  /** Records' meeting metadata: committee and agenda item labels keyed by official item ID. */
  public record MeetingRecords(String committeeId, Map<String, String> itemLabels) {
    public MeetingRecords {
      // Keep agenda order: meeting rows join the labels in this order.
      itemLabels = Collections.unmodifiableMap(new LinkedHashMap<>(itemLabels));
    }
  }

  public record Citation(UUID documentId, UUID versionId, UUID preparationId) {}

  public record Development(UUID id, String description, List<Citation> evidence) {
    public Development {
      evidence = List.copyOf(evidence);
    }
  }

  public record IssueMention(UUID issueId, String name) {}

  public record Association(
      UUID developmentId, UUID issueId, String basis, List<Citation> evidence) {
    public Association {
      evidence = List.copyOf(evidence);
    }
  }

  public record Revision(
      UUID revisionId,
      UUID meetingId,
      LocalDate meetingDate,
      List<Development> developments,
      List<IssueMention> issues,
      List<Association> associations) {
    public Revision {
      developments = List.copyOf(developments);
      issues = List.copyOf(issues);
      associations = List.copyOf(associations);
    }
  }

  /**
   * One pinned byte version. {@code family} is the primary source identifier family, and {@code
   * itemLabels} maps meeting IDs to the agenda item the document belongs to there.
   */
  public record Version(
      UUID documentId,
      UUID versionId,
      String title,
      String family,
      Map<UUID, String> itemLabels,
      Set<UUID> preparationIds) {
    public Version {
      itemLabels = Map.copyOf(itemLabels);
      preparationIds = Set.copyOf(preparationIds);
    }
  }

  /** One prepared unit's selected text and a readable location, such as "Page 3". */
  public record Unit(int ordinal, String text, String location) {}

  /** Everything an index build needs, read for one pinned publication. */
  public record Collection(
      Manifest manifest,
      Map<UUID, MeetingRecords> meetings,
      Map<UUID, Revision> revisions,
      Map<SourceRef, Version> versions,
      Map<SourceRef, List<Unit>> content) {
    public Collection {
      for (var meeting : manifest.meetings()) {
        if (!meetings.containsKey(meeting.meetingId())
            || (meeting.revisionId() != null && !revisions.containsKey(meeting.revisionId()))
            || !meeting.sources().stream().allMatch(versions::containsKey)) {
          throw new IllegalArgumentException("Collection is missing manifest data");
        }
      }
      meetings = Map.copyOf(meetings);
      revisions = Map.copyOf(revisions);
      versions = Map.copyOf(versions);
      content = Map.copyOf(content);
    }
  }
}
