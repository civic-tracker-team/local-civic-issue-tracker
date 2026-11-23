package org.civictracker.analysis.application;

import jakarta.persistence.EntityManager;
import java.time.Instant;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.UUID;
import org.civictracker.analysis.api.AnalysisCommands.*;
import org.civictracker.analysis.domain.MeetingInput;
import org.civictracker.analysis.evaluation.EvaluationJson;
import org.civictracker.analysis.persistence.AnalysisOperation;
import org.civictracker.analysis.persistence.AnalysisOperation.State;
import org.civictracker.analysis.persistence.AnalysisRevision.Origin;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/** Owns acceptance, the durable execution slot and atomic findings completion. */
@Service
public class AnalysisOperations {
  private final EntityManager entities;
  private final JdbcTemplate jdbc;
  private final TransactionTemplate transactions;
  private final PriorContext contexts;
  private final FindingsStore findings;
  public final UUID workerId = UUID.randomUUID();

  public AnalysisOperations(
      EntityManager entities,
      JdbcTemplate jdbc,
      PlatformTransactionManager manager,
      PriorContext contexts,
      FindingsStore findings) {
    this.entities = entities;
    this.jdbc = jdbc;
    this.transactions = new TransactionTemplate(manager);
    this.contexts = contexts;
    this.findings = findings;
  }

  public Status accept(Command command) {
    validate(command);
    String json = EvaluationJson.MAPPER.writeValueAsString(command);
    String hash = EvaluationJson.sha256(json);
    return transactions.execute(tx -> {
      lockSlot();
      var old = entities.find(AnalysisOperation.class, command.id());
      if (old != null) {
        if (!old.commandSha256.equals(hash) || !old.inputJson.equals(json))
          throw new OperationProblem(409, "OPERATION_INPUT_CONFLICT");
        return view(old);
      }
      try {
        contexts.build(command.priorRevisionIds(), command.selection().meetingDate());
      } catch (IllegalArgumentException | NoSuchElementException error) {
        throw new OperationProblem(400, "INVALID_PRIOR_REVISIONS");
      }
      var operation = new AnalysisOperation();
      operation.id = command.id();
      operation.state = State.ACCEPTED;
      operation.inputJson = json;
      operation.commandSha256 = hash;
      operation.acceptedAt = Instant.now();
      entities.persist(operation);
      entities.flush();
      return view(operation);
    });
  }

  public Status status(UUID id) {
    return transactions.execute(tx -> view(required(id)));
  }

  public AnalysisOperation claim() {
    return transactions.execute(tx -> {
      lockSlot();
      if (active() != null) return null;
      // A RUNNING row is never taken over, even if its slot was damaged.
      if (!entities
          .createQuery("from AnalysisOperation where state=:state", AnalysisOperation.class)
          .setParameter("state", State.RUNNING)
          .setMaxResults(1)
          .getResultList()
          .isEmpty()) return null;
      var pending =
          entities
              .createQuery(
                  "from AnalysisOperation where state=:state order by acceptedAt,id",
                  AnalysisOperation.class)
              .setParameter("state", State.ACCEPTED)
              .setMaxResults(1)
              .getResultList();
      if (pending.isEmpty()) return null;
      var row = pending.getFirst();
      row.state = State.RUNNING;
      row.startedAt = Instant.now();
      row.attemptId = UUID.randomUUID();
      row.workerId = workerId;
      row.attempts++;
      jdbc.update(
          "update analysis.operation_slot set operation_id=?,attempt_id=?,worker_id=? where id=1",
          row.id,
          row.attemptId,
          workerId);
      return row;
    });
  }

  public void fail(UUID id, UUID attempt, String code) {
    transactions.executeWithoutResult(tx -> {
      var row = guard(id, attempt);
      row.state = State.FAILED;
      row.errorCode = code;
      row.completedAt = Instant.now();
      release();
    });
  }

  public void park(UUID id, UUID attempt) {
    transactions.executeWithoutResult(tx -> guard(id, attempt).blockedAttemptId = attempt);
  }

  public void succeed(
      UUID id,
      UUID attempt,
      TrialArtifacts trial,
      MeetingInput expected,
      PriorContext.Snapshot context,
      String artifactKey) {
    if (!trial.input().equals(expected))
      throw new SaveRejected(SaveRejected.Code.ATTEMPT_INPUT_CHANGED);
    transactions.executeWithoutResult(tx -> {
      var row = guard(id, attempt);
      row.revisionId =
          findings.save(
              trial.input(),
              trial.candidates(),
              context,
              trial.metadata(Origin.OPERATION, id, artifactKey),
              trial.kinds());
      row.state = State.SUCCEEDED;
      row.completedAt = Instant.now();
      release();
    });
  }

