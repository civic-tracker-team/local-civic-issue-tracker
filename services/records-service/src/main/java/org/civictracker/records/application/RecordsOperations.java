package org.civictracker.records.application;

import jakarta.persistence.EntityManager;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.civictracker.records.api.OperationViews;
import org.civictracker.records.configuration.PortalSettings;
import org.civictracker.records.domain.AcquisitionData.Acquisition;
import org.civictracker.records.domain.AcquisitionData.Kind;
import org.civictracker.records.domain.AcquisitionData.Meeting;
import org.civictracker.records.domain.AcquisitionData.Receipt;
import org.civictracker.records.domain.AcquisitionData.Selection;
import org.civictracker.records.domain.AcquisitionData.State;
import org.civictracker.records.domain.AcquisitionData.Target;
import org.civictracker.records.persistence.AcquisitionItem;
import org.civictracker.records.persistence.DiscoveredMeeting;
import org.civictracker.records.persistence.RecordOperation;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.ObjectMapper;

/** Owns command acceptance, durable claiming and result visibility. */
@Service
public class RecordsOperations {
  private final EntityManager entities;
  private final JdbcTemplate jdbc;
  private final TransactionTemplate transactions;
  private final ObjectMapper json;
  private final PortalSettings.Sources sources;
  private final int maximumPageSize;
  private final int maximumYears;
  public final UUID workerId = UUID.randomUUID();

  public RecordsOperations(
      EntityManager entities,
      JdbcTemplate jdbc,
      PlatformTransactionManager manager,
      ObjectMapper json,
      PortalSettings.Sources sources,
      @Value("${records.operations.max-page-size:100}") int maximumPageSize,
      @Value("${records.operations.max-discovery-years:20}") int maximumYears) {
    this.entities = entities;
    this.jdbc = jdbc;
    this.transactions = new TransactionTemplate(manager);
    this.json = json;
    this.sources = sources;
    this.maximumPageSize = maximumPageSize;
    this.maximumYears = maximumYears;
    if (maximumPageSize < 1 || maximumYears < 1) {
      throw new IllegalArgumentException("Operation bounds must be positive");
    }
  }

  public OperationViews.Status discover(OperationViews.DiscoveryCommand command) {
    if (command.operationId() == null
        || command.sourceId() == null
        || command.from() == null
        || command.to() == null
        || command.to().isBefore(command.from())
        || (long) command.to().getYear() - command.from().getYear() >= maximumYears
        || command.committeeIds() == null
        || command.committeeIds().isEmpty()
        || command.committeeIds().stream().anyMatch(s -> s == null || s.isBlank())) {
      throw new OperationProblem(400, "INVALID_REQUEST");
    }
    OperationViews.Status replay =
        transactions.execute(status -> {
          lockSlot();
          RecordOperation prior = entities.find(RecordOperation.class, command.operationId());
          if (prior == null) {
            return null;
          }
          if (prior.kind != Kind.DISCOVERY) {
            throw new OperationProblem(409, "OPERATION_INPUT_CONFLICT");
          }
          Selection old = json.readValue(prior.inputJson, Selection.class);
          if (!old.sourceId().equals(command.sourceId())
              || !old.from().equals(command.from())
              || !old.to().equals(command.to())
              || !old.committeeIds()
                  .equals(command.committeeIds().stream().distinct().sorted().toList())
              || !Objects.equals(prior.replacesId, command.replacesOperationId())) {
            throw new OperationProblem(409, "OPERATION_INPUT_CONFLICT");
          }
          return view(prior);
        });
    if (replay != null) {
      return replay;
    }
    var selection =
        new Selection(
            command.sourceId(),
            sources.required(command.sourceId()),
            command.from(),
            command.to(),
            command.committeeIds().stream().distinct().sorted().toList());
    return accept(
        command.operationId(),
        Kind.DISCOVERY,
        json.writeValueAsString(selection),
        command.replacesOperationId());
  }

  public OperationViews.Status acquire(OperationViews.AcquisitionCommand command) {
    if (command.operationId() == null
        || command.discoveryId() == null
        || command.meetingId() == null) {
      throw new OperationProblem(400, "INVALID_REQUEST");
    }
    transactions.executeWithoutResult(status -> {
      requireSuccess(command.discoveryId(), Kind.DISCOVERY);
      selectedMeeting(command.discoveryId(), command.meetingId());
    });
    var selection = new Acquisition(command.discoveryId(), command.meetingId(), command.runId());
    return accept(
        command.operationId(),
        Kind.ACQUISITION,
        json.writeValueAsString(selection),
        command.replacesOperationId());
  }

