package org.civictracker.search.owners;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.civictracker.search.owners.Published.Association;
import org.civictracker.search.owners.Published.Citation;
import org.civictracker.search.owners.Published.Development;
import org.civictracker.search.owners.Published.IssueMention;
import org.civictracker.search.owners.Published.Manifest;
import org.civictracker.search.owners.Published.Meeting;
import org.civictracker.search.owners.Published.MeetingRecords;
import org.civictracker.search.owners.Published.Range;
import org.civictracker.search.owners.Published.Revision;
import org.civictracker.search.owners.Published.SourceRef;
import org.civictracker.search.owners.Published.Unit;
import org.civictracker.search.owners.Published.Version;
import tools.jackson.databind.JsonNode;

/**
 * Reads one pinned publication from its owners. Only the meetings, analysis revisions and
 * document versions named by the manifest are read (ADR-0051): owners do not check membership,
 * so nothing outside the manifest may reach the index.
 */
public final class PublishedSources {
  /** Records' default and maximum prepared-content page size. */
  static final int CONTENT_PAGE_SIZE = 20;

  /** Longest unit text kept, matching Records' default per-unit character limit. */
  static final int MAX_UNIT_CHARACTERS = 200_000;

  private final OwnerHttp coordinator;
  private final OwnerHttp records;
  private final OwnerHttp analysis;

  public PublishedSources(OwnerHttp coordinator, OwnerHttp records, OwnerHttp analysis) {
    this.coordinator = coordinator;
    this.records = records;
    this.analysis = analysis;
  }

  /** The current manifest, or empty before the first publication. */
  public Optional<Manifest> current() throws OwnerUnavailable, InterruptedException {
    var found = coordinator.find("/internal/v1/publications/current", "NO_PUBLICATION");
    return found.isEmpty() ? Optional.empty() : Optional.of(manifest(found.get()));
  }

  /** Reads everything an index build needs for this exact manifest. */
  public Published.Collection collect(Manifest manifest)
      throws OwnerUnavailable, InterruptedException {
    var meetings = new LinkedHashMap<UUID, MeetingRecords>();
    var revisions = new LinkedHashMap<UUID, Revision>();
    var refs = new LinkedHashSet<SourceRef>();
    var firstMeeting = new HashMap<SourceRef, UUID>();
    for (var meeting : manifest.meetings()) {
      meetings.put(meeting.meetingId(), meetingRecords(meeting.meetingId()));
      if (meeting.disposition().equals("ANALYZED")) {
        revisions.put(meeting.revisionId(), revision(meeting));
      }
      for (var ref : meeting.sources()) {
        refs.add(ref);
        firstMeeting.putIfAbsent(ref, meeting.meetingId());
      }
    }
    var cited = new HashMap<SourceRef, Set<UUID>>();
    for (var revision : revisions.values()) {
      var citations = new ArrayList<Citation>();
      revision.developments().forEach(d -> citations.addAll(d.evidence()));
      revision.associations().forEach(a -> citations.addAll(a.evidence()));
      for (var citation : citations) {
        var ref = new SourceRef(citation.documentId(), citation.versionId());
        if (!refs.contains(ref)) {
          throw new OwnerUnavailable(
              "PUBLICATION_MISMATCH", "Revision cites a version outside the manifest");
        }
        cited.computeIfAbsent(ref, r -> new HashSet<>()).add(citation.preparationId());
      }
    }
    var versions = new LinkedHashMap<SourceRef, Version>();
    var content = new LinkedHashMap<SourceRef, List<Unit>>();
    for (var ref : refs) {
      var version = version(ref);
      versions.put(ref, version);
      var preparation = preparation(cited.getOrDefault(ref, Set.of()), version.preparationIds());
      if (preparation != null) {
        var items = meetings.get(firstMeeting.get(ref)).itemLabels();
        content.put(ref, content(ref, preparation, items));
      }
    }
    return new Published.Collection(manifest, meetings, revisions, versions, content);
  }

  /**
   * The prepared revision whose text is indexed: the one analysis cited, otherwise the only
   * sealed one. Null when the choice is ambiguous, so the record is searchable by title only.
   */
  static UUID preparation(Set<UUID> cited, Set<UUID> sealed) {
    if (cited.size() == 1) {
      var only = cited.iterator().next();
      return sealed.contains(only) ? only : null;
    }
    if (cited.isEmpty() && sealed.size() == 1) {
      return sealed.iterator().next();
    }
    return null;
  }

