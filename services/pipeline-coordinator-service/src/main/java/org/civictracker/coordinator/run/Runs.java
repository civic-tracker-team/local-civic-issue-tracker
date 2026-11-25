package org.civictracker.coordinator.run;

import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import jakarta.persistence.OptimisticLockException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.civictracker.coordinator.config.CoordinatorRuntime;
import org.civictracker.coordinator.config.CoordinatorSettings;
import org.civictracker.coordinator.owners.AnalysisClient;
import org.civictracker.coordinator.owners.OwnerRejected;
import org.civictracker.coordinator.owners.OwnerStatus;
import org.civictracker.coordinator.owners.OwnerUnreachable;
import org.civictracker.coordinator.persistence.CurrentPublicationEntity;
import org.civictracker.coordinator.persistence.PublicationEntity;
import org.civictracker.coordinator.persistence.PublicationMeetingEntity;
import org.civictracker.coordinator.persistence.PublicationRangeEntity;
import org.civictracker.coordinator.persistence.PublicationSourceEntity;
import org.civictracker.coordinator.persistence.RunEntity;
import org.civictracker.coordinator.persistence.RunEventEntity;
import org.civictracker.coordinator.persistence.RunInputEntity;
import org.civictracker.coordinator.persistence.RunMeetingEntity;
import org.civictracker.coordinator.persistence.RunSlotEntity;
import org.civictracker.coordinator.publication.Manifest;
import org.civictracker.coordinator.publication.PublishedMeeting;
import org.civictracker.coordinator.publication.SourceVersion;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

/** Owns durable run transitions. Owner HTTP calls stay outside SQL transactions. */
@Service
public class Runs {
  private final EntityManager entities;
  private final TransactionTemplate transactions;
  private final ObjectMapper json;
  private final CoordinatorSettings settings;
  private final CoordinatorRuntime runtime;
  private final Clock clock;

  public Runs(
      EntityManager entities,
      PlatformTransactionManager manager,
      ObjectMapper json,
      CoordinatorSettings settings,
      CoordinatorRuntime runtime,
      Clock clock) {
    this.entities = entities;
    transactions = new TransactionTemplate(manager);
    this.json = json;
    this.settings = settings;
    this.runtime = runtime;
    this.clock = clock;
  }

  public RunView start(StartRun input, String maintainer) {
    StartRun command = validated(input, maintainer);
    String hash = sha256(json.writeValueAsString(command));
    return transactions.execute(tx -> {
      var slot = entities.find(RunSlotEntity.class, 1, LockModeType.PESSIMISTIC_WRITE);
      var existing = entities.find(RunEntity.class, command.runId());
      if (existing != null) {
        if (!existing.commandSha256.equals(hash)) {
          throw new RunProblem(409, "RUN_ID_CONFLICT");
        }
        return view(existing);
      }
      if (slot.runId != null) {
        throw new RunProblem(409, "RUN_ACTIVE", slot.runId);
      }
      try {
        runtime.validate();
      } catch (IllegalArgumentException unavailable) {
        throw new RunProblem(503, "COORDINATOR_NOT_CONFIGURED");
      }
      UUID baseId = entities.find(CurrentPublicationEntity.class, 1).publicationId;
      try {
        RunPlan.rejectOverlap(command, base(baseId));
      } catch (PlanRejected rejected) {
        throw new RunProblem(409, rejected.code);
      }
      var r = new RunEntity();
      r.id = command.runId();
      r.commandSha256 = hash;
      r.sourceId = command.sourceId();
      r.rangeFrom = command.from();
      r.rangeTo = command.to();
      r.committeeIds = json.writeValueAsString(command.committeeIds());
      r.maxMeetings = settings.maxMeetings();
      r.preparationProfileId = runtime.preparationProfileId();
      r.basePublicationId = baseId;
      r.state = RunState.ACTIVE;
      r.acceptedBy = maintainer;
      r.acceptedAt = clock.instant();
      entities.persist(r);
      slot.runId = r.id;
      event(r, RunEventEntity.Kind.STARTED, maintainer, null);
      entities.flush();
      return view(r);
    });
  }

  public RunView view(UUID id) {
    return transactions.execute(tx -> view(required(id)));
  }

  public Optional<RunView> active() {
    return transactions.execute(tx -> {
      UUID id = entities.find(RunSlotEntity.class, 1).runId;
      return id == null ? Optional.empty() : Optional.of(view(required(id)));
    });
  }

