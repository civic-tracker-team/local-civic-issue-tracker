package org.civictracker.search.index;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;
import org.civictracker.search.owners.Published;
import org.civictracker.search.owners.Published.Meeting;
import org.civictracker.search.owners.Published.Revision;
import org.civictracker.search.owners.Published.SourceRef;

/** Builds the searchable rows for one collected publication. Pure: no I/O and no clock. */
public final class IndexPlan {
  public enum Kind {
    ISSUE,
    RECORD,
    MEETING
  }

  /** One row. {@code details} is serialized as the hit's kind-specific fields. */
  public record Entry(
      Kind kind,
      UUID targetId,
      UUID versionId,
      int part,
      String label,
      String context,
      String body,
      String location,
      Map<String, Object> details,
      LocalDate sortDate) {}

  public record Plan(
      UUID publicationId,
      List<Published.Range> coverage,
      List<String> recordTypes,
      int meetingCount,
      int untextedVersions,
      List<Entry> entries) {
    public Plan {
      coverage = List.copyOf(coverage);
      recordTypes = List.copyOf(recordTypes);
      entries = List.copyOf(entries);
    }
  }

  static final String STATED = "SOURCE_STATED";

  private IndexPlan() {}

  public static Plan build(Published.Collection collection) {
    var manifest = collection.manifest();
    var meetings = manifest.meetings();
    var entries = new ArrayList<Entry>();
    for (var meeting : meetings) {
      entries.add(meeting(meeting, collection));
    }
    var recordTypes = new TreeSet<String>();
    int untexted = 0;
    for (var ref : refs(meetings)) {
      var version = collection.versions().get(ref);
      var holders = meetings.stream().filter(m -> m.sources().contains(ref)).toList();
      var first = holders.getFirst();
      var type = recordType(version);
      recordTypes.add(type);
      var label = blank(version.title()) ? "Untitled record" : version.title();
      var itemLabel = version.itemLabels().get(first.meetingId());
      var context = join(itemLabel, first.title());
      var units = collection.content().get(ref);
      if (units == null) untexted++;
      var details = new LinkedHashMap<String, Object>();
      details.put("documentId", ref.documentId());
      details.put("meetingId", first.meetingId());
      details.put("meetingIds", holders.stream().map(Meeting::meetingId).toList());
      details.put("meetingDate", first.date().toString());
      details.put("meetingTitle", first.title());
      details.put("recordType", type);
      details.put("itemLabel", itemLabel);
      details.put("textSearched", units != null);
      details.put("issues", linkedIssues(ref, collection));
      entries.add(
          new Entry(
              Kind.RECORD, ref.documentId(), ref.versionId(), 0, label, context, "", null,
              details, first.date()));
      if (units != null) {
        for (var unit : units) {
          entries.add(
              new Entry(
                  Kind.RECORD,
                  ref.documentId(),
                  ref.versionId(),
                  unit.ordinal() + 1,
                  label,
                  context,
                  unit.text(),
                  unit.location(),
                  details,
                  first.date()));
        }
      }
    }
    entries.addAll(issues(collection));
    return new Plan(
        manifest.publicationId(),
        manifest.coverage(),
        List.copyOf(recordTypes),
        meetings.size(),
        untexted,
        entries);
  }

  /** Listed portal documents are typed by their template name; others are attachments. */
  static String recordType(Published.Version version) {
    if ("document-list".equals(version.family()) && !blank(version.title())) {
      return version.title().strip();
    }
    return "document-list".equals(version.family()) ? "Record" : "Attachment";
  }

  private static Entry meeting(Meeting meeting, Published.Collection collection) {
    var records = collection.meetings().get(meeting.meetingId());
    var body = new ArrayList<String>(records.itemLabels().values());
    var revision =
        meeting.revisionId() == null ? null : collection.revisions().get(meeting.revisionId());
    if (revision != null) {
      revision.developments().forEach(d -> body.add(d.description()));
    }
    var details = new LinkedHashMap<String, Object>();
    details.put("officialId", meeting.officialId());
    details.put("committeeId", records.committeeId());
    details.put("disposition", meeting.disposition());
    details.put("recordCount", meeting.sources().size());
    var label = blank(meeting.title()) ? "Meeting of " + meeting.date() : meeting.title();
    return new Entry(
        Kind.MEETING, meeting.meetingId(), null, 0, label, "", String.join("\n", nonBlank(body)),
        null, details, meeting.date());
  }

