package org.civictracker.records.application;

import jakarta.persistence.EntityManager;
import java.io.IOException;
import java.net.URI;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.civictracker.records.configuration.PortalSettings;
import org.civictracker.records.domain.AcquisitionData.Meeting;
import org.civictracker.records.domain.AcquisitionData.Observation;
import org.civictracker.records.domain.AcquisitionData.Selection;
import org.civictracker.records.persistence.DiscoveredMeeting;
import org.civictracker.records.persistence.RecordOperation;
import org.civictracker.records.portal.primegov.PortalHttp;
import org.civictracker.records.portal.primegov.PrimeGovParser;
import org.civictracker.records.storage.OriginalFileStore;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import tools.jackson.databind.ObjectMapper;

/** Freezes discovery results while retaining each official response as evidence. */
@Service
public class RecordDiscovery {
  public record Found(Meeting meeting, Instant observedAt) {}

  private final RecordsOperations operations;
  private final RecordsFoundation foundation;
  private final PortalHttp portal;
  private final PrimeGovParser parser;
  private final PortalSettings.Sources settings;
  private final EntityManager entities;
  private final JdbcTemplate jdbc;
  private final OriginalFileStore files;
  private final ObjectMapper json;

  public RecordDiscovery(
      RecordsOperations operations,
      RecordsFoundation foundation,
      PortalHttp portal,
      PrimeGovParser parser,
      PortalSettings.Sources settings,
      EntityManager entities,
      JdbcTemplate jdbc,
      OriginalFileStore files,
      ObjectMapper json) {
    this.operations = operations;
    this.foundation = foundation;
    this.portal = portal;
    this.parser = parser;
    this.settings = settings;
    this.entities = entities;
    this.jdbc = jdbc;
    this.files = files;
    this.json = json;
  }

  public void execute(RecordOperation operation) throws IOException, InterruptedException {
    Selection selection = json.readValue(operation.inputJson, Selection.class);
    List<Found> found = load(operation, selection);
    operations.transaction(() -> {
      operations.guard(operation.id, operation.attemptId);
      entities
          .createQuery("delete from DiscoveredMeeting where operationId=:o")
          .setParameter("o", operation.id)
          .executeUpdate();
      List<DiscoveredMeeting> rows = new ArrayList<>();
      for (Found item : found) {
        DiscoveredMeeting row = new DiscoveredMeeting();
        row.operationId = operation.id;
        row.meetingId =
            foundation.observeMeeting(
                new Observation(selection.sourceId(), item.meeting(), item.observedAt()));
        row.payload = json.writeValueAsString(item.meeting());
        row.observedAt = item.observedAt();
        rows.add(row);
      }
      rows.sort(
          Comparator.comparing(
                  (DiscoveredMeeting r) -> json.readValue(r.payload, Meeting.class).localDate())
              .thenComparing(r -> r.meetingId));
      for (int i = 0; i < rows.size(); i++) {
        rows.get(i).ordinal = i;
        entities.persist(rows.get(i));
      }
      return null;
    });
  }

  public List<Found> load(RecordOperation operation, Selection selection)
      throws IOException, InterruptedException {
    if (!settings.required(selection.sourceId()).equals(selection.profile())) {
      throw new OperationProblem(409, "SOURCE_CONFIGURATION_CHANGED");
    }
    List<URI> urls = new ArrayList<>();
    for (int year = selection.from().getYear(); year <= selection.to().getYear(); year++) {
      urls.add(
          selection
              .profile()
              .baseUrl()
              .resolve("/api/v2/PublicPortal/ListArchivedMeetings?year=" + year));
    }
    urls.add(selection.profile().baseUrl().resolve("/api/v2/PublicPortal/ListUpcomingMeetings"));
    Map<String, Found> selected = new LinkedHashMap<>();
    for (URI url : urls) {
      try (var fetched = portal.fetch(selection.profile(), url)) {
        String key = capture(operation, fetched);
        List<Meeting> meetings;
        try (var input = files.open(key)) {
          meetings = parser.listing(input, selection);
        }
        for (Meeting meeting : meetings) {
          Found prior =
              selected.putIfAbsent(meeting.officialId(), new Found(meeting, fetched.observedAt()));
          if (prior != null && !prior.meeting().equals(meeting)) {
            throw new OperationProblem(409, "CONFLICTING_SOURCE_MEETING");
          }
        }
      }
    }
    return List.copyOf(selected.values());
  }

  public String capture(RecordOperation operation, PortalHttp.Fetched fetched) throws IOException {
    UUID id = UUID.randomUUID();
    try {
      return operations.transaction(() -> {
        operations.guard(operation.id, operation.attemptId);
        String key;
        try {
          key = files.installCapture(fetched.staged(), id);
        } catch (IOException error) {
          throw new java.io.UncheckedIOException(error);
        }
        jdbc.update(
            "insert into records.operation_capture"
                + " (id,operation_id,source_url,resolved_url,observed_at,"
                + "sha256,byte_length,storage_key)"
                + " values (?,?,?,?,?,?,?,?)",
            id,
            operation.id,
            fetched.source().toString(),
            fetched.resolved().toString(),
            java.sql.Timestamp.from(fetched.observedAt()),
            fetched.staged().sha256(),
            fetched.staged().byteLength(),
            key);
        return key;
      });
    } catch (java.io.UncheckedIOException error) {
      throw error.getCause();
    }
  }
}