  OperationViews.Status accept(UUID id, Kind kind, String input, UUID replaces) {
    return transactions.execute(status -> {
      lockSlot();
      RecordOperation prior = entities.find(RecordOperation.class, id);
      if (prior != null) {
        if (prior.kind != kind
            || !prior.inputJson.equals(input)
            || !Objects.equals(prior.replacesId, replaces)) {
          throw new OperationProblem(409, "OPERATION_INPUT_CONFLICT");
        }
        return view(prior);
      }
      if (replaces != null) {
        RecordOperation old = required(replaces);
        if (old.state != State.FAILED || old.kind != kind) {
          throw new OperationProblem(409, "REPLACEMENT_REQUIRES_FAILED_OPERATION");
        }
        if (kind == Kind.DISCOVERY) {
          var before = json.readValue(old.inputJson, Selection.class);
          var after = json.readValue(input, Selection.class);
          if (!before.sourceId().equals(after.sourceId())
              || !before.from().equals(after.from())
              || !before.to().equals(after.to())
              || !before.committeeIds().equals(after.committeeIds())) {
            throw new OperationProblem(409, "REPLACEMENT_WORK_CONFLICT");
          }
        }
        if (kind == Kind.ACQUISITION) {
          var before = json.readValue(old.inputJson, Acquisition.class);
          var after = json.readValue(input, Acquisition.class);
          if (!before.meetingId().equals(after.meetingId())
              || !Objects.equals(before.runId(), after.runId())) {
            throw new OperationProblem(409, "REPLACEMENT_WORK_CONFLICT");
          }
        }
        if (kind == Kind.PREPARATION) {
          var before =
              json.readValue(
                  old.inputJson, org.civictracker.records.domain.PreparationData.Selection.class);
          var after =
              json.readValue(
                  input, org.civictracker.records.domain.PreparationData.Selection.class);
          var oldAcquisition =
              json.readValue(
                  required(before.acquisitionOperationId()).inputJson, Acquisition.class);
          var newAcquisition =
              json.readValue(required(after.acquisitionOperationId()).inputJson, Acquisition.class);
          if (!oldAcquisition.meetingId().equals(newAcquisition.meetingId())
              || !Objects.equals(before.runId(), after.runId())) {
            throw new OperationProblem(409, "REPLACEMENT_WORK_CONFLICT");
          }
        }
      }
      RecordOperation created = new RecordOperation();
      created.id = id;
      created.kind = kind;
      created.state = State.ACCEPTED;
      created.inputJson = input;
      created.acceptedAt = Instant.now();
      created.replacesId = replaces;
      entities.persist(created);
      entities.flush();
      event(created, replaces == null ? "ACCEPTED" : "REPLACEMENT_ACCEPTED");
      return view(created);
    });
  }

  public OperationViews.Status status(UUID id) {
    return transactions.execute(status -> view(required(id)));
  }

  public String sourceId(UUID discoveryId) {
    return transactions.execute(status ->
        json.readValue(requireSuccess(discoveryId, Kind.DISCOVERY).inputJson, Selection.class)
            .sourceId());
  }

  public RecordOperation claim() {
    return transactions.execute(status -> {
      lockSlot();
      UUID active =
          jdbc.queryForObject(
              "select operation_id from records.operation_slot where id=1",
              (row, n) -> row.getObject(1, UUID.class));
      if (active != null) {
        return null;
      }
      List<RecordOperation> pending =
          entities
              .createQuery(
                  "from RecordOperation where state=:s order by acceptedAt,id",
                  RecordOperation.class)
              .setParameter("s", State.ACCEPTED)
              .setMaxResults(1)
              .getResultList();
      if (pending.isEmpty()) {
        return null;
      }
      RecordOperation operation = pending.getFirst();
      operation.state = State.RUNNING;
      operation.startedAt = Instant.now();
      operation.attemptId = UUID.randomUUID();
      operation.workerId = workerId;
      operation.attempts++;
      jdbc.update(
          "update records.operation_slot "
              + "set operation_id=?,attempt_id=?,worker_id=? where id=1",
          operation.id,
          operation.attemptId,
          operation.workerId);
      event(operation, "CLAIMED");
      return operation;
    });
  }

  /** Guards every checkpoint against a replaced execution attempt. */
  public void guard(UUID id, UUID attempt) {
    lockSlot();
    int count =
        jdbc.queryForObject(
            "select count(*) from records.operation_slot "
                + "where id=1 and operation_id=? and attempt_id=?",
            Integer.class,
            id,
            attempt);
    RecordOperation operation = required(id);
    if (count != 1 || operation.state != State.RUNNING || !attempt.equals(operation.attemptId)) {
      throw new OperationProblem(409, "STALE_EXECUTION_ATTEMPT");
    }
  }

