package org.civictracker.records.application;

import jakarta.persistence.EntityManager;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.civictracker.records.domain.AcquisitionData.Acquisition;
import org.civictracker.records.domain.AcquisitionData.Format;
import org.civictracker.records.domain.AcquisitionData.Kind;
import org.civictracker.records.domain.AcquisitionData.Meeting;
import org.civictracker.records.domain.AcquisitionData.Packet;
import org.civictracker.records.domain.AcquisitionData.Selection;
import org.civictracker.records.domain.AcquisitionData.Target;
import org.civictracker.records.domain.Hashes;
import org.civictracker.records.domain.SourceInput;
import org.civictracker.records.persistence.AcquisitionItem;
import org.civictracker.records.persistence.RecordOperation;
import org.civictracker.records.portal.primegov.PortalHttp;
import org.civictracker.records.portal.primegov.PrimeGovParser;
import org.springframework.stereotype.Service;
import tools.jackson.databind.ObjectMapper;

/** Acquires a whole selected meeting with atomic per-original checkpoints. */
@Service
public class RecordAcquisition {
  private record Inputs(Acquisition command, Selection selection, Meeting meeting) {}

  private final RecordsOperations operations;
  private final RecordsFoundation foundation;
  private final RecordDiscovery discovery;
  private final PortalHttp portal;
  private final PrimeGovParser parser;
  private final EntityManager entities;
  private final ObjectMapper json;

  public RecordAcquisition(
      RecordsOperations operations,
      RecordsFoundation foundation,
      RecordDiscovery discovery,
      PortalHttp portal,
      PrimeGovParser parser,
      EntityManager entities,
      ObjectMapper json) {
    this.operations = operations;
    this.foundation = foundation;
    this.discovery = discovery;
    this.portal = portal;
    this.parser = parser;
    this.entities = entities;
    this.json = json;
  }