  public RunView retry(UUID id, int haltId, String maintainer) {
    if (haltId < 1 || maintainer == null || maintainer.isBlank()) {
      throw new RunProblem(400, "INVALID_RETRY");
    }
    try {
      return transactions.execute(tx -> {
        var r = required(id);
        if (r.state != RunState.HALTED || r.haltId != haltId) {
          return view(r);
        }
        if (r.haltKind == StepKind.PUBLISH) {
          r.state = RunState.READY;
        } else if (r.haltKind == StepKind.DISCOVER) {
          if (r.discovered) {
            // Saved meetings and later owner commands name the current discovery attempt.
            throw new RunProblem(409, "RUN_NOT_RETRYABLE");
          }
          r.discoveryAttempt++;
        } else {
          var m = meeting(id, r.haltOrdinal);
          switch (r.haltKind) {
            case ACQUIRE -> m.acquireAttempt++;
            case PREPARE -> m.prepareAttempt++;
            case ANALYZE -> {
              m.analyzeAttempt++;
              m.frozenRequestJson = null;
              m.frozenRequestSha256 = null;
            }
            default -> throw new RunProblem(409, "RUN_NOT_RETRYABLE");
          }
        }
        if (r.haltKind != StepKind.PUBLISH) {
          r.state = RunState.ACTIVE;
        }
        r.haltKind = null;
        r.haltOrdinal = null;
        r.haltOperationId = null;
        r.haltCode = null;
        r.haltedAt = null;
        clearObservation(r);
        event(r, RunEventEntity.Kind.RETRIED, maintainer, null);
        entities.flush();
        return view(r);
      });
    } catch (OptimisticLockingFailureException | OptimisticLockException changed) {
      return view(id);
    }
  }

  public RunView skip(UUID id, int haltId, String reason, String maintainer) {
    if (haltId < 1
        || reason == null
        || reason.isBlank()
        || maintainer == null
        || maintainer.isBlank()) {
      throw new RunProblem(400, "INVALID_SKIP");
    }
    try {
      return transactions.execute(tx -> {
        var r = required(id);
        if (r.state != RunState.HALTED || r.haltId != haltId) {
          return view(r);
        }
        if (r.haltOrdinal == null || r.haltKind == StepKind.PUBLISH) {
          throw new RunProblem(409, "RUN_NOT_SKIPPABLE");
        }
        var m = meeting(id, r.haltOrdinal);
        m.progress = ProgressState.SKIPPED;
        m.revisionId = null;
        m.noInputReason = null;
        m.skipReason = reason;
        m.skippedBy = maintainer;
        m.skippedAt = clock.instant();
        r.state = RunState.ACTIVE;
        r.haltCode = null;
        r.haltKind = null;
        r.haltOrdinal = null;
        r.haltOperationId = null;
        r.haltedAt = null;
        clearObservation(r);
        event(r, RunEventEntity.Kind.SKIPPED, maintainer, reason);
        entities.flush();
        return view(r);
      });
    } catch (OptimisticLockingFailureException | OptimisticLockException changed) {
      return view(id);
    }
  }

  public RunView abandon(UUID id, String reason, String maintainer) {
    if (reason == null || reason.isBlank() || maintainer == null || maintainer.isBlank()) {
      throw new RunProblem(400, "INVALID_ABANDON");
    }
    try {
      return transactions.execute(tx -> {
        var r = required(id);
        if (r.state == RunState.ABANDONED) {
          return view(r);
        }
        if (r.state == RunState.PUBLISHED) {
          throw new RunProblem(409, "RUN_ALREADY_PUBLISHED");
        }
        var slot = entities.find(RunSlotEntity.class, 1, LockModeType.PESSIMISTIC_WRITE);
        r.state = RunState.ABANDONED;
        r.abandonReason = reason;
        r.completedAt = clock.instant();
        r.haltCode = null;
        r.haltKind = null;
        r.haltOrdinal = null;
        r.haltOperationId = null;
        r.haltedAt = null;
        clearObservation(r);
        if (r.id.equals(slot.runId)) {
          slot.runId = null;
        }
        event(r, RunEventEntity.Kind.ABANDONED, maintainer, reason);
        entities.flush();
        return view(r);
      });
    } catch (OptimisticLockingFailureException | OptimisticLockException changed) {
      // A concurrent driver may have advanced a fact. The caller can repeat this command.
      throw new RunProblem(409, "RUN_CHANGED");
    }
  }

