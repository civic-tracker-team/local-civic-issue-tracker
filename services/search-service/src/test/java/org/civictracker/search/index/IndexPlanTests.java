package org.civictracker.search.index;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.civictracker.search.owners.Published;
import org.civictracker.search.owners.Published.Citation;
import org.junit.jupiter.api.Test;

class IndexPlanTests {
  static final UUID ISSUE = UUID.randomUUID();
  static final UUID DOC = UUID.randomUUID();
  static final UUID VERSION = UUID.randomUUID();
  static final UUID PREP = UUID.randomUUID();

  @Test
  void issueNameIsTheLatestPinnedMentionEvenWhenManifestOrderDiffers() {
    var early = meeting("2026-10-07", "b");
    var late = meeting("2026-10-21", "a");
    var plan =
        IndexPlan.build(
            collection(
                List.of(late, early),
                Map.of(
                    early.revisionId(), revision(early, "Old Name", "SOURCE_STATED"),
                    late.revisionId(), revision(late, "New Name", "INFERRED"))));
    var issue = only(plan, IndexPlan.Kind.ISSUE);
    assertEquals("New Name", issue.label());
    assertEquals("Old Name", issue.context());
    assertEquals("2026-10-07", issue.details().get("firstDate"));
    assertEquals("2026-10-21", issue.details().get("latestDate"));
    assertEquals(2, issue.details().get("meetingCount"));
    assertEquals(false, issue.details().get("inferredOnly"));
  }

  @Test
  void aRecordWithoutTextStillHasASearchableTitleRow() {
    var analyzed = meeting("2026-10-07", "a");
    var meeting =
        new Published.Meeting(
            analyzed.meetingId(), "a", analyzed.date(), "Council", "SKIPPED", null,
            analyzed.sources());
    var plan = IndexPlan.build(collection(List.of(meeting), Map.of(), false));
    var rows = plan.entries().stream().filter(e -> e.kind() == IndexPlan.Kind.RECORD).toList();
    assertEquals(1, rows.size());
    assertEquals(0, rows.getFirst().part());
    assertEquals("Staff Report", rows.getFirst().label());
    assertEquals(false, rows.getFirst().details().get("textSearched"));
    assertEquals(1, plan.untextedVersions());
    assertTrue(((List<?>) rows.getFirst().details().get("issues")).isEmpty());
  }

  @Test
  void sourceTextCannotFakeSnippetMatchMarkers() {
    var analyzed = meeting("2026-10-07", "a");
    var meeting =
        new Published.Meeting(
            analyzed.meetingId(), "a", analyzed.date(), "Council \u0001beta\u0002", "SKIPPED",
            null, analyzed.sources());
    var items = Map.of("1", "Alpha \u0001beta\u0002 gamma");
    var plan = IndexPlan.build(collection(List.of(meeting), Map.of(), false, items));
    for (var entry : plan.entries()) {
      for (var text : new String[] {entry.label(), entry.context(), entry.body(), entry.location()}) {
        assertFalse(text != null && (text.contains("\u0001") || text.contains("\u0002")), text);
      }
    }
    assertEquals("Council beta", only(plan, IndexPlan.Kind.MEETING).label());
  }

  @Test
  void meetingRowsKeepAgendaOrder() {
    var analyzed = meeting("2026-10-07", "a");
    var meeting =
        new Published.Meeting(
            analyzed.meetingId(), "a", analyzed.date(), "Council", "SKIPPED", null,
            analyzed.sources());
    var items = new java.util.LinkedHashMap<String, String>();
    for (int i = 1; i <= 12; i++) items.put("item-" + i, "Label " + i);
    var plan = IndexPlan.build(collection(List.of(meeting), Map.of(), false, items));
    assertEquals(String.join("\n", items.values()), only(plan, IndexPlan.Kind.MEETING).body());
  }

  @Test
  void aCollectionMissingAPinnedRevisionIsRejected() {
    var meeting = meeting("2026-10-07", "a");
    org.junit.jupiter.api.Assertions.assertThrows(
        IllegalArgumentException.class, () -> collection(List.of(meeting), Map.of()));
  }

  @Test
  void recordTypesComeFromListedTemplateNamesOrAttachments() {
    assertEquals("Minutes", IndexPlan.recordType(version("document-list", " Minutes ")));
    assertEquals("Record", IndexPlan.recordType(version("document-list", null)));
    assertEquals("Attachment", IndexPlan.recordType(version("attachment-history", "Staff Report")));
    assertEquals("Attachment", IndexPlan.recordType(version(null, "Staff Report")));
  }

  @Test
  void anIssueLinkedOnlyByInferenceIsMarked() {
    var meeting = meeting("2026-10-07", "a");
    var plan =
        IndexPlan.build(
            collection(
                List.of(meeting), Map.of(meeting.revisionId(), revision(meeting, "N", "INFERRED"))));
    assertEquals(true, only(plan, IndexPlan.Kind.ISSUE).details().get("inferredOnly"));
    var record =
        plan.entries().stream().filter(e -> e.kind() == IndexPlan.Kind.RECORD).findFirst().orElseThrow();
    assertEquals(List.of(Map.of("id", ISSUE, "inferred", true)), record.details().get("issues"));
    assertFalse(plan.entries().stream().anyMatch(e -> e.body().contains("\u0001")));
  }

  private static IndexPlan.Entry only(IndexPlan.Plan plan, IndexPlan.Kind kind) {
    var rows = plan.entries().stream().filter(e -> e.kind() == kind).toList();
    assertEquals(1, rows.size());
    return rows.getFirst();
  }

  private static Published.Meeting meeting(String date, String official) {
    return new Published.Meeting(
        UUID.randomUUID(), official, LocalDate.parse(date), "Council", "ANALYZED",
        UUID.randomUUID(), List.of(new Published.SourceRef(DOC, VERSION)));
  }

  private static Published.Revision revision(Published.Meeting meeting, String name, String basis) {
    var development = UUID.randomUUID();
    var citation = new Citation(DOC, VERSION, PREP);
    return new Published.Revision(
        meeting.revisionId(), meeting.meetingId(), meeting.date(),
        List.of(new Published.Development(development, "Council discussed it.", List.of(citation))),
        List.of(new Published.IssueMention(ISSUE, name)),
        List.of(new Published.Association(development, ISSUE, basis, List.of(citation))));
  }

  private static Published.Version version(String family, String title) {
    return new Published.Version(DOC, VERSION, title, family, Map.of(), Set.of(PREP));
  }

  private static Published.Collection collection(
      List<Published.Meeting> meetings, Map<UUID, Published.Revision> revisions) {
    return collection(meetings, revisions, true);
  }

  private static Published.Collection collection(
      List<Published.Meeting> meetings, Map<UUID, Published.Revision> revisions, boolean text) {
    return collection(meetings, revisions, text, Map.of());
  }

  private static Published.Collection collection(
      List<Published.Meeting> meetings,
      Map<UUID, Published.Revision> revisions,
      boolean text,
      Map<String, String> items) {
    var ref = new Published.SourceRef(DOC, VERSION);
    var records = new java.util.HashMap<UUID, Published.MeetingRecords>();
    meetings.forEach(m -> records.put(m.meetingId(), new Published.MeetingRecords("7", items)));
    return new Published.Collection(
        new Published.Manifest(UUID.randomUUID(), List.of(), meetings),
        records,
        revisions,
        Map.of(ref, new Published.Version(DOC, VERSION, "Staff Report", "attachment-history", Map.of(), Set.of(PREP))),
        text ? Map.of(ref, List.of(new Published.Unit(0, "Page text", "Page 1"))) : Map.of());
  }
}
