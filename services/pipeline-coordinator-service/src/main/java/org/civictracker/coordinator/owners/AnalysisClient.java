package org.civictracker.coordinator.owners;

import static org.civictracker.coordinator.owners.OwnerHttp.*;

import java.net.URI;
import java.time.Duration;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.civictracker.coordinator.run.AnalysisSelection;
import org.civictracker.coordinator.run.FrozenAnalysisRequest;
import tools.jackson.databind.JsonNode;

/** Calls analysis without exposing its persistence or wire DTOs. */
public final class AnalysisClient {
  private final OwnerHttp http;

  public AnalysisClient(URI base, String username, String password, Duration timeout) {
    http = new OwnerHttp(base, username, password, timeout);
  }

  public Optional<OwnerStatus> operation(UUID id) throws OwnerRejected, OwnerUnreachable {
    String path = "/internal/v1/analysis-operations/" + id;
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
              optionalId(node, "blockedAttemptId"),
              optionalId(node, "revisionId"),
              http.url(path));
        });
  }

  public void analyze(UUID opId, AnalysisSelection selection, List<UUID> priorRevisionIds)
      throws OwnerRejected, OwnerUnreachable {
    analyze(freeze(opId, selection, priorRevisionIds));
  }

  public static FrozenAnalysisRequest freeze(
      UUID opId, AnalysisSelection selection, List<UUID> priors) {
    String json = JSON.writeValueAsString(new Command(opId, selection, List.copyOf(priors)));
    return new FrozenAnalysisRequest(opId, selection, priors, json, sha256(json));
  }

  /** Restores the exact stored bytes without serializing the command again. */
  public static FrozenAnalysisRequest readFrozen(String json, String expectedSha256) {
    if (!sha256(json).equals(expectedSha256)) {
      throw new IllegalArgumentException("Frozen analysis hash mismatch");
    }
    var command = JSON.readValue(json, Command.class);
    return new FrozenAnalysisRequest(
        command.id(), command.selection(), command.priorRevisionIds(), json, expectedSha256);
  }

  public void analyze(FrozenAnalysisRequest request) throws OwnerRejected, OwnerUnreachable {
    var restored = readFrozen(request.json(), request.sha256());
    if (!restored.equals(request)) {
      throw new IllegalArgumentException("Frozen analysis values do not match its bytes");
    }
    http.postJson("/internal/v1/analysis-operations", request.json());
  }

  private static String sha256(String value) {
    try {
      return java.util.HexFormat.of()
          .formatHex(
              java.security.MessageDigest.getInstance("SHA-256")
                  .digest(value.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
    } catch (java.security.NoSuchAlgorithmException impossible) {
      throw new IllegalStateException(impossible);
    }
  }

  public Optional<RevisionHeader> revision(UUID revisionId) throws OwnerRejected, OwnerUnreachable {
    return http.get(
        "/internal/v1/meeting-analyses/" + revisionId,
        true,
        node -> {
          if (!revisionId.equals(id(node, "revisionId"))) {
            throw new IllegalArgumentException();
          }
          var versions = new HashSet<UUID>();
          for (String collection : List.of("developments", "associations")) {
            JsonNode rows = node.get(collection);
            if (rows == null || !rows.isArray()) {
              throw new IllegalArgumentException();
            }
            for (var row : rows) {
              var evidence = row.get("evidence");
              if (evidence == null || !evidence.isArray()) {
                throw new IllegalArgumentException();
              }
              evidence.forEach(span -> versions.add(id(span, "versionId")));
            }
          }
          return new RevisionHeader(
              revisionId, id(node, "meetingId"), date(node, "meetingDate"), versions);
        });
  }

  private record Command(UUID id, AnalysisSelection selection, List<UUID> priorRevisionIds) {}
}