  /** Re-derives one cursor after every committed fact. */
  public void tick() {
    try {
      publishReadyAutomatically();
      for (int turn = 0; turn < 20; turn++) {
        var snapshot = transactions.execute(tx -> activeSnapshot());
        if (snapshot == null) {
          return;
        }
        Step step = RunPlan.next(snapshot.facts);
        if (step instanceof Step.Publish) {
          transactions.executeWithoutResult(tx -> {
            var r = writable(snapshot);
            if (r != null) {
              r.state = RunState.READY;
              clearObservation(r);
            }
          });
          publishReadyAutomatically();
          return;
        }
        UUID operation = operation(snapshot.facts, step);
        if (step instanceof Step.Analyze analyze && analyze.frozenRequest() == null) {
          freeze(snapshot, analyze, operation);
          continue;
        }
        try {
          var status =
              step.kind() == StepKind.ANALYZE
                  ? runtime.analysis().operation(operation)
                  : runtime.records().operation(operation);
          if (status.isEmpty()) {
            submit(snapshot.facts, step, operation);
            observe(snapshot, operation, ObservedState.ACCEPTED, null);
            return;
          }
          var owner = status.orElseThrow();
          switch (owner.state()) {
            case ACCEPTED, RUNNING -> {
              observe(
                  snapshot,
                  operation,
                  ObservedState.valueOf(owner.state().name()),
                  owner.blockedId());
              return;
            }
            case FAILED -> {
              halt(
                  snapshot,
                  step,
                  owner.errorCode() == null ? "OWNER_FAILED" : owner.errorCode(),
                  ObservedState.FAILED,
                  owner.blockedId());
              return;
            }
            case SUCCEEDED ->
                record(snapshot, step, result(snapshot.facts, step, operation, owner));
          }
        } catch (OwnerRejected rejected) {
          halt(snapshot, step, "SUBMIT_REJECTED:" + rejected.code);
          return;
        } catch (OwnerUnreachable unreachable) {
          observe(snapshot, operation, ObservedState.UNREACHABLE, null);
          return;
        } catch (PlanRejected rejected) {
          halt(snapshot, step, rejected.code, ObservedState.SUCCEEDED, null);
          return;
        }
      }
    } catch (OptimisticLockingFailureException | OptimisticLockException changed) {
      // The next tick reloads the winning transaction's facts.
    }
  }

  /** Checks owner revisions before the atomic publication transaction. */
  public RunView publish(UUID id) {
    var snapshot =
        transactions.execute(tx -> {
          var r = required(id);
          if (r.state != RunState.READY && r.state != RunState.PUBLISHED) {
            throw new RunProblem(409, "RUN_NOT_READY");
          }
          return new Snapshot(r.version, facts(r));
        });
    if (view(id).state() == RunState.PUBLISHED) {
      return view(id);
    }
    Manifest manifest = RunPlan.manifest(snapshot.facts);
    boolean valid = true;
    try {
      for (var m : snapshot.facts.meetings().orElseThrow()) {
        if (m.progress() instanceof Progress.Analyzed a) {
          var header = runtime.analysis().revision(a.revisionId());
          var versions =
              a.pins().stream().map(Pin::versionId).collect(java.util.stream.Collectors.toSet());
          if (header.isEmpty()
              || !header.get().meetingId().equals(m.meeting().meetingId())
              || !Objects.equals(header.get().meetingDate(), m.meeting().date())
              || !versions.containsAll(header.get().evidenceVersionIds())) {
            valid = false;
            break;
          }
        }
      }
    } catch (OwnerRejected rejected) {
      valid = false;
    } catch (OwnerUnreachable unavailable) {
      throw new RunProblem(503, "PUBLICATION_CHECK_UNAVAILABLE");
    }
    if (!valid) {
      publicationHalt(snapshot, "PUBLISH_CHECK_FAILED");
      return view(id);
    }
    try {
      transactions.executeWithoutResult(tx -> {
        var slot = entities.find(RunSlotEntity.class, 1, LockModeType.PESSIMISTIC_WRITE);
        var r = entities.find(RunEntity.class, id, LockModeType.PESSIMISTIC_WRITE);
        if (r.state == RunState.PUBLISHED) {
          return;
        }
        if (r.state != RunState.READY || r.version != snapshot.version) {
          throw new RunProblem(409, "RUN_CHANGED");
        }
        var publication = new PublicationEntity();
        publication.id = manifest.publicationId();
        publication.runId = id;
        publication.previousId = manifest.previousPublicationId();
        publication.publishedAt = clock.instant();
        entities.persist(publication);
        entities.flush();
        for (var range : manifest.coverage()) {
          var row = new PublicationRangeEntity();
          row.id = new PublicationRangeEntity.Key(publication.id, range.from());
          row.rangeTo = range.to();
          entities.persist(row);
        }
        for (var meeting : manifest.meetings()) {
          var row = new PublicationMeetingEntity();
          row.id = new PublicationMeetingEntity.Key(publication.id, meeting.meetingId());
          row.officialId = meeting.officialId();
          row.meetingDate = meeting.date();
          row.title = meeting.title();
          row.disposition = meeting.disposition();
          row.revisionId = meeting.revisionId();
          row.skipReason = meeting.skipReason();
          entities.persist(row);
          for (var source : meeting.sources()) {
            var value = new PublicationSourceEntity();
            value.id =
                new PublicationSourceEntity.Key(
                    publication.id, meeting.meetingId(), source.versionId());
            value.documentId = source.documentId();
            entities.persist(value);
          }
        }
        entities.flush();
        int changed =
            entities
                .createNativeQuery(
                    "update coordinator.current_publication set publication_id=:new where id=1 and"
                        + " publication_id is not distinct from :base")
                .setParameter("new", publication.id)
                .setParameter("base", r.basePublicationId)
                .executeUpdate();
        if (changed != 1) {
          throw new PointerMoved();
        }
        r.state = RunState.PUBLISHED;
        r.completedAt = clock.instant();
        clearObservation(r);
        if (id.equals(slot.runId)) {
          slot.runId = null;
        }
        event(r, RunEventEntity.Kind.PUBLISHED, null, null);
      });
    } catch (PointerMoved moved) {
      publicationHalt(snapshot, "POINTER_MOVED");
    }
    return view(id);
  }

