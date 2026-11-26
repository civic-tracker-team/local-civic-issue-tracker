package org.civictracker.search;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * A synthetic one-month collection served through {@link OwnerStub}. Every value is invented.
 *
 * <p>Two council meetings are analyzed and a planning meeting is cancelled. One parking issue is
 * renamed between meetings; a library report has no issue link; a sidewalk issue is linked only
 * by inference. The owners also hold data outside the manifest: an abandoned revision of the
 * first meeting, and a newer staff-report version, each with words found nowhere else.
 */
final class SyntheticPublication {
  static UUID id(String name) {
    return UUID.nameUUIDFromBytes(name.getBytes(StandardCharsets.UTF_8));
  }

  static final UUID P1 = id("publication-1");
  static final UUID P2 = id("publication-2");
  static final UUID M1 = id("meeting-1");
  static final UUID M2 = id("meeting-2");
  static final UUID M3 = id("meeting-3");
  static final UUID M4 = id("meeting-4");
  static final UUID R1 = id("revision-1");
  static final UUID R2 = id("revision-2");
  static final UUID R4 = id("revision-4");
  static final UUID R_ABANDONED = id("revision-abandoned");
  static final UUID AGENDA = id("doc-agenda");
  static final UUID STAFF_REPORT = id("doc-staff-report");
  static final UUID LIBRARY = id("doc-library");
  static final UUID MINUTES = id("doc-minutes");
  static final UUID BUDGET = id("doc-budget");
  static final UUID V_AGENDA = id("v-agenda");
  static final UUID V_STAFF = id("v-staff");
  static final UUID V_STAFF_NEWER = id("v-staff-newer");
  static final UUID V_LIBRARY = id("v-library");
  static final UUID V_MINUTES = id("v-minutes");
  static final UUID V_BUDGET = id("v-budget");
  static final UUID PARKING = id("issue-parking");
  static final UUID SIDEWALK = id("issue-sidewalk");
  static final UUID SKATE = id("issue-skate");
  static final UUID BUDGET_ISSUE = id("issue-budget");

  private final OwnerStub stub;

  SyntheticPublication(OwnerStub stub) {
    this.stub = stub;
  }

  /** Serves the owner data behind publication 1 and makes it current. */
  void publishFirst() {
    meetings();
    versions();
    revisions();
    stub.put("/internal/v1/publications/current", manifest(P1, firstMeetings(), "2026-10-31"));
  }

  /** Adds a November meeting and makes publication 2 current. */
  void publishSecond() {
    stub.put(
        "/internal/v1/meetings/" + M4,
        meeting(M4, "Budget Workshop", List.of(Map.of("officialItemId", "401", "label", "Budget"))));
    document(BUDGET, V_BUDGET, "attachment-history", "Midyear Budget Review", M4, "Budget", "P-budget");
    content(V_BUDGET, BUDGET, "P-budget", List.of(pdf(0, 1, "Midyear budget review shows a surplus.")));
    var developments = List.of(development("d-budget", "Council reviewed the midyear budget surplus.", BUDGET, V_BUDGET, "P-budget"));
    stub.put(
        "/internal/v1/meeting-analyses/" + R4,
        revision(R4, M4, "2026-11-04", developments,
            List.of(Map.of("issueId", BUDGET_ISSUE.toString(), "name", "Midyear Budget", "continued", false)),
            List.of(association("d-budget", BUDGET_ISSUE, "SOURCE_STATED", BUDGET, V_BUDGET, "P-budget"))));
    var meetings = new ArrayList<>(firstMeetings());
    meetings.add(
        manifestMeeting(M4, "2026-11-04", "Budget Workshop", "ANALYZED", R4, List.of(ref(BUDGET, V_BUDGET))));
    stub.put("/internal/v1/publications/current", manifest(P2, meetings, "2026-11-30"));
  }

  private List<Map<String, Object>> firstMeetings() {
    return List.of(
        manifestMeeting(M1, "2026-10-07", "City Council Regular Meeting", "ANALYZED", R1,
            List.of(ref(AGENDA, V_AGENDA), ref(STAFF_REPORT, V_STAFF), ref(LIBRARY, V_LIBRARY))),
        manifestMeeting(M2, "2026-10-21", "City Council Regular Meeting", "ANALYZED", R2,
            List.of(ref(MINUTES, V_MINUTES))),
        manifestMeeting(M3, "2026-10-28", "Planning Commission - Cancelled", "CANCELLED", null,
            List.of()));
  }

