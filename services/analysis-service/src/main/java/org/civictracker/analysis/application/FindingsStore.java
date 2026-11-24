package org.civictracker.analysis.application;

import jakarta.persistence.EntityManager;
import java.time.Instant;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.UUID;
import org.civictracker.analysis.domain.Candidates;
import org.civictracker.analysis.domain.IssueHistory;
import org.civictracker.analysis.domain.MeetingInput;
import org.civictracker.analysis.domain.SavedFindings;
import org.civictracker.analysis.evaluation.EvaluationJson;
import org.civictracker.analysis.persistence.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Saves complete immutable revisions using the analysis runtime role. */
@Service
public class FindingsStore {
  private final EntityManager entities;
  private final FindingsReader reader;

  public FindingsStore(EntityManager entities, FindingsReader reader) {
    this.entities = entities;
    this.reader = reader;
  }

  @Transactional
  public UUID save(
      MeetingInput input,
      Candidates candidates,
      PriorContext.Snapshot priorContext,
      RevisionMetadata metadata,
      DevelopmentKinds kinds) {
    kinds.requireMatches(candidates);
    if (!input.priorIssues().equals(priorContext.issues()))
      throw new SaveRejected(SaveRejected.Code.PRIOR_CONTEXT_INPUT_MISMATCH);
    var revision = new AnalysisRevision();
    revision.operationId = metadata.operationId();
    revision.origin = metadata.origin();
    revision.meetingId = input.meetingId();
    revision.officialId = input.officialId();
    revision.meetingDate = input.meetingDate();
    revision.title = input.title();
    revision.contractRevision = metadata.contractRevision();
    revision.contractDetailJson = metadata.contractDetailJson();
    revision.profileFingerprint = metadata.profileFingerprint();
    revision.inputSha256 = metadata.inputSha256();
    revision.modelInputSha256 = metadata.modelInputSha256();
    revision.candidatesSha256 = metadata.candidatesSha256();
    revision.receiptSha256 = metadata.receiptSha256();
    revision.promptTokens = metadata.promptTokens();
    revision.artifactKey = metadata.artifactKey();
    revision.createdAt = Instant.now();
    entities.persist(revision);
    entities.flush();

    int contextOrdinal = 0;
    for (var prior : priorContext.issues()) {
      var row = new PriorContextEntry();
      row.revisionId = revision.id;
      row.ordinal = contextOrdinal++;
      row.priorLabel = prior.id();
      row.issueId = required(priorContext.issueIds(), prior.id());
      row.description = prior.description();
      row.reviewedThrough = prior.reviewedThrough();
      row.snapshotSha256 = prior.snapshotSha256();
      entities.persist(row);
    }

    var locations = locations(input);
    var evidence = new LinkedHashMap<String, UUID>();
    int ordinal = 0;
    for (var candidate : candidates.evidence()) {
      var span = candidate.source();
      var location = locations.get(span.segmentId());
      if (location == null)
        throw new SaveRejected(
            SaveRejected.Code.UNKNOWN_EVIDENCE_SEGMENT,
            "Unknown evidence segment: " + span.segmentId());
      if (!location.documentId().equals(span.documentId())
          || !location.versionId().equals(span.versionId())
          || !location.preparationId().equals(span.preparationId()))
        throw new SaveRejected(SaveRejected.Code.EVIDENCE_SOURCE_MISMATCH);
      var row = new EvidenceSpanEntity();
      row.revisionId = revision.id;
      row.localLabel = candidate.id();
      row.ordinal = ordinal++;
      row.documentId = span.documentId();
      row.versionId = span.versionId();
      row.preparationId = span.preparationId();
      row.profileFingerprint = span.profileFingerprint();
      row.segmentId = span.segmentId();
      row.start = span.start();
      row.end = span.end();
      row.text = span.text();
      row.unitOrdinal = location.unitOrdinal();
      row.segmentOrdinal = location.segmentOrdinal();
      row.locatorJson = EvaluationJson.MAPPER.writeValueAsString(location.locator());
      unique(evidence, candidate.id(), row.id);
      entities.persist(row);
    }

    var issues = new LinkedHashMap<String, UUID>();
    ordinal = 0;
    for (var candidate : candidates.issues()) {
      UUID issueId;
      if (candidate.priorIssueId() == null) {
        var issue = new AnalysisIssue();
        issue.firstRevisionId = revision.id;
        issue.firstLabel = candidate.id();
        issueId = issue.id;
        entities.persist(issue);
      } else issueId = required(priorContext.issueIds(), candidate.priorIssueId());
      unique(issues, candidate.id(), issueId);
      var mention = new IssueMention();
      mention.revisionId = revision.id;
      mention.ordinal = ordinal++;
      mention.issueId = issueId;
      mention.localLabel = candidate.id();
      mention.name = candidate.label();
      mention.priorLabel = candidate.priorIssueId();
      entities.persist(mention);
    }

    var developments = new LinkedHashMap<String, UUID>();
    ordinal = 0;
    for (var candidate : candidates.developments()) {
      var row = new DevelopmentEntity();
      row.revisionId = revision.id;
      row.localLabel = candidate.id();
      row.ordinal = ordinal++;
      row.description = candidate.description();
      row.status = candidate.status();
      row.kind = kinds.entries().get(row.ordinal).kind();
      unique(developments, candidate.id(), row.id);
      entities.persist(row);
      int dateOrdinal = 0;
      for (var claim : candidate.dates()) {
        var date = new DevelopmentDateEntity();
        date.developmentId = row.id;
        date.ordinal = dateOrdinal++;
        date.role = claim.role();
        date.precision = claim.precision();
        date.start = claim.start();
        date.end = claim.end();
        date.approximate = claim.approximate();
        entities.persist(date);
      }
      int evidenceOrdinal = 0;
      for (String label : candidate.evidenceIds()) {
        var link = new DevelopmentEvidence();
        link.revisionId = revision.id;
        link.developmentId = row.id;
        link.evidenceId = required(evidence, label);
        link.ordinal = evidenceOrdinal++;
        entities.persist(link);
      }
    }

    var merged = new LinkedHashMap<LinkKey, MergedLink>();
    for (var candidate : candidates.associations()) {
      var key =
          new LinkKey(
              required(developments, candidate.developmentId()),
              required(issues, candidate.issueId()));
      var link = merged.computeIfAbsent(key, ignored -> new MergedLink(candidate.basis()));
      if (link.basis != candidate.basis())
        throw new SaveRejected(SaveRejected.Code.CONFLICTING_LINK_BASIS);
      for (var label : candidate.evidenceIds()) link.evidence.add(required(evidence, label));
    }
    ordinal = 0;
    for (var entry : merged.entrySet()) {
      var row = new AssociationEntity();
      row.revisionId = revision.id;
      row.ordinal = ordinal++;
      row.developmentId = entry.getKey().developmentId();
      row.issueId = entry.getKey().issueId();
      row.basis = entry.getValue().basis;
      entities.persist(row);
      int evidenceOrdinal = 0;
      for (UUID evidenceId : entry.getValue().evidence) {
        var link = new AssociationEvidence();
        link.revisionId = revision.id;
        link.associationId = row.id;
        link.evidenceId = evidenceId;
        link.ordinal = evidenceOrdinal++;
        entities.persist(link);
      }
    }
    entities.flush();
    return revision.id;
  }