  private void publicationHalt(Snapshot snapshot, String code) {
    transactions.executeWithoutResult(tx -> {
      var r = required(snapshot.facts.runId());
      if (r.state == RunState.READY && r.version == snapshot.version) {
        doHalt(r, new Step.Publish(RunPlan.manifest(snapshot.facts)), code);
      }
    });
  }

  private void publishReadyAutomatically() {
    if (!runtime.automaticPublish()) {
      return;
    }
    UUID id =
        transactions.execute(tx -> {
          var slot = entities.find(RunSlotEntity.class, 1);
          return slot.runId != null && required(slot.runId).state == RunState.READY
              ? slot.runId
              : null;
        });
    if (id != null) {
      publish(id);
    }
  }

  private static final class PointerMoved extends RuntimeException {}

  private void submit(RunFacts facts, Step step, UUID id) throws OwnerRejected, OwnerUnreachable {
    UUID replaces =
        attempt(step) > 1
            ? RunPlan.operationId(facts.runId(), step.kind(), meetingId(step), attempt(step) - 1)
            : null;
    switch (step) {
      case Step.Discover d -> runtime.records().discover(id, facts.command(), replaces);
      case Step.Acquire a ->
          runtime
              .records()
              .acquire(
                  id,
                  RunPlan.operationId(
                      facts.runId(), StepKind.DISCOVER, null, facts.discoveryAttempt()),
                  a.meeting().meetingId(),
                  facts.runId(),
                  replaces);
      case Step.Prepare p ->
          runtime
              .records()
              .prepare(
                  id,
                  RunPlan.operationId(
                      facts.runId(),
                      StepKind.ACQUIRE,
                      p.meeting().meetingId(),
                      p.acquiredAttempt()),
                  facts.runId(),
                  facts.preparationProfileId(),
                  replaces);
      case Step.Analyze a -> runtime.analysis().analyze(Objects.requireNonNull(a.frozenRequest()));
      default -> throw new IllegalStateException("Publication is not an owner command");
    }
  }

  private Fact result(RunFacts facts, Step step, UUID id, OwnerStatus status)
      throws OwnerRejected, OwnerUnreachable, PlanRejected {
    return switch (step) {
      case Step.Discover d ->
          new DiscoveryFact(
              RunPlan.plan(facts.command(), facts.maxMeetings(), runtime.records().meetings(id)));
      case Step.Acquire a ->
          new AcquisitionFact(
              RunPlan.afterAcquisition(a.meeting(), runtime.records().originals(id)));
      case Step.Prepare p ->
          new PreparationFact(
              RunPlan.pins(
                  runtime
                      .records()
                      .originals(
                          RunPlan.operationId(
                              facts.runId(),
                              StepKind.ACQUIRE,
                              p.meeting().meetingId(),
                              p.acquiredAttempt())),
                  runtime.records().preparations(id)));
      case Step.Analyze a -> {
        if (status.revisionId() == null) {
          throw new PlanRejected("FACT_CONFLICT");
        }
        yield new AnalysisFact(status.revisionId());
      }
      default -> throw new IllegalStateException("Publication is not an owner result");
    };
  }