  private void meetings() {
    stub.put("/internal/v1/meetings/" + M1, meeting(M1, "City Council Regular Meeting", List.of(
        Map.of("officialItemId", "101", "label", "Third Street Parking Structure"),
        Map.of("officialItemId", "102", "label", "Library Hours Report"))));
    stub.put("/internal/v1/meetings/" + M2, meeting(M2, "City Council Regular Meeting", List.of(
        Map.of("officialItemId", "201", "label", "Parking Structure Construction Contract"))));
    stub.put("/internal/v1/meetings/" + M3, meeting(M3, "Planning Commission - Cancelled", List.of()));
  }

  private void versions() {
    document(AGENDA, V_AGENDA, "document-list", "Agenda", M1, null, "P-agenda");
    document(STAFF_REPORT, V_STAFF, "attachment-history", "Parking Structure Staff Report", M1,
        "Third Street Parking Structure", "P-staff");
    document(LIBRARY, V_LIBRARY, "attachment-history", "Library Hours Report", M1,
        "Library Hours Report", "P-library");
    document(MINUTES, V_MINUTES, "document-list", "Minutes", M2, null, "P-minutes");
    // The staff report's newer bytes exist in Records but are not pinned by any publication.
    stub.put("/internal/v1/document-versions/" + V_STAFF_NEWER, Map.of(
        "id", V_STAFF_NEWER.toString(), "documentId", STAFF_REPORT.toString(),
        "title", "Parking Structure Staff Report", "preparationIds", List.of(id("P-newer").toString())));
    content(V_STAFF_NEWER, STAFF_REPORT, "P-newer", List.of(pdf(0, 1, "Zeppelin mooring mast added.")));
    content(V_AGENDA, AGENDA, "P-agenda", List.of(html(0, "101", "Item 1. Third Street parking structure design.\n"), html(1, "102", "Item 2. Library hours report.")));
    content(V_STAFF, STAFF_REPORT, "P-staff", List.of(
        pdf(0, 1, "Downtown parking structure staff report.\n"),
        pdf(1, 2, "Construction cost estimate is twelve million dollars.\n"),
        pdf(2, 3, "")));
    content(V_LIBRARY, LIBRARY, "P-library", List.of(pdf(0, 1, "Library hours will extend on weekends. <b>Read</b> & enjoy.")));
    content(V_MINUTES, MINUTES, "P-minutes", List.of(pdf(0, 1,
        "The council approved the construction contract for the parking structure. Sidewalk repairs near the structure were discussed.")));
  }

  private void revisions() {
    stub.put("/internal/v1/meeting-analyses/" + R1, revision(R1, M1, "2026-10-07",
        List.of(
            development("d1", "Council reviewed a proposed five-level parking structure on Third Street.", STAFF_REPORT, V_STAFF, "P-staff"),
            development("d2", "Council received a report on weekend library hours.", LIBRARY, V_LIBRARY, "P-library")),
        List.of(Map.of("issueId", PARKING.toString(), "name", "Downtown Parking Structure", "continued", false)),
        List.of(association("d1", PARKING, "SOURCE_STATED", STAFF_REPORT, V_STAFF, "P-staff"))));
    stub.put("/internal/v1/meeting-analyses/" + R2, revision(R2, M2, "2026-10-21",
        List.of(
            development("d3", "Council approved the parking structure construction contract.", MINUTES, V_MINUTES, "P-minutes"),
            development("d4", "Council discussed sidewalk repairs near the structure.", MINUTES, V_MINUTES, "P-minutes")),
        List.of(
            Map.of("issueId", PARKING.toString(), "name", "Third Street Parking Structure", "continued", true),
            Map.of("issueId", SIDEWALK.toString(), "name", "Sidewalk Repair Program", "continued", false)),
        List.of(
            association("d3", PARKING, "SOURCE_STATED", MINUTES, V_MINUTES, "P-minutes"),
            association("d4", SIDEWALK, "INFERRED", MINUTES, V_MINUTES, "P-minutes"))));
    // An abandoned attempt at the first meeting: saved by analysis, never published.
    stub.put("/internal/v1/meeting-analyses/" + R_ABANDONED, revision(R_ABANDONED, M1, "2026-10-07",
        List.of(development("d-skate", "Council considered skate park lighting.", STAFF_REPORT, V_STAFF, "P-staff")),
        List.of(Map.of("issueId", SKATE.toString(), "name", "Skate Park Lighting", "continued", false)),
        List.of(association("d-skate", SKATE, "SOURCE_STATED", STAFF_REPORT, V_STAFF, "P-staff"))));
  }

  /** Makes the first meeting's revision cite the unpinned staff-report version. */
  void citeUnpinnedVersion() {
    stub.put("/internal/v1/meeting-analyses/" + R1, revision(R1, M1, "2026-10-07",
        List.of(development("d1", "Council reviewed a parking structure.", STAFF_REPORT, V_STAFF_NEWER, "P-newer")),
        List.of(Map.of("issueId", PARKING.toString(), "name", "Downtown Parking Structure", "continued", false)),
        List.of()));
  }