  /**
   * One row per issue named in the pinned revisions. The name is the latest mention among them,
   * never a name from a revision outside the publication.
   */
  private static List<Entry> issues(Published.Collection collection) {
    var ordered =
        collection.manifest().meetings().stream()
            .filter(m -> m.revisionId() != null)
            .sorted(Comparator.comparing(Meeting::date).thenComparing(Meeting::officialId))
            .map(m -> collection.revisions().get(m.revisionId()))
            .toList();
    var names = new LinkedHashMap<UUID, List<String>>();
    var dates = new LinkedHashMap<UUID, List<LocalDate>>();
    var meetingIds = new LinkedHashMap<UUID, Set<UUID>>();
    var descriptions = new LinkedHashMap<UUID, Set<String>>();
    var bases = new LinkedHashMap<UUID, Set<String>>();
    for (Revision revision : ordered) {
      for (var mention : revision.issues()) {
        names.computeIfAbsent(mention.issueId(), i -> new ArrayList<>()).add(mention.name());
        dates
            .computeIfAbsent(mention.issueId(), i -> new ArrayList<>())
            .add(revision.meetingDate());
        meetingIds.computeIfAbsent(mention.issueId(), i -> new LinkedHashSet<>())
            .add(revision.meetingId());
      }
      var byId = new LinkedHashMap<UUID, String>();
      revision.developments().forEach(d -> byId.put(d.id(), d.description()));
      for (var association : revision.associations()) {
        var description = byId.get(association.developmentId());
        if (description != null) {
          descriptions.computeIfAbsent(association.issueId(), i -> new LinkedHashSet<>())
              .add(description);
        }
        bases.computeIfAbsent(association.issueId(), i -> new LinkedHashSet<>())
            .add(association.basis());
      }
    }
    var entries = new ArrayList<Entry>();
    for (var issue : names.entrySet()) {
      var all = nonBlank(issue.getValue());
      var name = all.isEmpty() ? "Unnamed issue" : all.getLast();
      var earlier = new LinkedHashSet<>(all);
      earlier.remove(name);
      var issueDates = dates.get(issue.getKey());
      var linked = bases.getOrDefault(issue.getKey(), Set.of());
      var details = new LinkedHashMap<String, Object>();
      details.put("firstDate", issueDates.getFirst().toString());
      details.put("latestDate", issueDates.getLast().toString());
      details.put("meetingCount", meetingIds.get(issue.getKey()).size());
      details.put(
          "developmentCount", descriptions.getOrDefault(issue.getKey(), Set.of()).size());
      // FR-45: an issue whose every development link was inferred may be wrongly connected.
      details.put("inferredOnly", !linked.isEmpty() && !linked.contains(STATED));
      entries.add(
          new Entry(
              Kind.ISSUE,
              issue.getKey(),
              null,
              0,
              name,
              String.join("\n", earlier),
              String.join("\n", descriptions.getOrDefault(issue.getKey(), Set.of())),
              null,
              details,
              issueDates.getLast()));
    }
    return entries;
  }

  /** Issues whose association evidence cites this version, with how each link is supported. */
  private static List<Map<String, Object>> linkedIssues(
      SourceRef ref, Published.Collection collection) {
    var basis = new LinkedHashMap<UUID, String>();
    for (var meeting : collection.manifest().meetings()) {
      if (meeting.revisionId() == null) continue;
      for (var association : collection.revisions().get(meeting.revisionId()).associations()) {
        boolean cites =
            association.evidence().stream()
                .anyMatch(c -> c.documentId().equals(ref.documentId())
                    && c.versionId().equals(ref.versionId()));
        if (cites) {
          basis.merge(
              association.issueId(),
              association.basis(),
              (a, b) -> STATED.equals(a) || STATED.equals(b) ? STATED : a);
        }
      }
    }
    var result = new ArrayList<Map<String, Object>>();
    basis.forEach((id, b) -> result.add(Map.of("id", id, "inferred", !STATED.equals(b))));
    return result;
  }

  private static Set<SourceRef> refs(List<Meeting> meetings) {
    var refs = new LinkedHashSet<SourceRef>();
    meetings.forEach(m -> refs.addAll(m.sources()));
    return refs;
  }

  private static String join(String... values) {
    return String.join("\n", nonBlank(java.util.Arrays.asList(values)));
  }

  private static List<String> nonBlank(List<String> values) {
    return values.stream().filter(v -> !blank(v)).toList();
  }

  private static boolean blank(String value) {
    return value == null || value.isBlank();
  }
}