  private void freeze(Snapshot snapshot, Step.Analyze step, UUID operation) {
    var frozen = AnalysisClient.freeze(operation, step.selection(), step.priorRevisionIds());
    transactions.executeWithoutResult(tx -> {
      var r = writable(snapshot);
      if (r == null) {
        return;
      }
      var m = meeting(r.id, step.meeting().ordinal());
      if (m.frozenRequestJson != null) {
        if (!m.frozenRequestJson.equals(frozen.json())
            || !m.frozenRequestSha256.equals(frozen.sha256())) {
          doHalt(r, step, "FACT_CONFLICT");
        }
        return;
      }
      m.frozenRequestJson = frozen.json();
      m.frozenRequestSha256 = frozen.sha256();
      r.observedOperationId = operation;
      r.observedState = null;
      r.observedBlockedId = null;
      r.observedAt = clock.instant();
    });
  }

  private void record(Snapshot snapshot, Step step, Fact fact) {
    transactions.executeWithoutResult(tx -> {
      var r = required(snapshot.facts.runId());
      if (r.state != RunState.ACTIVE || !sameAttempt(r, step)) {
        return;
      }
      Boolean same = sameFact(r, step, fact);
      if (same != null) {
        if (!same) {
          updateObservation(r, operation(snapshot.facts, step), ObservedState.SUCCEEDED, null);
          doHalt(r, step, "FACT_CONFLICT");
        }
        return;
      }
      if (r.version != snapshot.version) {
        return;
      }
      // Win the run version before Hibernate can flush any child inserts.
      updateObservation(r, operation(snapshot.facts, step), ObservedState.SUCCEEDED, null);
      entities.flush();
      switch (fact) {
        case DiscoveryFact discovery -> {
          for (var planned : discovery.meetings) {
            var m = new RunMeetingEntity();
            m.id = new RunMeetingEntity.Key(r.id, planned.ordinal());
            m.meetingId = planned.meetingId();
            m.officialId = planned.officialId();
            m.meetingDate = planned.date();
            m.title = planned.title();
            m.cancellationEvidence = planned.cancellationEvidence();
            m.progress = ProgressState.DISCOVERED;
            entities.persist(m);
          }
          r.discovered = true;
        }
        case AcquisitionFact acquisition -> {
          var m = meeting(r.id, ordinal(step));
          if (acquisition.progress instanceof Progress.NoInputs none) {
            m.progress = ProgressState.NO_INPUTS;
            m.noInputReason = none.reason();
          } else {
            m.progress = ProgressState.ACQUIRED;
          }
        }
        case PreparationFact preparation -> {
          var m = meeting(r.id, ordinal(step));
          for (int n = 0; n < preparation.pins.size(); n++) {
            var p = preparation.pins.get(n);
            var row = new RunInputEntity();
            row.id = new RunInputEntity.Key(r.id, m.id.ordinal(), p.acquisitionItemId());
            row.inputOrder = n;
            row.documentId = p.documentId();
            row.versionId = p.versionId();
            row.artifactId = p.artifactId();
            row.sha256 = p.sha256();
            row.byteLength = p.byteLength();
            row.preparationId = p.preparationId();
            row.profileFingerprint = p.profileFingerprint();
            entities.persist(row);
          }
          m.progress = ProgressState.PREPARED;
        }
        case AnalysisFact analysis -> {
          var m = meeting(r.id, ordinal(step));
          m.progress = ProgressState.ANALYZED;
          m.revisionId = analysis.revisionId;
        }
      }
    });
  }

  private Boolean sameFact(RunEntity r, Step step, Fact fact) {
    if (fact instanceof DiscoveryFact d) {
      return r.discovered
          ? facts(r).meetings().orElseThrow().stream()
              .map(MeetingFacts::meeting)
              .toList()
              .equals(d.meetings)
          : null;
    }
    var m = meeting(r.id, ordinal(step));
    return switch (fact) {
      case AcquisitionFact a -> {
        if (m.progress == ProgressState.DISCOVERED) {
          yield null;
        }
        if (a.progress instanceof Progress.NoInputs n) {
          yield m.progress == ProgressState.NO_INPUTS && m.noInputReason == n.reason();
        }
        yield m.progress == ProgressState.ACQUIRED
            || m.progress == ProgressState.PREPARED
            || m.progress == ProgressState.ANALYZED;
      }
      case PreparationFact p ->
          m.progress == ProgressState.PREPARED || m.progress == ProgressState.ANALYZED
              ? pins(r.id, m.id.ordinal()).equals(p.pins)
              : null;
      case AnalysisFact a ->
          m.progress == ProgressState.ANALYZED ? Objects.equals(m.revisionId, a.revisionId) : null;
      default -> throw new IllegalStateException("Unexpected discovery fact");
    };
  }