  static Manifest manifest(JsonNode node) throws OwnerUnavailable {
    var coverage = new ArrayList<Range>();
    for (var range : node.path("coverage")) {
      coverage.add(new Range(date(range, "from"), date(range, "to")));
    }
    var meetings = new ArrayList<Meeting>();
    var seen = new HashSet<UUID>();
    for (var meeting : node.path("meetings")) {
      var sources = new ArrayList<SourceRef>();
      for (var source : meeting.path("sources")) {
        sources.add(new SourceRef(uuid(source, "documentId"), uuid(source, "versionId")));
      }
      var disposition = text(meeting, "disposition");
      var revision = optionalUuid(meeting, "revisionId");
      var id = uuid(meeting, "meetingId");
      if (!seen.add(id)
          || disposition == null
          || (disposition.equals("ANALYZED") != (revision != null))) {
        throw new OwnerUnavailable("INVALID_MANIFEST", "Manifest meeting entry is inconsistent");
      }
      meetings.add(
          new Meeting(
              id,
              text(meeting, "officialId"),
              date(meeting, "date"),
              Text.clean(text(meeting, "title")),
              disposition,
              revision,
              sources));
    }
    return new Manifest(uuid(node, "publicationId"), coverage, meetings);
  }

  private MeetingRecords meetingRecords(UUID id) throws OwnerUnavailable, InterruptedException {
    var node = records.get("/internal/v1/meetings/" + id);
    if (!id.equals(optionalUuid(node.path("meeting"), "id"))) {
      throw new OwnerUnavailable("PUBLICATION_MISMATCH", "Records returned another meeting");
    }
    var items = new LinkedHashMap<String, String>();
    for (var item : node.path("items")) {
      var official = text(item, "officialItemId");
      var label = text(item, "label");
      if (official != null && label != null) items.putIfAbsent(official, Text.clean(label));
    }
    return new MeetingRecords(text(node.path("meeting"), "committeeId"), items);
  }

  private Revision revision(Meeting meeting) throws OwnerUnavailable, InterruptedException {
    var node = analysis.get("/internal/v1/meeting-analyses/" + meeting.revisionId());
    if (!meeting.revisionId().equals(optionalUuid(node, "revisionId"))
        || !meeting.meetingId().equals(optionalUuid(node, "meetingId"))
        || !meeting.date().equals(date(node, "meetingDate"))) {
      throw new OwnerUnavailable(
          "PUBLICATION_MISMATCH", "Analysis revision does not match its manifest meeting");
    }
    var developments = new ArrayList<Development>();
    for (var development : node.path("developments")) {
      developments.add(
          new Development(
              uuid(development, "id"),
              Text.clean(text(development, "description")),
              citations(development.path("evidence"))));
    }
    var issues = new ArrayList<IssueMention>();
    for (var issue : node.path("issues")) {
      issues.add(new IssueMention(uuid(issue, "issueId"), Text.clean(text(issue, "name"))));
    }
    var associations = new ArrayList<Association>();
    for (var association : node.path("associations")) {
      associations.add(
          new Association(
              uuid(association, "developmentId"),
              uuid(association, "issueId"),
              text(association, "basis"),
              citations(association.path("evidence"))));
    }
    return new Revision(
        meeting.revisionId(), meeting.meetingId(), meeting.date(), developments, issues,
        associations);
  }

  private Version version(SourceRef ref) throws OwnerUnavailable, InterruptedException {
    var document = records.get("/internal/v1/documents/" + ref.documentId());
    var version = records.get("/internal/v1/document-versions/" + ref.versionId());
    boolean listed = false;
    for (var id : document.path("versionIds")) {
      listed |= id.asString().equals(ref.versionId().toString());
    }
    if (!ref.documentId().equals(optionalUuid(document, "id"))
        || !ref.documentId().equals(optionalUuid(version, "documentId"))
        || !ref.versionId().equals(optionalUuid(version, "id"))
        || !listed) {
      throw new OwnerUnavailable("PUBLICATION_MISMATCH", "Records version does not match the pin");
    }
    String family = null;
    for (var source : document.path("sourceIds")) {
      if (family == null || source.path("primary").asBoolean()) family = text(source, "family");
    }
    var labels = new LinkedHashMap<UUID, String>();
    for (var context : document.path("contexts")) {
      var meeting = optionalUuid(context, "meetingId");
      var label = text(context, "itemLabel");
      if (meeting != null && label != null) labels.putIfAbsent(meeting, Text.clean(label));
    }
    var preparations = new LinkedHashSet<UUID>();
    for (var id : version.path("preparationIds")) {
      preparations.add(UUID.fromString(id.asString()));
    }
    var title = text(version, "title");
    if (title == null) title = text(version, "originalFilename");
    return new Version(
        ref.documentId(), ref.versionId(), Text.clean(title), family, labels, preparations);
  }

