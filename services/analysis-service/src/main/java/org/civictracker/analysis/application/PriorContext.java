package org.civictracker.analysis.application;

import jakarta.persistence.EntityManager;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.UUID;
import org.civictracker.analysis.domain.MeetingInput;
import org.civictracker.analysis.evaluation.EvaluationJson;
import org.civictracker.analysis.persistence.AnalysisRevision;
import org.civictracker.analysis.persistence.IssueMention;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Builds deterministic model labels from explicitly selected earlier revisions. */
@Service
public class PriorContext {
  private final EntityManager entities;

  public PriorContext(EntityManager entities) {
    this.entities = entities;
  }

  public record Snapshot(List<MeetingInput.PriorIssue> issues, Map<String, UUID> issueIds) {
    public Snapshot {
      issues = List.copyOf(issues);
      issueIds = Map.copyOf(issueIds);
      var labels = issues.stream().map(MeetingInput.PriorIssue::id).toList();
      if (new HashSet<>(labels).size() != labels.size()
          || !issueIds.keySet().equals(new HashSet<>(labels)))
        throw new IllegalArgumentException("PRIOR_CONTEXT_LABEL_MISMATCH");
    }

    public static Snapshot empty() {
      return new Snapshot(List.of(), Map.of());
    }
  }

  private record Entry(String id, UUID issueId, String description, LocalDate reviewedThrough) {}

  @Transactional(readOnly = true)
  public Snapshot build(List<UUID> priorRevisionIds, LocalDate meetingDate) {
    if (new HashSet<>(priorRevisionIds).size() != priorRevisionIds.size())
      throw new IllegalArgumentException("DUPLICATE_PRIOR_REVISION");
    var revisions = new ArrayList<AnalysisRevision>();
    for (UUID id : priorRevisionIds) {
      var revision = entities.find(AnalysisRevision.class, id);
      if (revision == null) throw new NoSuchElementException("Unknown prior revision: " + id);
      if (!revision.meetingDate.isBefore(meetingDate))
        throw new IllegalArgumentException("PRIOR_REVISION_NOT_EARLIER");
      revisions.add(revision);
    }
    revisions.sort(
        Comparator.comparing((AnalysisRevision r) -> r.meetingDate)
            .thenComparing(r -> r.id.toString()));
    var issues = new LinkedHashMap<UUID, Entry>();
    for (var revision : revisions) {
      var mentions =
          entities
              .createQuery(
                  "from IssueMention where revisionId=:id order by ordinal", IssueMention.class)
              .setParameter("id", revision.id)
              .getResultList();
      for (var mention : mentions) {
        var earlier = issues.get(mention.issueId);
        String label = earlier == null ? "p" + (issues.size() + 1) : earlier.id();
        issues.put(
            mention.issueId, new Entry(label, mention.issueId, mention.name, revision.meetingDate));
      }
    }
    String hash =
        EvaluationJson.sha256(
            EvaluationJson.MAPPER.writeValueAsString(List.copyOf(issues.values())));
    var labels = new LinkedHashMap<String, UUID>();
    var model = new ArrayList<MeetingInput.PriorIssue>();
    for (var entry : issues.values()) {
      labels.put(entry.id(), entry.issueId());
      model.add(
          new MeetingInput.PriorIssue(
              entry.id(), entry.description(), entry.reviewedThrough(), hash));
    }
    return new Snapshot(model, labels);
  }
}