  private boolean sameAttempt(RunEntity r, Step step) {
    if (step instanceof Step.Discover d) {
      return r.discoveryAttempt == d.attempt();
    }
    var m = meeting(r.id, ordinal(step));
    return switch (step) {
      case Step.Acquire a -> m.acquireAttempt == a.attempt();
      case Step.Prepare p ->
          m.prepareAttempt == p.attempt() && m.acquireAttempt == p.acquiredAttempt();
      case Step.Analyze a -> m.analyzeAttempt == a.attempt();
      default -> false;
    };
  }

  private void observe(Snapshot snapshot, UUID id, ObservedState state, UUID blocked) {
    transactions.executeWithoutResult(tx -> {
      var r = writable(snapshot);
      if (r != null) {
        updateObservation(r, id, state, blocked);
      }
    });
  }

  private void updateObservation(RunEntity r, UUID id, ObservedState state, UUID blocked) {
    if (!Objects.equals(r.observedOperationId, id)
        || r.observedState != state
        || !Objects.equals(r.observedBlockedId, blocked)) {
      r.observedOperationId = id;
      r.observedState = state;
      r.observedBlockedId = blocked;
      r.observedAt = clock.instant();
    }
  }

  private void halt(Snapshot snapshot, Step step, String code) {
    halt(snapshot, step, code, null, null);
  }

  private void halt(
      Snapshot snapshot, Step step, String code, ObservedState observation, UUID blocked) {
    transactions.executeWithoutResult(tx -> {
      var r = writable(snapshot);
      if (r != null) {
        if (observation != null) {
          updateObservation(r, operation(snapshot.facts, step), observation, blocked);
        }
        doHalt(r, step, code);
      }
    });
  }

  private void doHalt(RunEntity r, Step step, String code) {
    r.state = RunState.HALTED;
    r.haltId++;
    r.haltKind = step.kind();
    r.haltOrdinal = ordinal(step);
    r.haltOperationId = step instanceof Step.Publish ? null : operation(facts(r), step);
    r.haltCode = code;
    r.haltedAt = clock.instant();
    event(r, RunEventEntity.Kind.HALTED, null, code);
  }

  private Snapshot activeSnapshot() {
    var slot = entities.find(RunSlotEntity.class, 1);
    if (slot.runId == null) {
      return null;
    }
    var r = required(slot.runId);
    return r.state == RunState.ACTIVE ? new Snapshot(r.version, facts(r)) : null;
  }

  private RunEntity writable(Snapshot snapshot) {
    var r = required(snapshot.facts.runId());
    return r.state == RunState.ACTIVE && r.version == snapshot.version ? r : null;
  }