  public void complete(UUID id, UUID attempt, String failure) {
    transactions.executeWithoutResult(status -> {
      guard(id, attempt);
      RecordOperation operation = required(id);
      operation.state = failure == null ? State.SUCCEEDED : State.FAILED;
      operation.errorCode = failure;
      operation.completedAt = Instant.now();
      event(operation, failure);
      jdbc.update(
          "update records.operation_slot "
              + "set operation_id=null,attempt_id=null,worker_id=null where id=1");
    });
  }

  public void park(UUID id, UUID attempt, UUID execution) {
    transactions.executeWithoutResult(status -> {
      guard(id, attempt);
      RecordOperation operation = required(id);
      operation.blockedExecutionId = execution;
      note(id, "OCR_EXECUTION_UNRESOLVED");
      event(operation, "OCR_EXECUTION_UNRESOLVED");
    });
  }

  public OperationViews.Status resume(UUID id, OperationViews.Resume confirmation) {
    return transactions.execute(status -> {
      lockSlot();
      RecordOperation operation = required(id);
      if (operation.kind == Kind.PREPARATION && operation.blockedExecutionId == null) {
        operation.blockedExecutionId = unresolvedOcr(id);
      }
      if (operation.blockedExecutionId != null
          && (!operation.blockedExecutionId.equals(confirmation.ocrExecutionId())
              || !confirmation.previousOcrStopped())) {
        throw new OperationProblem(409, "OCR_RECONCILIATION_REQUIRED");
      }
      if ((operation.state != State.RUNNING && operation.state != State.FAILED)
          || !confirmation.previousWorkerStopped()
          || !Objects.equals(confirmation.attemptId(), operation.attemptId)
          || !Objects.equals(confirmation.previousWorkerId(), operation.workerId)
          || (operation.state == State.RUNNING && operation.workerId.equals(workerId))) {
        throw new OperationProblem(409, "EXECUTION_RECONCILIATION_REQUIRED");
      }
      UUID active =
          jdbc.queryForObject(
              "select operation_id from records.operation_slot where id=1",
              (row, n) -> row.getObject(1, UUID.class));
      if (active != null && !active.equals(id)) {
        throw new OperationProblem(409, "ANOTHER_OPERATION_ACTIVE");
      }
      operation.state = State.ACCEPTED;
      if (operation.blockedExecutionId != null) {
        jdbc.update(
            "update records.preparation_ocr set state='STOP_CONFIRMED' "
                + "where id=? and state='STARTED'",
            operation.blockedExecutionId);
        operation.blockedExecutionId = null;
        jdbc.update(
            "delete from records.operation_note where operation_id=? "
                + "and code='OCR_EXECUTION_UNRESOLVED'",
            id);
      }
      operation.completedAt = null;
      operation.errorCode = null;
      event(operation, "MAINTAINER_CONFIRMED_PREVIOUS_WORKER_STOPPED");
      jdbc.update(
          "update records.operation_slot "
              + "set operation_id=null,attempt_id=null,worker_id=null where id=1");
      return view(operation);
    });
  }

  public <T> T transaction(java.util.function.Supplier<T> work) {
    return transactions.execute(status -> work.get());
  }

  public RecordOperation required(UUID id) {
    RecordOperation operation = entities.find(RecordOperation.class, id);
    if (operation == null) {
      throw new OperationProblem(404, "OPERATION_NOT_FOUND");
    }
    return operation;
  }

  public RecordOperation requireSuccess(UUID id, Kind kind) {
    RecordOperation operation = required(id);
    if (operation.kind != kind || operation.state != State.SUCCEEDED) {
      throw new OperationProblem(409, "OPERATION_NOT_SUCCEEDED");
    }
    return operation;
  }

  public DiscoveredMeeting selectedMeeting(UUID discoveryId, UUID meetingId) {
    return entities
        .createQuery(
            "from DiscoveredMeeting where operationId=:o and meetingId=:m", DiscoveredMeeting.class)
        .setParameter("o", discoveryId)
        .setParameter("m", meetingId)
        .getResultStream()
        .findFirst()
        .orElseThrow(() -> new OperationProblem(404, "MEETING_SELECTION_NOT_FOUND"));
  }