  @Transactional(readOnly = true)
  public SavedFindings read(UUID revisionId) {
    return reader.read(revisionId);
  }

  @Transactional(readOnly = true)
  public IssueHistory issueHistory(UUID issueId) {
    return reader.issueHistory(issueId);
  }

  private record LinkKey(UUID developmentId, UUID issueId) {}

  private static class MergedLink {
    final Candidates.Basis basis;
    final LinkedHashSet<UUID> evidence = new LinkedHashSet<>();

    MergedLink(Candidates.Basis basis) {
      this.basis = basis;
    }
  }

  private record Location(
      UUID documentId,
      UUID versionId,
      UUID preparationId,
      int unitOrdinal,
      int segmentOrdinal,
      Map<String, Object> locator) {}

  private static Map<UUID, Location> locations(MeetingInput input) {
    var result = new HashMap<UUID, Location>();
    for (var source : input.sources()) {
      for (var unit : source.units()) {
        int segmentOrdinal = 0;
        for (var segment : unit.segments()) {
          if (!segment.selected()) continue;
          if (result.putIfAbsent(
                  segment.id(),
                  new Location(
                      source.documentId(),
                      source.versionId(),
                      source.preparationId(),
                      unit.ordinal(),
                      segmentOrdinal++,
                      segment.locator()))
              != null)
            throw new SaveRejected(
                SaveRejected.Code.DUPLICATE_INPUT_SEGMENT,
                "Duplicate input segment: " + segment.id());
        }
      }
    }
    return result;
  }

  private static void unique(Map<String, UUID> labels, String label, UUID id) {
    if (label == null || labels.putIfAbsent(label, id) != null)
      throw new SaveRejected(
          SaveRejected.Code.INVALID_LOCAL_LABEL, "Invalid or duplicate local label");
  }

  private static UUID required(Map<String, UUID> labels, String label) {
    var id = labels.get(label);
    if (id == null)
      throw new SaveRejected(
          SaveRejected.Code.UNKNOWN_LOCAL_LABEL, "Unknown local label: " + label);
    return id;
  }
}