  private RunView view(RunEntity r) {
    RunFacts facts = facts(r);
    boolean done = r.state == RunState.PUBLISHED || r.state == RunState.ABANDONED;
    Step step = done || r.state == RunState.READY ? null : RunPlan.next(facts);
    StepKind kind = r.state == RunState.HALTED ? r.haltKind : step == null ? null : step.kind();
    Phase phase =
        done
            ? Phase.DONE
            : kind == null
                ? Phase.PUBLISHING
                : switch (kind) {
                  case DISCOVER -> Phase.DISCOVERING;
                  case ACQUIRE, PREPARE -> Phase.PREPARING;
                  case ANALYZE -> Phase.ANALYZING;
                  case PUBLISH -> Phase.PUBLISHING;
                };
    UUID operation =
        r.state == RunState.HALTED
            ? r.haltOperationId
            : step == null || step instanceof Step.Publish ? null : operation(facts, step);
    CurrentStep current = null;
    Waiting waiting = null;
    if (operation != null) {
      boolean matches = operation.equals(r.observedOperationId);
      var url = runtime.statusUrl(kind == StepKind.ANALYZE, operation);
      current =
          new CurrentStep(
              kind,
              r.state == RunState.HALTED ? r.haltOrdinal : ordinal(step),
              operation,
              url,
              matches && r.observedState != null ? r.observedState.name() : null,
              matches ? r.observedBlockedId : null,
              matches ? r.observedAt : null);
      if (r.state == RunState.ACTIVE && matches) {
        if (r.observedState == ObservedState.UNREACHABLE) {
          waiting = new Waiting(WaitingState.OWNER_UNREACHABLE, null, url, r.observedAt);
        } else if (r.observedBlockedId != null) {
          waiting = new Waiting(WaitingState.OWNER_BLOCKED, r.observedBlockedId, url, r.observedAt);
        } else if ((r.observedState == ObservedState.ACCEPTED
                || r.observedState == ObservedState.RUNNING)
            && r.observedAt != null
            && !clock.instant().isBefore(r.observedAt.plus(runtime.stallAfter()))) {
          waiting = new Waiting(WaitingState.STALLED, null, url, r.observedAt);
        }
      }
    }
    var halt =
        r.state == RunState.HALTED
            ? new Halt(
                r.haltId,
                r.haltKind,
                r.haltOrdinal,
                r.haltOperationId,
                r.haltCode,
                RunPlan.hint(r.haltKind, r.haltCode),
                r.haltedAt)
            : null;
    var meetings =
        meetingRows(r.id).stream()
            .map(m ->
                new MeetingView(
                    m.id.ordinal(),
                    m.meetingId,
                    m.officialId,
                    m.meetingDate,
                    m.title,
                    m.progress.name(),
                    m.revisionId,
                    m.skipReason,
                    new Attempts(m.acquireAttempt, m.prepareAttempt, m.analyzeAttempt)))
            .toList();
    return new RunView(
        r.id,
        r.state,
        phase,
        facts.command(),
        r.basePublicationId,
        meetings,
        current,
        halt,
        r.state == RunState.PUBLISHED ? RunPlan.publicationId(r.id) : null,
        waiting);
  }

  private RunFacts facts(RunEntity r) {
    var command =
        new StartRun(
            r.id,
            r.sourceId,
            r.rangeFrom,
            r.rangeTo,
            json.readValue(r.committeeIds, new TypeReference<List<String>>() {}));
    Optional<List<MeetingFacts>> meetings = Optional.empty();
    if (r.discovered) {
      meetings =
          Optional.of(
              meetingRows(r.id).stream()
                  .map(m -> {
                    var pins = pins(r.id, m.id.ordinal());
                    Progress progress =
                        switch (m.progress) {
                          case DISCOVERED -> new Progress.Discovered();
                          case ACQUIRED -> new Progress.Acquired();
                          case PREPARED -> new Progress.Prepared(pins);
                          case ANALYZED -> new Progress.Analyzed(pins, m.revisionId);
                          case NO_INPUTS -> new Progress.NoInputs(m.noInputReason);
                          case SKIPPED ->
                              new Progress.Skipped(m.skipReason, m.skippedBy, m.skippedAt, pins);
                        };
                    var planned =
                        new PlannedMeeting(
                            m.id.ordinal(),
                            m.meetingId,
                            m.officialId,
                            m.meetingDate,
                            m.title,
                            m.cancellationEvidence);
                    var frozen =
                        m.frozenRequestJson == null
                            ? null
                            : AnalysisClient.readFrozen(m.frozenRequestJson, m.frozenRequestSha256);
                    return new MeetingFacts(
                        planned,
                        progress,
                        new Attempts(m.acquireAttempt, m.prepareAttempt, m.analyzeAttempt),
                        frozen);
                  })
                  .toList());
    }
    return new RunFacts(
        r.id,
        command,
        r.maxMeetings,
        r.preparationProfileId,
        base(r.basePublicationId),
        r.discoveryAttempt,
        meetings);
  }

  private PublicationBase base(UUID id) {
    if (id == null) {
      return PublicationBase.NONE;
    }
    var coverage =
        entities
            .createQuery(
                "from PublicationRangeEntity where id.publicationId=:id order by id.rangeFrom",
                PublicationRangeEntity.class)
            .setParameter("id", id)
            .getResultList()
            .stream()
            .map(r -> new DateRange(r.id.rangeFrom(), r.rangeTo))
            .toList();
    var sources =
        entities
            .createQuery(
                "from PublicationSourceEntity where id.publicationId=:id",
                PublicationSourceEntity.class)
            .setParameter("id", id)
            .getResultList();
    var meetings =
        entities
            .createQuery(
                "from PublicationMeetingEntity where id.publicationId=:id order by"
                    + " meetingDate,officialId",
                PublicationMeetingEntity.class)
            .setParameter("id", id)
            .getResultList()
            .stream()
            .map(m ->
                new PublishedMeeting(
                    m.id.meetingId(),
                    m.officialId,
                    m.meetingDate,
                    m.title,
                    m.disposition,
                    m.revisionId,
                    sources.stream()
                        .filter(s -> s.id.meetingId().equals(m.id.meetingId()))
                        .map(s -> new SourceVersion(s.documentId, s.id.versionId()))
                        .sorted(
                            Comparator.comparing(SourceVersion::documentId)
                                .thenComparing(SourceVersion::versionId))
                        .toList(),
                    m.skipReason))
            .toList();
    return new PublicationBase(id, meetings, coverage);
  }

