package org.civictracker.records.application;

import jakarta.persistence.EntityManager;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import org.apache.pdfbox.util.Version;
import org.civictracker.records.api.OperationViews;
import org.civictracker.records.configuration.PreparationSettings;
import org.civictracker.records.domain.AcquisitionData.Kind;
import org.civictracker.records.domain.AcquisitionData.Target;
import org.civictracker.records.domain.PreparationData.Input;
import org.civictracker.records.domain.PreparationData.OcrRegion;
import org.civictracker.records.domain.PreparationData.OcrResult;
import org.civictracker.records.domain.PreparationData.PdfLocator;
import org.civictracker.records.domain.PreparationData.Point;
import org.civictracker.records.domain.PreparationData.Profile;
import org.civictracker.records.domain.PreparationData.Routing;
import org.civictracker.records.domain.PreparationData.Segment;
import org.civictracker.records.domain.PreparationData.Selection;
import org.civictracker.records.domain.PreparationData.Specification;
import org.civictracker.records.domain.PreparationData.Unit;
import org.civictracker.records.ocr.OcrClient;
import org.civictracker.records.persistence.AcquisitionItem;
import org.civictracker.records.persistence.Artifact;
import org.civictracker.records.persistence.OriginalVersion;
import org.civictracker.records.persistence.PreparationEntry;
import org.civictracker.records.persistence.PreparedContent;
import org.civictracker.records.persistence.PreparedSegment;
import org.civictracker.records.persistence.PreparedUnit;
import org.civictracker.records.persistence.RecordOperation;
import org.civictracker.records.preparation.HtmlContentExtractor;
import org.civictracker.records.preparation.PdfContentExtractor;
import org.civictracker.records.storage.OriginalFileStore;
import org.jsoup.Jsoup;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import tools.jackson.databind.ObjectMapper;

/** Prepares frozen owner inputs and commits each completed unit with its checkpoint. */
@Service
public class RecordPreparation {
  public static final String PIPELINE = "native-first-2";
  private final EntityManager entities;
  private final RecordsOperations operations;
  private final OriginalFileStore files;
  private final PreparationSettings.Settings settings;
  private final ObjectMapper json;
  private final JdbcTemplate jdbc;
  private final OcrClient ocr;

  public RecordPreparation(
      EntityManager entities,
      RecordsOperations operations,
      OriginalFileStore files,
      PreparationSettings.Settings settings,
      ObjectMapper json,
      JdbcTemplate jdbc,
      OcrClient ocr) {
    this.entities = entities;
    this.operations = operations;
    this.files = files;
    this.settings = settings;
    this.json = json;
    this.jdbc = jdbc;
    this.ocr = ocr;
  }

  public OperationViews.Status accept(OperationViews.PreparationCommand command) {
    if (command.operationId() == null
        || command.acquisitionOperationId() == null
        || command.profileId() == null
        || command.profileId().isBlank()) {
      throw new OperationProblem(400, "INVALID_REQUEST");
    }
    return operations.transaction(() -> {
      RecordOperation prior = entities.find(RecordOperation.class, command.operationId());
      if (prior != null) {
        if (prior.kind != Kind.PREPARATION) {
          throw new OperationProblem(409, "OPERATION_INPUT_CONFLICT");
        }
        Selection old = json.readValue(prior.inputJson, Selection.class);
        if (!old.acquisitionOperationId().equals(command.acquisitionOperationId())
            || !Objects.equals(old.runId(), command.runId())
            || !old.profileId().equals(command.profileId())
            || old.freshRevision() != command.freshRevision()
            || !Objects.equals(prior.replacesId, command.replacesOperationId())) {
          throw new OperationProblem(409, "OPERATION_INPUT_CONFLICT");
        }
        return operations.status(prior.id);
      }
      operations.requireSuccess(command.acquisitionOperationId(), Kind.ACQUISITION);
      Profile profile = settings.profiles().get(command.profileId());
      if (profile == null) {
        throw new OperationProblem(400, "UNKNOWN_PREPARATION_PROFILE");
      }
      var rows =
          entities
              .createQuery(
                  "from AcquisitionItem where operationId=:o order by ordinal",
                  AcquisitionItem.class)
              .setParameter("o", command.acquisitionOperationId())
              .setMaxResults(settings.maxInputs() + 1)
              .getResultList();
      if (rows.size() > settings.maxInputs()) {
        throw new OperationProblem(422, "PREPARATION_INPUT_LIMIT");
      }
      var inputs =
          rows.stream()
              .map(row ->
                  new Input(
                      row.id,
                      row.documentId,
                      row.versionId,
                      row.artifactId,
                      row.sha256,
                      row.byteLength,
                      json.readValue(row.targetJson, Target.class).format()))
              .toList();
      Specification specification = specification(profile);
      return operations.accept(
          command.operationId(),
          Kind.PREPARATION,
          json.writeValueAsString(
              new Selection(
                  command.acquisitionOperationId(),
                  command.runId(),
                  command.profileId(),
                  command.freshRevision(),
                  specification,
                  PdfContentExtractor.sha256(json.writeValueAsBytes(specification)),
                  inputs)),
          command.replacesOperationId());
    });
  }

