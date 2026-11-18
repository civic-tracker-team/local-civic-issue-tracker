package org.civictracker.analysis.records;

import java.io.IOException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.UUID;
import org.civictracker.analysis.domain.MeetingInput;
import org.civictracker.analysis.evaluation.EvaluationJson;
import org.civictracker.analysis.evaluation.JsonHttp;
import tools.jackson.databind.JsonNode;

/** Reads complete owner receipts before accepting the pinned content selection. */
public final class PreparedMeetingReader {
  public record Pin(
      UUID acquisitionItemId,
      UUID documentId,
      UUID versionId,
      UUID artifactId,
      String sha256,
      long byteLength,
      UUID preparationId,
      String profileFingerprint) {}

  public record Selection(
      UUID discoveryId,
      UUID acquisitionId,
      UUID preparationOperationId,
      UUID meetingId,
      String officialId,
      LocalDate meetingDate,
      String title,
      List<Pin> inputs,
      List<MeetingInput.PriorIssue> priorIssues) {
    public Selection {
      inputs = List.copyOf(inputs);
      priorIssues = List.copyOf(priorIssues);
    }
  }

  private final JsonHttp http;
  private final int pageSize;

  public PreparedMeetingReader(JsonHttp http, int pageSize) {
    if (pageSize < 1) throw new IllegalArgumentException("Positive page size required");
    this.http = http;
    this.pageSize = pageSize;
  }

  public MeetingInput readPinned(Selection selection) throws IOException, InterruptedException {
    successful(selection.discoveryId(), "DISCOVERY");
    successful(selection.acquisitionId(), "ACQUISITION");
    successful(selection.preparationOperationId(), "PREPARATION");
    var meetings = pages("/operations/" + selection.discoveryId() + "/meetings");
    var meeting =
        meetings.stream()
            .filter(m -> m.path("id").asText().equals(selection.meetingId().toString()))
            .findFirst()
            .orElseThrow(() -> new IOException("Meeting absent from frozen discovery"));
    var source = meeting.path("source");
    require(
        source.path("officialId").asText().equals(selection.officialId())
            && source.path("localDate").asText().equals(selection.meetingDate().toString())
            && source.path("title").asText().equals(selection.title()),
        "Meeting metadata mismatch");
    for (var prior : selection.priorIssues()) {
      require(
          prior.id() != null
              && !prior.id().isBlank()
              && prior.description() != null
              && prior.reviewedThrough() != null
              && prior.reviewedThrough().isBefore(selection.meetingDate())
              && prior.snapshotSha256() != null
              && prior.snapshotSha256().matches("[a-f0-9]{64}"),
          "Prior issue context must precede this meeting and identify a reviewed snapshot");
    }
    var originals = pages("/operations/" + selection.acquisitionId() + "/originals");
    var preparations = pages("/operations/" + selection.preparationOperationId() + "/preparations");
    require(
        !selection.inputs().isEmpty()
            && originals.size() == selection.inputs().size()
            && preparations.size() == selection.inputs().size(),
        "Incomplete selected receipt");
    var seen = new HashSet<UUID>();
    var segmentIds = new HashSet<UUID>();
    var results = new ArrayList<MeetingInput.Source>();
    for (var pin : selection.inputs()) {
      require(
          pin.acquisitionItemId() != null && seen.add(pin.acquisitionItemId()),
          "Duplicate input pin");
      var original =
          originals.stream()
              .filter(n -> n.path("id").asText().equals(pin.acquisitionItemId().toString()))
              .findFirst()
              .orElseThrow(() -> new IOException("Missing acquisition item"));
      var retained = original.path("retained");
      var prepared =
          preparations.stream()
              .filter(n ->
                  n.path("acquisitionItemId").asText().equals(pin.acquisitionItemId().toString()))
              .findFirst()
              .orElseThrow(() -> new IOException("Missing preparation item"));
      require(
          matches(retained, "documentId", pin.documentId())
              && matches(retained, "versionId", pin.versionId())
              && matches(retained, "artifactId", pin.artifactId())
              && retained.path("sha256").asText().equals(pin.sha256())
              && retained.path("byteLength").asLong(-1) == pin.byteLength()
              && pin.sha256().matches("[a-f0-9]{64}")
              && matches(prepared, "documentId", pin.documentId())
              && matches(prepared, "versionId", pin.versionId())
              && matches(prepared, "preparationId", pin.preparationId())
              && prepared.path("profileFingerprint").asText().equals(pin.profileFingerprint()),
          "Original/preparation pin mismatch");
      var version = get("/document-versions/" + pin.versionId());
      require(
          matches(version, "documentId", pin.documentId())
              && matches(version, "id", pin.versionId())
              && matches(version, "artifactId", pin.artifactId())
              && version.path("sha256").asText().equals(pin.sha256())
              && version.path("byteLength").asLong(-1) == pin.byteLength(),
          "Version identity mismatch");
      var units = new ArrayList<MeetingInput.Unit>();
      int count = -1;
      for (int page = 0; ; page++) {
        var response =
            get(
                "/document-versions/"
                    + pin.versionId()
                    + "/content?preparationId="
                    + pin.preparationId()
                    + "&page="
                    + page
                    + "&size="
                    + pageSize);
        require(
            matches(response, "documentId", pin.documentId())
                && matches(response, "versionId", pin.versionId())
                && matches(response, "preparationId", pin.preparationId())
                && response.path("profileFingerprint").asText().equals(pin.profileFingerprint()),
            "Content pin mismatch");
        int currentCount = response.path("unitCount").asInt(-1);
        require(currentCount >= 0 && (count == -1 || currentCount == count), "Changed unit count");
        count = currentCount;
        verifyPage(response, page, "units");
        for (var item : response.path("units")) {
          var unit = EvaluationJson.MAPPER.treeToValue(item, MeetingInput.Unit.class);
          require(unit.ordinal() == units.size(), "Missing or reordered content unit");
          for (var segment : unit.segments()) {
            require(
                segment.id() != null
                    && segmentIds.add(segment.id())
                    && segment.text() != null
                    && segment.method() != null
                    && !segment.locator().isEmpty(),
                "Invalid or duplicate segment");
          }
          require(
              !unit.segments().isEmpty()
                  || unit.disposition().equals("EMPTY")
                  || unit.disposition().equals("NON_TEXT"),
              "Text unit has no segments");
          units.add(unit);
        }
        require(units.size() <= count, "Too many content units");
        if (!response.path("hasNext").asBoolean()) break;
        require(units.size() < count, "Pagination exceeds expected coverage");
      }
      require(units.size() == count, "Incomplete content coverage");
      var urls = new ArrayList<String>();
      version.path("sourceUrls").forEach(u -> urls.add(u.asText()));
      results.add(
          new MeetingInput.Source(
              pin.documentId(),
              pin.versionId(),
              pin.artifactId(),
              pin.sha256(),
              pin.byteLength(),
              pin.preparationId(),
              pin.profileFingerprint(),
              urls,
              units));
    }
    return new MeetingInput(
        selection.meetingId(),
        selection.officialId(),
        selection.meetingDate(),
        selection.title(),
        results,
        selection.priorIssues());
  }