  void restoreFirstRevision() {
    revisions();
  }

  static String contentPath(UUID version, String preparation, int page) {
    return "/internal/v1/document-versions/" + version + "/content?preparationId=" + id(preparation)
        + "&page=" + page + "&size=20";
  }

  private void document(UUID document, UUID version, String family, String title, UUID meeting,
      String itemLabel, String preparation) {
    var context = new LinkedHashMap<String, Object>();
    context.put("meetingId", meeting.toString());
    context.put("itemLabel", itemLabel);
    stub.put("/internal/v1/documents/" + document, Map.of(
        "id", document.toString(), "currentVersionId", version.toString(),
        "sourceIds", List.of(Map.of("meetingId", meeting.toString(), "family", family, "value", "x", "primary", true)),
        "contexts", List.of(context),
        "versionIds", document.equals(STAFF_REPORT)
            ? List.of(version.toString(), V_STAFF_NEWER.toString()) : List.of(version.toString())));
    stub.put("/internal/v1/document-versions/" + version, Map.of(
        "id", version.toString(), "documentId", document.toString(), "title", title,
        "originalFilename", "ignored.pdf", "preparationIds", List.of(id(preparation).toString())));
  }

  private void content(UUID version, UUID document, String preparation, List<Map<String, Object>> units) {
    stub.put(contentPath(version, preparation, 0), Map.of(
        "documentId", document.toString(), "versionId", version.toString(),
        "preparationId", id(preparation).toString(), "unitCount", units.size(),
        "units", units, "page", 0, "size", 20, "hasNext", false));
  }

  private static Map<String, Object> pdf(int ordinal, int page, String text) {
    var segments = text.isEmpty() ? List.of() : List.of(
        Map.of("text", text, "selected", true, "locator", Map.of("kind", "pdf", "page", page)),
        Map.of("text", "unselected native alternative zebra", "selected", false,
            "locator", Map.of("kind", "pdf", "page", page)));
    return Map.of("ordinal", ordinal, "disposition", text.isEmpty() ? "EMPTY" : "TEXT", "segments", segments);
  }

  private static Map<String, Object> html(int ordinal, String item, String text) {
    return Map.of("ordinal", ordinal, "disposition", "TEXT", "segments", List.of(
        Map.of("text", text, "selected", true, "locator", Map.of("kind", "html", "itemId", item))));
  }

  private static Map<String, Object> meeting(UUID id, String title, List<Map<String, String>> items) {
    return Map.of("meeting", Map.of("id", id.toString(), "title", title, "committeeId", "7"),
        "items", items, "documents", List.of());
  }

  private static Map<String, Object> manifest(UUID id, List<Map<String, Object>> meetings, String to) {
    return Map.of("publicationId", id.toString(), "runId", id("run-" + id).toString(),
        "coverage", List.of(Map.of("from", "2026-10-01", "to", to)), "meetings", meetings);
  }

  private static Map<String, Object> manifestMeeting(UUID id, String date, String title,
      String disposition, UUID revision, List<Map<String, String>> sources) {
    var meeting = new LinkedHashMap<String, Object>();
    meeting.put("meetingId", id.toString());
    meeting.put("officialId", "official-" + date);
    meeting.put("date", date);
    meeting.put("title", title);
    meeting.put("disposition", disposition);
    meeting.put("revisionId", revision == null ? null : revision.toString());
    meeting.put("sources", sources);
    return meeting;
  }

  private static Map<String, String> ref(UUID document, UUID version) {
    return Map.of("documentId", document.toString(), "versionId", version.toString());
  }

  private static Map<String, Object> revision(UUID id, UUID meeting, String date,
      List<Map<String, Object>> developments, List<Map<String, Object>> issues,
      List<Map<String, Object>> associations) {
    return Map.of("revisionId", id.toString(), "meetingId", meeting.toString(), "meetingDate", date,
        "developments", developments, "issues", issues, "associations", associations);
  }

  private static Map<String, Object> development(String key, String description, UUID document,
      UUID version, String preparation) {
    return Map.of("id", id(key).toString(), "description", description,
        "evidence", List.of(span(document, version, preparation)));
  }

  private static Map<String, Object> association(String development, UUID issue, String basis,
      UUID document, UUID version, String preparation) {
    return Map.of("developmentId", id(development).toString(), "issueId", issue.toString(),
        "basis", basis, "evidence", List.of(span(document, version, preparation)));
  }

  private static Map<String, String> span(UUID document, UUID version, String preparation) {
    return Map.of("documentId", document.toString(), "versionId", version.toString(),
        "preparationId", id(preparation).toString());
  }
}