  public static Specification specification(Profile profile) {
    return new Specification(
        profile,
        Version.getVersion(),
        Objects.toString(Jsoup.class.getPackage().getImplementationVersion(), "1.24.1"),
        PIPELINE);
  }

  public void execute(RecordOperation operation) throws IOException, InterruptedException {
    Selection selection = json.readValue(operation.inputJson, Selection.class);
    if (!specification(selection.specification().profile()).equals(selection.specification())) {
      throw new OperationProblem(409, "PREPARATION_RUNTIME_CHANGED");
    }
    Profile profile = selection.specification().profile();
    if (selection.inputs().isEmpty()) {
      operations.transaction(() -> {
        operations.note(operation.id, "NO_SELECTED_ORIGINALS");
        return null;
      });
    }
    for (int index = 0; index < selection.inputs().size(); index++) {
      if (Thread.currentThread().isInterrupted()) {
        throw new InterruptedException();
      }
      Input input = selection.inputs().get(index);
      progress(operation, input.versionId(), null);
      if (input.byteLength() > profile.maxInputBytes()) {
        throw new OperationProblem(422, "PREPARATION_INPUT_LIMIT");
      }
      String key =
          operations.transaction(() -> {
            operations.guard(operation.id, operation.attemptId);
            OriginalVersion version = entities.find(OriginalVersion.class, input.versionId());
            if (version == null
                || !version.documentId.equals(input.documentId())
                || !version.artifactId.equals(input.artifactId())
                || !version.sha256.equals(input.sha256())
                || version.byteLength != input.byteLength()) {
              throw new OperationProblem(409, "PREPARATION_SOURCE_CHANGED");
            }
            return entities.find(Artifact.class, input.artifactId()).storageKey;
          });
      try (var stream = files.openVerifiedLength(key, input.byteLength());
          var staged = files.stage(stream)) {
        if (!staged.sha256().equals(input.sha256()) || staged.byteLength() != input.byteLength()) {
          throw new OperationProblem(409, "PREPARATION_SOURCE_CHANGED");
        }
        int ordinal = index;
        PreparedContent content =
            operations.transaction(() -> content(operation, selection, input, ordinal));
        if (content.sealedAt != null) {
          continue;
        }
        switch (input.format()) {
          case HTML -> {
            if (input.byteLength() > Math.min(profile.maxInputBytes(), 8388608)) {
              throw new OperationProblem(422, "HTML_INPUT_LIMIT");
            }
            var units = HtmlContentExtractor.extract(staged.path(), profile);
            expected(operation, content, units.size());
            for (Unit unit : units) {
              save(operation, content.id, unit);
            }
          }
          case PDF -> {
            try (var pdf = new PdfContentExtractor(staged.path(), profile)) {
              expected(operation, content, pdf.size());
              for (int page = 0; page < pdf.size(); page++) {
                progress(operation, input.versionId(), page);
                if (Thread.currentThread().isInterrupted()) {
                  throw new InterruptedException();
                }
                if (saved(content.id, page)) {
                  continue;
                }
                int number = page + 1;
                Routing routing =
                    profile.routing().stream()
                        .filter(r -> r.versionId().equals(input.versionId()) && r.page() == number)
                        .findFirst()
                        .orElse(null);
                var work = pdf.page(page, routing);
                var segments = new ArrayList<>(work.nativeUnit().segments());
                for (int image = 0; image < work.images().size(); image++) {
                  var source = work.images().get(image);
                  OcrResult result = recognize(operation, content.id, page, image, source, profile);
                  if (!PdfContentExtractor.usableText(
                      result.regions().stream()
                          .map(OcrRegion::text)
                          .collect(java.util.stream.Collectors.joining("\n")))) {
                    throw new OperationProblem(422, "OCR_CONTENT_UNRESOLVED");
                  }
                  for (OcrRegion region : result.regions()) {
                    if (region.text().isBlank()) {
                      continue;
                    }
                    var placement = source.locator().image();
                    var matrix = placement.imageToPage();
                    List<Point> polygon =
                        region.polygon().stream()
                            .map(p ->
                                new Point(
                                    matrix.get(0) * p.x() + matrix.get(2) * p.y() + matrix.get(4),
                                    matrix.get(1) * p.x() + matrix.get(3) * p.y() + matrix.get(5)))
                            .toList();
                    var locator = source.locator();
                    segments.add(
                        new Segment(
                            UUID.randomUUID(),
                            region.text(),
                            "OCR",
                            new PdfLocator(
                                locator.page(),
                                locator.pageLabel(),
                                locator.width(),
                                locator.height(),
                                polygon,
                                placement),
                            region.score(),
                            true));
                  }
                }
                if (!work.images().isEmpty()
                    && segments.stream()
                        .noneMatch(
                            s -> s.method().equals("OCR") && s.selected() && !s.text().isBlank())) {
                  throw new OperationProblem(422, "OCR_CONTENT_UNRESOLVED");
                }
                if (segments.size() > profile.maxRegions()
                    || segments.stream()
                            .mapToLong(s -> s.text().codePointCount(0, s.text().length()))
                            .sum()
                        > profile.maxCharactersPerUnit()) {
                  throw new OperationProblem(422, "PREPARATION_TEXT_LIMIT");
                }
                save(
                    operation,
                    content.id,
                    new Unit(
                        page,
                        work.nativeUnit().disposition(),
                        work.nativeUnit().nativeText(),
                        segments,
                        work.nativeUnit().notes()));
              }
            }
          }
          case UNSUPPORTED -> throw new OperationProblem(422, "PREPARATION_FORMAT_UNSUPPORTED");
        }
        operations.transaction(() -> {
          operations.guard(operation.id, operation.attemptId);
          PreparedContent row = entities.find(PreparedContent.class, content.id);
          long count =
              entities
                  .createQuery(
                      "select count(u) from PreparedUnit u where preparationId=:p", Long.class)
                  .setParameter("p", content.id)
                  .getSingleResult();
          if (row.unitCount == null || count != row.unitCount) {
            throw new OperationProblem(409, "PREPARATION_INCOMPLETE");
          }
          row.sealedAt = Instant.now();
          return null;
        });
      }
    }
  }

