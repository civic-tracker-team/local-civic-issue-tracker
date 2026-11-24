package org.civictracker.analysis.persistence;

import jakarta.persistence.EntityManager;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.UUID;
import org.civictracker.analysis.domain.Candidates.DateClaim;
import org.civictracker.analysis.domain.IssueHistory;
import org.civictracker.analysis.domain.SavedFindings;
import org.civictracker.analysis.domain.SavedFindings.EvidenceSpan;
import org.civictracker.analysis.evaluation.EvaluationJson;
import org.springframework.stereotype.Component;
import tools.jackson.core.type.TypeReference;

/** Maps stored rows to the draft T08 read model without changing write semantics. */
@Component
public class FindingsReader {
  private final EntityManager entities;

  public FindingsReader(EntityManager entities) {
    this.entities = entities;
  }

  public SavedFindings read(UUID revisionId) {
    var revision = entities.find(AnalysisRevision.class, revisionId);
    if (revision == null)
      throw new NoSuchElementException("Unknown analysis revision: " + revisionId);
    var spans = new HashMap<UUID, EvidenceSpan>();
    for (var r : rows(EvidenceSpanEntity.class, revisionId)) {
      Map<String, Object> locator =
          EvaluationJson.MAPPER.readValue(
              r.locatorJson, new TypeReference<Map<String, Object>>() {});
      spans.put(
          r.id,
          new EvidenceSpan(
              r.documentId,
              r.versionId,
              r.preparationId,
              r.segmentId,
              r.start,
              r.end,
              r.text,
              r.unitOrdinal,
              r.segmentOrdinal,
              locator));
    }
    var devLinks = new HashMap<UUID, List<EvidenceSpan>>();
    for (var r : rows(DevelopmentEvidence.class, revisionId))
      devLinks
          .computeIfAbsent(r.developmentId, k -> new ArrayList<>())
          .add(spans.get(r.evidenceId));
    var devRows = rows(DevelopmentEntity.class, revisionId);
    var dates = new HashMap<UUID, List<DateClaim>>();
    if (!devRows.isEmpty()) {
      var claims =
          entities
              .createQuery(
                  "from DevelopmentDateEntity where developmentId in :ids order by ordinal",
                  DevelopmentDateEntity.class)
              .setParameter("ids", devRows.stream().map(r -> r.id).toList())
              .getResultList();
      for (var r : claims)
        dates
            .computeIfAbsent(r.developmentId, k -> new ArrayList<>())
            .add(new DateClaim(r.role, r.precision, r.start, r.end, r.approximate));
    }
    var developments =
        devRows.stream()
            .map(r ->
                new SavedFindings.Development(
                    r.id,
                    r.kind,
                    r.description,
                    r.status,
                    dates.getOrDefault(r.id, List.of()),
                    devLinks.getOrDefault(r.id, List.of())))
            .toList();
    var issues = new LinkedHashMap<UUID, SavedFindings.Issue>();
    for (var mention : rows(IssueMention.class, revisionId))
      issues.putIfAbsent(
          mention.issueId,
          new SavedFindings.Issue(mention.issueId, mention.name, mention.priorLabel != null));
    var assocLinks = new HashMap<UUID, List<EvidenceSpan>>();
    for (var r : rows(AssociationEvidence.class, revisionId))
      assocLinks
          .computeIfAbsent(r.associationId, k -> new ArrayList<>())
          .add(spans.get(r.evidenceId));
    var associations =
        rows(AssociationEntity.class, revisionId).stream()
            .map(r ->
                new SavedFindings.Association(
                    r.developmentId, r.issueId, r.basis, assocLinks.getOrDefault(r.id, List.of())))
            .toList();
    return new SavedFindings(
        revision.id,
        revision.meetingId,
        revision.officialId,
        revision.meetingDate,
        revision.title,
        revision.contractRevision,
        revision.profileFingerprint,
        developments,
        List.copyOf(issues.values()),
        associations);
  }

  public IssueHistory issueHistory(UUID issueId) {
    if (entities.find(AnalysisIssue.class, issueId) == null)
      throw new NoSuchElementException("Unknown analysis issue: " + issueId);
    var revisions =
        entities
            .createQuery(
                "from AnalysisRevision r where exists (select m.id from IssueMention m where"
                    + " m.revisionId=r.id and m.issueId=:issue) order by r.meetingDate, r.id",
                AnalysisRevision.class)
            .setParameter("issue", issueId)
            .getResultList();
    var history = new ArrayList<IssueHistory.Revision>();
    SavedFindings.Issue current = null;
    for (var revision : revisions) {
      var mentions =
          rows(IssueMention.class, revision.id).stream()
              .filter(m -> m.issueId.equals(issueId))
              .toList();
      var last = mentions.getLast();
      current = new SavedFindings.Issue(issueId, last.name, last.priorLabel != null);
      var saved = read(revision.id);
      var links = saved.associations().stream().filter(a -> a.issueId().equals(issueId)).toList();
      var ids =
          links.stream()
              .map(SavedFindings.Association::developmentId)
              .collect(java.util.stream.Collectors.toSet());
      history.add(
          new IssueHistory.Revision(
              revision.id,
              revision.meetingId,
              revision.meetingDate,
              mentions.stream().map(m -> m.name).toList(),
              saved.developments().stream().filter(d -> ids.contains(d.id())).toList(),
              links));
    }
    return new IssueHistory(current, history);
  }

  private <T> List<T> rows(Class<T> type, UUID revisionId) {
    return entities
        .createQuery(
            "from " + type.getSimpleName() + " where revisionId=:revision order by ordinal", type)
        .setParameter("revision", revisionId)
        .getResultList();
  }
}
