package org.civictracker.search.query;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Slim search results over one published collection. Lists and totals appear only for the
 * selected types (FR-36). {@code collection} states what the results cover (FR-43, FR-44).
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record SearchResponse(
    Collection collection,
    Query query,
    Map<String, Long> totals,
    List<Hit> issues,
    List<Hit> records,
    List<Hit> meetings) {

  /**
   * The published collection searched. {@code recordsSearchedByTitleOnly} counts records whose
   * prepared text could not be selected unambiguously, so only their labels were indexed.
   */
  public record Collection(
      UUID publicationId,
      Instant indexedAt,
      List<Range> coverage,
      List<String> recordTypes,
      int meetingCount,
      int recordsSearchedByTitleOnly) {}

  public record Range(LocalDate from, LocalDate to) {}

  public record Query(String q, List<String> types, int limit, int offset) {}

  /**
   * One result. {@code versionId} is set for records only. {@code snippet} is plain source text
   * split into fragments; {@code match} marks matched words. Nothing in it is markup.
   */
  @JsonInclude(JsonInclude.Include.ALWAYS)
  public record Hit(
      UUID id,
      UUID versionId,
      String label,
      LocalDate date,
      String location,
      List<Fragment> snippet,
      Map<String, Object> details) {}

  public record Fragment(String text, boolean match) {}
}