  private PreparedContent content(
      RecordOperation operation, Selection selection, Input input, int ordinal) {
    operations.guard(operation.id, operation.attemptId);
    var entries =
        entities
            .createQuery(
                "from PreparationEntry where operationId=:o and ordinal=:n", PreparationEntry.class)
            .setParameter("o", operation.id)
            .setParameter("n", ordinal)
            .getResultList();
    if (!entries.isEmpty()) {
      return entities.find(PreparedContent.class, entries.getFirst().preparationId);
    }
    var reusable =
        selection.freshRevision()
            ? List.<PreparedContent>of()
            : entities
                .createQuery(
                    "from PreparedContent where versionId=:v and artifactId=:a and"
                        + " profileFingerprint=:f and sealedAt is not null order by sealedAt"
                        + " desc,id",
                    PreparedContent.class)
                .setParameter("v", input.versionId())
                .setParameter("a", input.artifactId())
                .setParameter("f", selection.fingerprint())
                .setMaxResults(1)
                .getResultList();
    PreparedContent content;
    if (reusable.isEmpty()) {
      content = new PreparedContent();
      content.documentId = input.documentId();
      content.versionId = input.versionId();
      content.artifactId = input.artifactId();
      content.inputSha256 = input.sha256();
      content.profileFingerprint = selection.fingerprint();
      content.profileJson = json.writeValueAsString(selection.specification());
      entities.persist(content);
    } else {
      content = reusable.getFirst();
    }
    var entry = new PreparationEntry();
    entry.operationId = operation.id;
    entry.ordinal = ordinal;
    entry.acquisitionItemId = input.acquisitionItemId();
    entry.preparationId = content.id;
    entities.persist(entry);
    return content;
  }

  private void expected(RecordOperation operation, PreparedContent content, int count) {
    operations.transaction(() -> {
      operations.guard(operation.id, operation.attemptId);
      var row = entities.find(PreparedContent.class, content.id);
      if (row.unitCount != null && row.unitCount != count) {
        throw new OperationProblem(409, "PREPARATION_UNIT_COUNT_CHANGED");
      }
      row.unitCount = count;
      return null;
    });
  }