  public Status resume(UUID id, Resume confirmation) {
    return transactions.execute(tx -> {
      lockSlot();
      var row = required(id);
      if (confirmation == null
          || (row.state != State.RUNNING && row.state != State.FAILED)
          || !confirmation.previousWorkerStopped()
          || !Objects.equals(row.attemptId, confirmation.attemptId())
          || !Objects.equals(row.workerId, confirmation.previousWorkerId())
          || (row.state == State.RUNNING
              && row.blockedAttemptId == null
              && workerId.equals(row.workerId)))
        throw new OperationProblem(409, "EXECUTION_RECONCILIATION_REQUIRED");
      if (row.state == State.RUNNING && !confirmation.previousRuntimeStopped())
        throw new OperationProblem(409, "RUNTIME_RECONCILIATION_REQUIRED");
      if (active() != null && !active().equals(id))
        throw new OperationProblem(409, "ANOTHER_OPERATION_ACTIVE");
      row.state = State.ACCEPTED;
      row.blockedAttemptId = null;
      row.completedAt = null;
      row.errorCode = null;
      release();
      return view(row);
    });
  }

  private AnalysisOperation guard(UUID id, UUID attempt) {
    lockSlot();
    var row = required(id);
    int count =
        jdbc.queryForObject(
            "select count(*) from analysis.operation_slot where id=1 and operation_id=? and"
                + " attempt_id=? and worker_id=?",
            Integer.class,
            id,
            attempt,
            workerId);
    if (count != 1
        || row.state != State.RUNNING
        || !attempt.equals(row.attemptId)
        || row.blockedAttemptId != null) throw new OperationProblem(409, "STALE_EXECUTION_ATTEMPT");
    return row;
  }

  private AnalysisOperation required(UUID id) {
    var row = entities.find(AnalysisOperation.class, id);
    if (row == null) throw new OperationProblem(404, "OPERATION_NOT_FOUND");
    return row;
  }

  private void lockSlot() {
    jdbc.queryForObject(
        "select id from analysis.operation_slot where id=1 for update", Integer.class);
  }

  private UUID active() {
    return jdbc.queryForObject(
        "select operation_id from analysis.operation_slot where id=1",
        (rs, n) -> rs.getObject(1, UUID.class));
  }

  private void release() {
    jdbc.update(
        "update analysis.operation_slot set operation_id=null,attempt_id=null,worker_id=null where"
            + " id=1");
  }

  private Status view(AnalysisOperation r) {
    UUID blocked = r.blockedAttemptId;
    if (blocked == null && r.state == State.RUNNING && !workerId.equals(r.workerId))
      blocked = r.attemptId;
    return new Status(
        r.id,
        r.state,
        r.acceptedAt,
        r.startedAt,
        r.completedAt,
        r.attemptId,
        r.workerId,
        blocked,
        r.errorCode,
        r.attempts,
        r.revisionId);
  }

  private static void validate(Command c) {
    if (c == null || c.id() == null || c.selection() == null || c.priorRevisionIds() == null)
      throw new OperationProblem(400, "INVALID_REQUEST");
    var s = c.selection();
    if (s.discoveryId() == null
        || s.acquisitionId() == null
        || s.preparationOperationId() == null
        || s.meetingId() == null
        || s.meetingDate() == null
        || s.title() == null
        || s.officialId() == null
        || s.inputs() == null
        || s.inputs().isEmpty()) throw new OperationProblem(400, "INVALID_REQUEST");
    var ids = new java.util.HashSet<UUID>();
    for (var p : s.inputs()) {
      if (p == null
          || p.acquisitionItemId() == null
          || !ids.add(p.acquisitionItemId())
          || p.documentId() == null
          || p.versionId() == null
          || p.artifactId() == null
          || p.preparationId() == null
          || p.byteLength() < 0
          || p.sha256() == null
          || !p.sha256().matches("[a-f0-9]{64}")
          || p.profileFingerprint() == null
          || p.profileFingerprint().isBlank()) throw new OperationProblem(400, "INVALID_REQUEST");
    }
  }
}