  private List<Unit> content(SourceRef ref, UUID preparation, Map<String, String> items)
      throws OwnerUnavailable, InterruptedException {
    var units = new ArrayList<Unit>();
    int expected = -1;
    for (int page = 0; ; page++) {
      var node =
          records.get(
              "/internal/v1/document-versions/"
                  + ref.versionId()
                  + "/content?preparationId="
                  + preparation
                  + "&page="
                  + page
                  + "&size="
                  + CONTENT_PAGE_SIZE);
      int count = node.path("unitCount").asInt(-1);
      if (!ref.documentId().equals(optionalUuid(node, "documentId"))
          || !ref.versionId().equals(optionalUuid(node, "versionId"))
          || !preparation.equals(optionalUuid(node, "preparationId"))
          || count < 0
          || (expected != -1 && count != expected)) {
        throw new OwnerUnavailable("PUBLICATION_MISMATCH", "Prepared content does not match");
      }
      expected = count;
      for (var unit : node.path("units")) {
        var text = new StringBuilder();
        String location = null;
        for (var segment : unit.path("segments")) {
          if (!segment.path("selected").asBoolean()) continue;
          var value = text(segment, "text");
          if (value == null) continue;
          if (!text.isEmpty() && text.charAt(text.length() - 1) != '\n') text.append('\n');
          text.append(value);
          if (location == null) location = location(segment.path("locator"), items);
        }
        var cleaned = Text.clean(text.toString());
        if (cleaned != null && !cleaned.isBlank()) {
          if (cleaned.length() > MAX_UNIT_CHARACTERS) {
            cleaned = cleaned.substring(0, MAX_UNIT_CHARACTERS);
          }
          units.add(new Unit(unit.path("ordinal").asInt(), cleaned, location));
        }
      }
      if (!node.path("hasNext").asBoolean()) break;
      if (page > count) {
        throw new OwnerUnavailable("PUBLICATION_MISMATCH", "Prepared content does not end");
      }
    }
    return units;
  }

  static String location(JsonNode locator, Map<String, String> items) {
    var kind = text(locator, "kind");
    if ("pdf".equals(kind) && locator.path("page").asInt(0) > 0) {
      return "Page " + locator.path("page").asInt();
    }
    if ("html".equals(kind)) {
      var item = text(locator, "itemId");
      return item == null ? null : items.get(item);
    }
    return null;
  }

  private static List<Citation> citations(JsonNode evidence) throws OwnerUnavailable {
    var result = new ArrayList<Citation>();
    for (var span : evidence) {
      result.add(
          new Citation(
              uuid(span, "documentId"), uuid(span, "versionId"), uuid(span, "preparationId")));
    }
    return result;
  }

  static String text(JsonNode node, String field) {
    var value = node.path(field);
    return value.isString() ? value.asString() : null;
  }

  private static UUID optionalUuid(JsonNode node, String field) {
    var value = text(node, field);
    try {
      return value == null ? null : UUID.fromString(value);
    } catch (IllegalArgumentException invalid) {
      return null;
    }
  }

  private static UUID uuid(JsonNode node, String field) throws OwnerUnavailable {
    var value = optionalUuid(node, field);
    if (value == null) {
      throw new OwnerUnavailable("OWNER_INVALID_RESPONSE", "Missing identifier " + field);
    }
    return value;
  }

  private static LocalDate date(JsonNode node, String field) throws OwnerUnavailable {
    try {
      return LocalDate.parse(text(node, field));
    } catch (RuntimeException invalid) {
      throw new OwnerUnavailable("OWNER_INVALID_RESPONSE", "Missing date " + field);
    }
  }
}