  private void successful(UUID id, String kind) throws IOException, InterruptedException {
    var value = get("/operations/" + id);
    require(
        matches(value, "id", id)
            && value.path("kind").asText().equals(kind)
            && value.path("state").asText().equals("SUCCEEDED"),
        "Operation is not a successful " + kind);
  }

  private List<JsonNode> pages(String path) throws IOException, InterruptedException {
    var result = new ArrayList<JsonNode>();
    for (int page = 0; ; page++) {
      var response = get(path + "?page=" + page + "&size=" + pageSize);
      verifyPage(response, page, "items");
      response.path("items").forEach(result::add);
      if (!response.path("hasNext").asBoolean()) return result;
    }
  }

  private void verifyPage(JsonNode value, int page, String field) throws IOException {
    require(
        value.path("page").asInt(-1) == page
            && value.path("size").asInt(-1) == pageSize
            && value.path("hasNext").isBoolean()
            && value.path(field).isArray()
            && value.path(field).size() <= pageSize
            && (!value.path("hasNext").asBoolean() || value.path(field).size() == pageSize),
        "Invalid page metadata");
  }

  private boolean matches(JsonNode node, String field, UUID id) {
    return id != null && node.path(field).asText().equals(id.toString());
  }

  private JsonNode get(String path) throws IOException, InterruptedException {
    return http.get("/internal/v1" + path);
  }

  private void require(boolean condition, String message) throws IOException {
    if (!condition) throw new IOException(message);
  }
}
