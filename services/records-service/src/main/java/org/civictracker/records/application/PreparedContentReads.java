package org.civictracker.records.application;

import jakarta.persistence.EntityManager;
import java.util.List;
import java.util.UUID;
import org.civictracker.records.api.OperationViews;
import org.civictracker.records.configuration.PreparationSettings;
import org.civictracker.records.domain.AcquisitionData.Kind;
import org.civictracker.records.domain.PreparationData.Locator;
import org.civictracker.records.domain.PreparationData.Segment;
import org.civictracker.records.domain.PreparationData.Unit;
import org.civictracker.records.persistence.AcquisitionItem;
import org.civictracker.records.persistence.OriginalVersion;
import org.civictracker.records.persistence.PreparationEntry;
import org.civictracker.records.persistence.PreparedContent;
import org.civictracker.records.persistence.PreparedSegment;
import org.civictracker.records.persistence.PreparedUnit;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

/** Reads only sealed revisions and keeps paged text pinned to one revision. */
@Service
@Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
public class PreparedContentReads {
  private final EntityManager entities;
  private final ObjectMapper json;
  private final RecordsOperations operations;
  private final int maxReadUnits;

  public PreparedContentReads(
      EntityManager entities,
      ObjectMapper json,
      RecordsOperations operations,
      PreparationSettings.Settings settings) {
    this.entities = entities;
    this.json = json;
    this.operations = operations;
    this.maxReadUnits = settings.maxReadUnits();
  }

  public record Content(
      UUID documentId,
      UUID versionId,
      UUID preparationId,
      String profileFingerprint,
      int unitCount,
      List<Unit> units,
      int page,
      int size,
      boolean hasNext) {}

  public Content content(UUID versionId, UUID preparationId, int page, int size) {
    if (size > maxReadUnits) {
      throw new OperationProblem(400, "INVALID_PAGE");
    }
    int offset = operations.offset(page, size);
    if (entities.find(OriginalVersion.class, versionId) == null) {
      throw new OperationProblem(404, "RECORD_NOT_FOUND");
    }
    PreparedContent content;
    if (preparationId == null) {
      if (page != 0) {
        throw new OperationProblem(400, "PREPARATION_REFERENCE_REQUIRED");
      }
      var choices =
          entities
              .createQuery(
                  "from PreparedContent where versionId=:v and sealedAt is not null",
                  PreparedContent.class)
              .setParameter("v", versionId)
              .setMaxResults(2)
              .getResultList();
      if (choices.size() > 1) {
        throw new OperationProblem(409, "PREPARATION_REFERENCE_REQUIRED");
      }
      content = choices.isEmpty() ? null : choices.getFirst();
    } else {
      content = entities.find(PreparedContent.class, preparationId);
    }
    if (content == null || content.sealedAt == null || !content.versionId.equals(versionId)) {
      throw new OperationProblem(404, "PREPARED_CONTENT_NOT_FOUND");
    }
    var rows =
        entities
            .createQuery(
                "from PreparedUnit where preparationId=:p order by ordinal", PreparedUnit.class)
            .setParameter("p", content.id)
            .setFirstResult(offset)
            .setMaxResults(size + 1)
            .getResultList();
    List<Unit> units =
        rows.stream()
            .limit(size)
            .map(row -> {
              var segments =
                  entities
                      .createQuery(
                          "from PreparedSegment where unitId=:u order by ordinal",
                          PreparedSegment.class)
                      .setParameter("u", row.id)
                      .getResultList()
                      .stream()
                      .map(s ->
                          new Segment(
                              s.id,
                              s.textValue,
                              s.method,
                              json.readValue(s.locatorJson, Locator.class),
                              s.score,
                              s.selected))
                      .toList();
              return new Unit(
                  row.ordinal,
                  row.disposition,
                  row.nativeText,
                  segments,
                  List.of(json.readValue(row.notesJson, String[].class)));
            })
            .toList();
    return new Content(
        content.documentId,
        versionId,
        content.id,
        content.profileFingerprint,
        content.unitCount,
        units,
        page,
        size,
        rows.size() > size);
  }

  public OperationViews.Page<OperationViews.PreparationResult> results(
      UUID id, int page, int size) {
    int offset = operations.offset(page, size);
    operations.requireSuccess(id, Kind.PREPARATION);
    var rows =
        entities
            .createQuery(
                "from PreparationEntry where operationId=:o order by ordinal",
                PreparationEntry.class)
            .setParameter("o", id)
            .setFirstResult(offset)
            .setMaxResults(size + 1)
            .getResultList();
    var results =
        rows.stream()
            .limit(size)
            .map(row -> {
              var content = entities.find(PreparedContent.class, row.preparationId);
              var input = entities.find(AcquisitionItem.class, row.acquisitionItemId);
              return new OperationViews.PreparationResult(
                  input.id,
                  input.documentId,
                  input.versionId,
                  content.id,
                  content.profileFingerprint);
            })
            .toList();
    return new OperationViews.Page<>(results, page, size, rows.size() > size);
  }
}
