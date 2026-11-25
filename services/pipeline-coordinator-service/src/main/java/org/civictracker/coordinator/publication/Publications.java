package org.civictracker.coordinator.publication;

import jakarta.persistence.EntityManager;
import java.util.Comparator;
import java.util.Optional;
import java.util.UUID;
import org.civictracker.coordinator.persistence.CurrentPublicationEntity;
import org.civictracker.coordinator.persistence.PublicationEntity;
import org.civictracker.coordinator.persistence.PublicationMeetingEntity;
import org.civictracker.coordinator.persistence.PublicationRangeEntity;
import org.civictracker.coordinator.persistence.PublicationSourceEntity;
import org.civictracker.coordinator.run.DateRange;
import org.civictracker.coordinator.run.RunProblem;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@Service
public class Publications {
  private final EntityManager entities;
  private final TransactionTemplate transactions;

  public Publications(EntityManager entities, PlatformTransactionManager manager) {
    this.entities = entities;
    transactions = new TransactionTemplate(manager);
  }

  public Optional<Manifest> current() {
    return transactions.execute(tx -> {
      var id = entities.find(CurrentPublicationEntity.class, 1).publicationId;
      return id == null ? Optional.empty() : Optional.of(read(id));
    });
  }

  public Optional<Manifest> byId(UUID id) {
    return transactions.execute(tx ->
        entities.find(PublicationEntity.class, id) == null
            ? Optional.empty()
            : Optional.of(read(id)));
  }

  private Manifest read(UUID id) {
    var publication = entities.find(PublicationEntity.class, id);
    if (publication == null) {
      throw new RunProblem(404, "PUBLICATION_NOT_FOUND");
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
    return new Manifest(id, publication.runId, publication.previousId, coverage, meetings);
  }
}