  private List<RunMeetingEntity> meetingRows(UUID id) {
    return entities
        .createQuery(
            "from RunMeetingEntity where id.runId=:id order by id.ordinal", RunMeetingEntity.class)
        .setParameter("id", id)
        .getResultList();
  }

  private List<Pin> pins(UUID id, int ordinal) {
    return entities
        .createQuery(
            "from RunInputEntity where id.runId=:id and id.ordinal=:ordinal order by inputOrder",
            RunInputEntity.class)
        .setParameter("id", id)
        .setParameter("ordinal", ordinal)
        .getResultList()
        .stream()
        .map(p ->
            new Pin(
                p.id.acquisitionItemId(),
                p.documentId,
                p.versionId,
                p.artifactId,
                p.sha256,
                p.byteLength,
                p.preparationId,
                p.profileFingerprint))
        .toList();
  }

  private RunEntity required(UUID id) {
    var r = entities.find(RunEntity.class, id);
    if (r == null) {
      throw new RunProblem(404, "RUN_NOT_FOUND");
    }
    return r;
  }

  private RunMeetingEntity meeting(UUID id, int ordinal) {
    return Objects.requireNonNull(
        entities.find(RunMeetingEntity.class, new RunMeetingEntity.Key(id, ordinal)));
  }

  private void event(RunEntity r, RunEventEntity.Kind kind, String maintainer, String detail) {
    var e = new RunEventEntity();
    e.runId = r.id;
    e.kind = kind;
    e.haltId = r.haltId;
    e.maintainer = maintainer;
    e.detail = detail;
    e.at = clock.instant();
    entities.persist(e);
  }

  private static void clearObservation(RunEntity r) {
    r.observedOperationId = null;
    r.observedState = null;
    r.observedBlockedId = null;
    r.observedAt = null;
  }

  private static UUID operation(RunFacts facts, Step step) {
    return RunPlan.operationId(facts.runId(), step.kind(), meetingId(step), attempt(step));
  }

  private static int attempt(Step step) {
    return switch (step) {
      case Step.Discover d -> d.attempt();
      case Step.Acquire a -> a.attempt();
      case Step.Prepare p -> p.attempt();
      case Step.Analyze a -> a.attempt();
      default -> throw new IllegalArgumentException();
    };
  }

  private static Integer ordinal(Step step) {
    return switch (step) {
      case Step.Acquire a -> a.meeting().ordinal();
      case Step.Prepare p -> p.meeting().ordinal();
      case Step.Analyze a -> a.meeting().ordinal();
      default -> null;
    };
  }

  private static UUID meetingId(Step step) {
    return switch (step) {
      case Step.Acquire a -> a.meeting().meetingId();
      case Step.Prepare p -> p.meeting().meetingId();
      case Step.Analyze a -> a.meeting().meetingId();
      default -> null;
    };
  }

  private static StartRun validated(StartRun command, String maintainer) {
    if (command == null
        || command.runId() == null
        || command.sourceId() == null
        || command.sourceId().isBlank()
        || command.from() == null
        || command.to() == null
        || command.to().isBefore(command.from())
        || command.committeeIds().isEmpty()
        || command.committeeIds().stream().anyMatch(c -> c.isBlank())
        || maintainer == null
        || maintainer.isBlank()) {
      throw new RunProblem(400, "INVALID_RUN_REQUEST");
    }
    return new StartRun(
        command.runId(),
        command.sourceId(),
        command.from(),
        command.to(),
        command.committeeIds().stream().sorted().toList());
  }

  private static String sha256(String value) {
    try {
      return HexFormat.of()
          .formatHex(
              MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
    } catch (NoSuchAlgorithmException impossible) {
      throw new IllegalStateException(impossible);
    }
  }

  private record Snapshot(long version, RunFacts facts) {}

  private sealed interface Fact {}

  private record DiscoveryFact(List<PlannedMeeting> meetings) implements Fact {}

  private record AcquisitionFact(Progress progress) implements Fact {}

  private record PreparationFact(List<Pin> pins) implements Fact {}

  private record AnalysisFact(UUID revisionId) implements Fact {}
}