  public void execute(RecordOperation operation) throws IOException, InterruptedException {
    Inputs inputs =
        operations.transaction(() -> {
          var command = json.readValue(operation.inputJson, Acquisition.class);
          var prior = operations.requireSuccess(command.discoveryId(), Kind.DISCOVERY);
          var snapshot = operations.selectedMeeting(command.discoveryId(), command.meetingId());
          return new Inputs(
              command,
              json.readValue(prior.inputJson, Selection.class),
              json.readValue(snapshot.payload, Meeting.class));
        });
    var current =
        discovery.load(operation, inputs.selection()).stream()
            .filter(row -> row.meeting().officialId().equals(inputs.meeting().officialId()))
            .findFirst()
            .orElseThrow(() -> new OperationProblem(409, "SOURCE_SELECTION_CHANGED"));
    if (!current.meeting().equals(inputs.meeting())) {
      throw new OperationProblem(409, "SOURCE_SELECTION_CHANGED");
    }
    operations.transaction(() -> {
      operations.guard(operation.id, operation.attemptId);
      add(operation.id, inputs.meeting().documents());
      var previous = operations.previousMeeting(inputs.command().meetingId());
      if (previous.isEmpty()) {
        operations.note(operation.id, "FIRST_SUCCESSFUL_ACQUISITION_BASELINE_UNAVAILABLE");
      } else if (!previous.get().equals(inputs.meeting())) {
        operations.note(operation.id, "SOURCE_METADATA_OR_MEMBERSHIP_CHANGED");
      }
      if (inputs.meeting().documents().stream().noneMatch(t -> t.format() == Format.HTML)) {
        operations.note(operation.id, "ITEM_DISCOVERY_UNAVAILABLE_WITHOUT_HTML");
      }
      if (inputs.meeting().documents().isEmpty()) {
        operations.note(operation.id, "NO_PUBLISHED_SELECTED_ORIGINALS");
      }
      return null;
    });
    while (true) {
      AcquisitionItem item =
          operations.transaction(() ->
              entities
                  .createQuery(
                      "from AcquisitionItem where operationId=:o and versionId is null order by"
                          + " ordinal",
                      AcquisitionItem.class)
                  .setParameter("o", operation.id)
                  .setMaxResults(1)
                  .getResultStream()
                  .findFirst()
                  .orElse(null));
      if (item == null) {
        return;
      }
      Target target = json.readValue(item.targetJson, Target.class);
      if (target.format() == Format.UNSUPPORTED) {
        throw new OperationProblem(503, "UNSUPPORTED_SOURCE_FORMAT");
      }
      try (var fetched = portal.fetch(inputs.selection().profile(), target.url())) {
        Packet packet = null;
        if (target.format() == Format.PDF) {
          validatePdf(fetched.staged().path());
        } else {
          try (var stream = Files.newInputStream(fetched.staged().path())) {
            packet = parser.packet(stream, fetched.resolved());
          } catch (IOException | RuntimeException invalid) {
            discovery.capture(operation, fetched);
            throw invalid;
          }
        }
        Packet expanded = packet;
        operations.transaction(() -> {
          operations.guard(operation.id, operation.attemptId);
          AcquisitionItem saved = entities.find(AcquisitionItem.class, item.id);
          SourceInput source =
              new SourceInput(
                  inputs.selection().sourceId(),
                  inputs.meeting().officialId(),
                  inputs.meeting().title(),
                  inputs.meeting().sourceDate(),
                  target.itemId(),
                  target.itemLabel(),
                  target.templateId(),
                  target.outputType(),
                  target.language(),
                  target.family(),
                  target.officialId(),
                  target.url(),
                  target.title(),
                  fetched.observedAt(),
                  true,
                  inputs.meeting().cancellationEvidence());
          var receipt =
              foundation.retainStaged(
                  source, fetched.staged(), target.publicationDate(), fetched.resolved());
          saved.documentId = receipt.documentId();
          saved.versionId = receipt.versionId();
          saved.artifactId = receipt.artifactId();
          saved.observationId = receipt.observationId();
          saved.currentVersionId = receipt.currentVersionId();
          saved.sha256 = receipt.sha256();
          saved.byteLength = receipt.byteLength();
          saved.currentChanged = receipt.currentChanged();
          if (receipt.currentChanged()) {
            operations.note(operation.id, "CURRENT_ORIGINAL_CHANGED");
          }
          if (expanded != null) {
            foundation.observeItems(
                inputs.command().meetingId(), expanded.items(), current.observedAt());
            add(operation.id, expanded.attachments());
          }
          if (target.language() == null) {
            operations.note(operation.id, "SOURCE_LANGUAGE_UNSPECIFIED");
          }
          return null;
        });
      }
    }
  }

  private void add(UUID operationId, List<Target> targets) {
    List<AcquisitionItem> existing =
        entities
            .createQuery(
                "from AcquisitionItem where operationId=:o order by ordinal", AcquisitionItem.class)
            .setParameter("o", operationId)
            .getResultList();
    List<String> retained = new ArrayList<>(existing.stream().map(i -> i.targetJson).toList());
    for (Target target : targets) {
      String payload = json.writeValueAsString(target);
      if (retained.contains(payload)) {
        continue;
      }
      AcquisitionItem item = new AcquisitionItem();
      item.operationId = operationId;
      item.ordinal = retained.size();
      item.targetJson = payload;
      item.targetKey = hash(payload);
      entities.persist(item);
      retained.add(payload);
    }
  }

  private static String hash(String value) {
    return Hashes.sha256(value.getBytes(StandardCharsets.UTF_8));
  }

  private static void validatePdf(Path file) throws IOException {
    try (FileChannel channel = FileChannel.open(file)) {
      ByteBuffer header = ByteBuffer.allocate(5);
      channel.read(header);
      ByteBuffer tail = ByteBuffer.allocate((int) Math.min(8192, channel.size()));
      channel.position(channel.size() - tail.capacity());
      while (tail.hasRemaining() && channel.read(tail) > 0) {
        // Read the bounded trailer without buffering the PDF.
      }
      if (!new String(header.array(), StandardCharsets.US_ASCII).equals("%PDF-")
          || !new String(tail.array(), StandardCharsets.US_ASCII).contains("%%EOF")) {
        throw new OperationProblem(503, "INVALID_SOURCE_PDF");
      }
    }
  }
}