  private boolean saved(UUID preparationId, int ordinal) {
    return operations.transaction(() ->
        !entities
            .createQuery(
                "from PreparedUnit where preparationId=:p and ordinal=:n", PreparedUnit.class)
            .setParameter("p", preparationId)
            .setParameter("n", ordinal)
            .getResultList()
            .isEmpty());
  }

  private void progress(RecordOperation operation, UUID version, Integer unit) {
    operations.transaction(() -> {
      operations.guard(operation.id, operation.attemptId);
      var row = entities.find(RecordOperation.class, operation.id);
      row.preparingVersionId = version;
      row.preparingUnitOrdinal = unit;
      return null;
    });
  }

  private void save(RecordOperation operation, UUID preparationId, Unit unit) {
    operations.transaction(() -> {
      operations.guard(operation.id, operation.attemptId);
      if (saved(preparationId, unit.ordinal())) {
        return null;
      }
      var row = new PreparedUnit();
      row.preparationId = preparationId;
      row.ordinal = unit.ordinal();
      row.disposition = unit.disposition();
      row.nativeText = unit.nativeText();
      row.notesJson = json.writeValueAsString(unit.notes());
      entities.persist(row);
      for (int n = 0; n < unit.segments().size(); n++) {
        Segment source = unit.segments().get(n);
        var segment = new PreparedSegment();
        segment.id = source.id();
        segment.unitId = row.id;
        segment.ordinal = n;
        segment.textValue = source.text();
        segment.method = source.method();
        segment.locatorJson = json.writeValueAsString(source.locator());
        segment.score = source.score();
        segment.selected = source.selected();
        entities.persist(segment);
      }
      return null;
    });
  }

  private OcrResult recognize(
      RecordOperation operation,
      UUID preparationId,
      int unit,
      int ordinal,
      PdfContentExtractor.ImageInput image,
      Profile profile)
      throws IOException, InterruptedException {
    if (image.bytes().length > settings.maxOcrBytes()) {
      throw new OperationProblem(422, "OCR_INPUT_LIMIT");
    }
    ocr.preflight();
    String hash = image.locator().image().imageSha256();
    var cached =
        jdbc.queryForList(
            "select result_json from records.preparation_ocr where preparation_id=? and"
                + " unit_ordinal=? and image_ordinal=? and image_sha256=? and state='RETURNED' and"
                + " result_json is not null order by id limit 1",
            String.class,
            preparationId,
            unit,
            ordinal,
            hash);
    if (!cached.isEmpty()) {
      return json.readValue(cached.getFirst(), OcrResult.class);
    }
    UUID execution = UUID.randomUUID();
    try (var staged = files.stage(new ByteArrayInputStream(image.bytes()))) {
      String key = files.installPrepared(staged, execution);
      operations.transaction(() -> {
        operations.guard(operation.id, operation.attemptId);
        jdbc.update(
            "insert into records.preparation_ocr"
                + " (id,operation_id,attempt_id,preparation_id,unit_ordinal,image_ordinal,image_sha256,image_key,state)"
                + " values (?,?,?,?,?,?,?,?,'STARTED')",
            execution,
            operation.id,
            operation.attemptId,
            preparationId,
            unit,
            ordinal,
            hash,
            key);
        return null;
      });
    }
    try {
      OcrResult result = ocr.recognize(execution, image.bytes(), image.locator().image(), profile);
      operations.transaction(() -> {
        operations.guard(operation.id, operation.attemptId);
        jdbc.update(
            "update records.preparation_ocr set state='RETURNED',result_json=? where id=?",
            json.writeValueAsString(result),
            execution);
        return null;
      });
      return result;
    } catch (OcrClient.FinishedFailure failure) {
      operations.transaction(() -> {
        operations.guard(operation.id, operation.attemptId);
        jdbc.update("update records.preparation_ocr set state='RETURNED' where id=?", execution);
        return null;
      });
      throw new OperationProblem(422, failure.code());
    }
  }

  public UUID unresolved(UUID operationId) {
    var rows =
        jdbc.queryForList(
            "select id from records.preparation_ocr where operation_id=? "
                + "and state='STARTED' order by id limit 1",
            UUID.class,
            operationId);
    return rows.isEmpty() ? null : rows.getFirst();
  }
}
