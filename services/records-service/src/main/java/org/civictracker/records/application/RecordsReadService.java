package org.civictracker.records.application;

import jakarta.persistence.EntityManager;
import java.io.IOException;
import java.io.InputStream;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import org.civictracker.records.api.MeetingViews;
import org.civictracker.records.api.RecordViews;
import org.civictracker.records.persistence.AgendaItem;
import org.civictracker.records.persistence.Artifact;
import org.civictracker.records.persistence.Document;
import org.civictracker.records.persistence.DocumentContext;
import org.civictracker.records.persistence.Meeting;
import org.civictracker.records.persistence.OriginalVersion;
import org.civictracker.records.persistence.SourceAlias;
import org.civictracker.records.persistence.SourceObservation;
import org.civictracker.records.storage.OriginalFileStore;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

/** Reads owner metadata in a consistent database snapshot. */
@Service
@Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
public class RecordsReadService {
  private final EntityManager entities;
  private final OriginalFileStore files;

  public RecordsReadService(EntityManager entities, OriginalFileStore files) {
    this.entities = entities;
    this.files = files;
  }

  /** Returns source references and version identities without their content. */
  public RecordViews.Document document(UUID id) {
    Document document = required(Document.class, id);
    List<RecordViews.SourceId> aliases =
        entities
            .createQuery(
                "from SourceAlias where documentId = :id order by meetingId, family, value",
                SourceAlias.class)
            .setParameter("id", id)
            .getResultList()
            .stream()
            .map(a -> new RecordViews.SourceId(a.meetingId, a.family, a.value, a.primaryReference))
            .toList();
    List<RecordViews.Context> contexts =
        entities
            .createQuery(
                "from DocumentContext where documentId = :id order by id", DocumentContext.class)
            .setParameter("id", id)
            .getResultList()
            .stream()
            .map(this::context)
            .toList();
    List<String> urls =
        entities
            .createQuery(
                "select distinct sourceUrl from SourceObservation where documentId = :id "
                    + "order by sourceUrl",
                String.class)
            .setParameter("id", id)
            .getResultList();
    List<UUID> versions =
        entities
            .createQuery(
                "select id from OriginalVersion where documentId = :id order by id", UUID.class)
            .setParameter("id", id)
            .getResultList();
    return new RecordViews.Document(
        id, document.currentVersionId, aliases, contexts, urls, versions);
  }

  public MeetingViews.Detail meeting(UUID id) {
    Meeting meeting = required(Meeting.class, id);
    var summary =
        new MeetingViews.Summary(
            id,
            meeting.portal,
            meeting.officialId,
            meeting.committeeId,
            meeting.title,
            meeting.sourceDate,
            meeting.publicationDate,
            meeting.videoUrl,
            meeting.cancellationEvidence);
    var items =
        entities
            .createQuery("from AgendaItem where meetingId=:m order by id", AgendaItem.class)
            .setParameter("m", id)
            .getResultList()
            .stream()
            .map(item -> new MeetingViews.Item(item.id, item.officialId, item.label))
            .toList();
    var documents =
        entities
            .createQuery(
                "from DocumentContext where meetingId=:m order by id", DocumentContext.class)
            .setParameter("m", id)
            .getResultList()
            .stream()
            .map(context ->
                new MeetingViews.Document(
                    context.documentId,
                    context.itemId,
                    context.templateId,
                    context.outputType,
                    context.language))
            .toList();
    return new MeetingViews.Detail(summary, items, documents);
  }

  /** Describes one exact retained byte version. */
  public RecordViews.Version version(UUID id) {
    OriginalVersion version = required(OriginalVersion.class, id);
    Artifact artifact = required(Artifact.class, version.artifactId);
    List<SourceObservation> observations =
        entities
            .createQuery("from SourceObservation where versionId = :id", SourceObservation.class)
            .setParameter("id", id)
            .getResultList();
    Instant firstObserved =
        observations.stream().map(o -> o.observedAt).min(Comparator.naturalOrder()).orElse(null);
    List<String> urls = observations.stream().map(o -> o.sourceUrl).distinct().sorted().toList();
    return new RecordViews.Version(
        id,
        version.documentId,
        version.sha256,
        version.byteLength,
        artifact.id,
        artifact.originalFilename,
        firstObserved,
        urls,
        observations.stream()
            .sorted(Comparator.comparing(o -> o.observedAt))
            .map(o ->
                new RecordViews.Observation(
                    o.sourceUrl, o.resolvedUrl, o.publicationDate, o.observedAt, o.currentSource))
            .toList(),
        entities
            .createQuery(
                "select id from PreparedContent where versionId=:v and sealedAt is not null order"
                    + " by id",
                UUID.class)
            .setParameter("v", id)
            .getResultList());
  }

  public record ArtifactReference(UUID id, String storageKey, long byteLength) {}

  /** Resolves an artifact referenced by retained version metadata. */
  public ArtifactReference artifact(UUID id) {
    Artifact artifact = required(Artifact.class, id);
    List<OriginalVersion> versions =
        entities
            .createQuery("from OriginalVersion where artifactId = :id", OriginalVersion.class)
            .setParameter("id", id)
            .getResultList();
    if (versions.isEmpty()) {
      throw new RecordUnavailable();
    }
    return new ArtifactReference(id, artifact.storageKey, versions.getFirst().byteLength);
  }

  /** Opens a retained file after metadata selection, without a long database transaction. */
  @Transactional(propagation = org.springframework.transaction.annotation.Propagation.NOT_SUPPORTED)
  public InputStream open(ArtifactReference artifact) throws IOException {
    return files.openVerifiedLength(artifact.storageKey(), artifact.byteLength());
  }

  private RecordViews.Context context(DocumentContext context) {
    Meeting meeting = required(Meeting.class, context.meetingId);
    AgendaItem item = context.itemId == null ? null : required(AgendaItem.class, context.itemId);
    return new RecordViews.Context(
        meeting.id,
        meeting.portal,
        meeting.officialId,
        context.itemId,
        item == null ? null : item.officialId,
        item == null ? null : item.label,
        context.templateId,
        context.outputType,
        context.language);
  }

  private <T> T required(Class<T> type, UUID id) {
    T entity = entities.find(type, id);
    if (entity == null) {
      throw new RecordUnavailable();
    }
    return entity;
  }
}
