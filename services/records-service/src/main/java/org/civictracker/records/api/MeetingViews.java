package org.civictracker.records.api;

import java.util.List;
import java.util.UUID;

public final class MeetingViews {
  private MeetingViews() {}

  public record Summary(
      UUID id,
      String sourcePortal,
      String officialMeetingId,
      String committeeId,
      String title,
      String sourceDate,
      String publicationDate,
      String videoUrl,
      String cancellationEvidence) {}

  public record Item(UUID id, String officialItemId, String label) {}

  public record Document(
      UUID documentId, UUID itemId, String templateId, String outputType, String language) {}

  public record Detail(Summary meeting, List<Item> items, List<Document> documents) {
    public Detail {
      items = List.copyOf(items);
      documents = List.copyOf(documents);
    }
  }
}
