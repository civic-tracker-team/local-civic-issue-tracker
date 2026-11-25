package org.civictracker.coordinator.owners;

import static org.civictracker.coordinator.owners.OwnerHttp.*;

import java.net.URI;
import java.time.Duration;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.civictracker.coordinator.run.StartRun;

/** Calls records through its operation API and retained result pages. */
public final class RecordsClient {
  private final OwnerHttp http;

  public RecordsClient(URI base, String username, String password, Duration timeout) {
    http = new OwnerHttp(base, username, password, timeout);
  }

  public Optional<OwnerStatus> operation(UUID id) throws OwnerRejected, OwnerUnreachable {
    String path = "/internal/v1/operations/" + id;
    return http.get(
        path,
        true,
        node -> {
          if (!id.equals(id(node, "id"))) {
            throw new IllegalArgumentException();
          }
          return new OwnerStatus(
              id,
              OwnerState.valueOf(text(node, "state")),
              optionalText(node, "errorCode"),
              optionalId(node, "blockedExecutionId"),
              null,
              http.url(path));
        });
  }

  public void discover(UUID opId, StartRun run, UUID replaces)
      throws OwnerRejected, OwnerUnreachable {
    http.post(
        "/internal/v1/discovery-operations",
        new Discovery(opId, run.sourceId(), run.from(), run.to(), run.committeeIds(), replaces));
  }

  public void acquire(UUID opId, UUID discoveryId, UUID meetingId, UUID runId, UUID replaces)
      throws OwnerRejected, OwnerUnreachable {
    http.post(
        "/internal/v1/acquisition-operations",
        new Acquisition(opId, discoveryId, meetingId, runId, replaces));
  }

  public void prepare(UUID opId, UUID acquisitionId, UUID runId, String profileId, UUID replaces)
      throws OwnerRejected, OwnerUnreachable {
    http.post(
        "/internal/v1/preparation-operations",
        new Preparation(opId, acquisitionId, runId, profileId, false, replaces));
  }

  public List<DiscoveredMeeting> meetings(UUID discoveryId) throws OwnerRejected, OwnerUnreachable {
    return http.pages(
        "/internal/v1/operations/" + discoveryId + "/meetings",
        node -> {
          var source = node.get("source");
          return new DiscoveredMeeting(
              id(node, "id"),
              text(source, "officialId"),
              date(source, "localDate"),
              text(source, "title"),
              optionalText(source, "cancellationEvidence"));
        });
  }

  public List<Original> originals(UUID acquisitionId) throws OwnerRejected, OwnerUnreachable {
    return http.pages(
        "/internal/v1/operations/" + acquisitionId + "/originals",
        node -> {
          var retained = node.get("retained");
          return new Original(
              id(node, "id"),
              id(retained, "documentId"),
              id(retained, "versionId"),
              id(retained, "artifactId"),
              text(retained, "sha256"),
              number(retained, "byteLength"));
        });
  }

  public List<Prepared> preparations(UUID preparationId) throws OwnerRejected, OwnerUnreachable {
    return http.pages(
        "/internal/v1/operations/" + preparationId + "/preparations",
        node ->
            new Prepared(
                id(node, "acquisitionItemId"),
                id(node, "documentId"),
                id(node, "versionId"),
                id(node, "preparationId"),
                text(node, "profileFingerprint")));
  }

  private record Discovery(
      UUID operationId,
      String sourceId,
      LocalDate from,
      LocalDate to,
      List<String> committeeIds,
      UUID replacesOperationId) {}

  private record Acquisition(
      UUID operationId, UUID discoveryId, UUID meetingId, UUID runId, UUID replacesOperationId) {}

  private record Preparation(
      UUID operationId,
      UUID acquisitionOperationId,
      UUID runId,
      String profileId,
      boolean freshRevision,
      UUID replacesOperationId) {}
}