  public OperationViews.Page<OperationViews.Discovered> meetings(UUID id, int page, int size) {
    int offset = offset(page, size);
    return transactions.execute(status -> {
      requireSuccess(id, Kind.DISCOVERY);
      var rows =
          entities
              .createQuery(
                  "from DiscoveredMeeting where operationId=:o order by ordinal",
                  DiscoveredMeeting.class)
              .setParameter("o", id)
              .setFirstResult(offset)
              .setMaxResults(size + 1)
              .getResultList();
      return new OperationViews.Page<>(
          rows.stream()
              .limit(size)
              .map(row ->
                  new OperationViews.Discovered(
                      row.meetingId, json.readValue(row.payload, Meeting.class), row.observedAt))
              .toList(),
          page,
          size,
          rows.size() > size);
    });
  }

  public OperationViews.Page<OperationViews.Original> originals(UUID id, int page, int size) {
    int offset = offset(page, size);
    return transactions.execute(status -> {
      requireSuccess(id, Kind.ACQUISITION);
      var rows =
          entities
              .createQuery(
                  "from AcquisitionItem where operationId=:o order by ordinal",
                  AcquisitionItem.class)
              .setParameter("o", id)
              .setFirstResult(offset)
              .setMaxResults(size + 1)
              .getResultList();
      return new OperationViews.Page<>(
          rows.stream()
              .limit(size)
              .map(row ->
                  new OperationViews.Original(
                      row.id,
                      json.readValue(row.targetJson, Target.class),
                      new Receipt(
                          row.documentId,
                          row.versionId,
                          row.artifactId,
                          row.observationId,
                          row.currentVersionId,
                          row.sha256,
                          row.byteLength,
                          row.currentChanged)))
              .toList(),
          page,
          size,
          rows.size() > size);
    });
  }

  public int offset(int page, int size) {
    if (page < 0 || size < 1 || size > maximumPageSize || (long) page * size > Integer.MAX_VALUE) {
      throw new OperationProblem(400, "INVALID_PAGE");
    }
    return page * size;
  }

  public Optional<Meeting> previousMeeting(UUID meetingId) {
    var rows =
        jdbc.queryForList(
            "select d.payload from records.record_operation o "
                + "join records.discovered_meeting d on d.operation_id="
                + "(o.input_json::jsonb->>'discoveryId')::uuid "
                + "and d.meeting_id=(o.input_json::jsonb->>'meetingId')::uuid "
                + "where o.kind='ACQUISITION' and o.state='SUCCEEDED' "
                + "and d.meeting_id=? order by o.completed_at desc,o.id desc limit 1",
            String.class,
            meetingId);
    return rows.stream().findFirst().map(payload -> json.readValue(payload, Meeting.class));
  }

  public void note(UUID id, String code) {
    jdbc.update(
        "insert into records.operation_note(operation_id,code) values (?,?) "
            + "on conflict do nothing",
        id,
        code);
  }

  private OperationViews.Status view(RecordOperation operation) {
    List<String> notes =
        jdbc.queryForList(
            "select code from records.operation_note where operation_id=? order by code",
            String.class,
            operation.id);
    String result =
        operation.state != State.SUCCEEDED
            ? null
            : "/internal/v1/operations/"
                + operation.id
                + switch (operation.kind) {
                  case DISCOVERY -> "/meetings";
                  case ACQUISITION -> "/originals";
                  case PREPARATION -> "/preparations";
                };
    return new OperationViews.Status(
        operation.id,
        operation.kind,
        operation.state,
        operation.acceptedAt,
        operation.startedAt,
        operation.completedAt,
        operation.attemptId,
        operation.workerId,
        operation.attempts,
        operation.errorCode,
        operation.replacesId,
        result,
        notes,
        operation.blockedExecutionId == null && operation.kind == Kind.PREPARATION
            ? unresolvedOcr(operation.id)
            : operation.blockedExecutionId,
        operation.preparingVersionId == null
            ? null
            : new OperationViews.PreparationProgress(
                operation.preparingVersionId, operation.preparingUnitOrdinal));
  }

  private void lockSlot() {
    jdbc.queryForList("select id from records.operation_slot where id=1 for update");
  }

  private UUID unresolvedOcr(UUID operationId) {
    var executions =
        jdbc.queryForList(
            "select id from records.preparation_ocr where operation_id=? "
                + "and state='STARTED' order by id limit 1",
            UUID.class,
            operationId);
    return executions.isEmpty() ? null : executions.getFirst();
  }

  private void event(RecordOperation operation, String detail) {
    jdbc.update(
        "insert into records.operation_event"
            + " (id,operation_id,state,attempt_id,worker_id,recorded_at,detail) values"
            + " (?,?,?,?,?,?,?)",
        UUID.randomUUID(),
        operation.id,
        operation.state.name(),
        operation.attemptId,
        operation.workerId,
        java.sql.Timestamp.from(Instant.now()),
        detail);
  }
}
