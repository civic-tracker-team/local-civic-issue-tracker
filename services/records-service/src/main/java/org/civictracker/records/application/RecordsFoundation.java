package org.civictracker.records.application;

import jakarta.persistence.EntityManager;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.net.URI;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.civictracker.records.domain.AcquisitionData.Item;
import org.civictracker.records.domain.AcquisitionData.Observation;
import org.civictracker.records.domain.AcquisitionData.Receipt;
import org.civictracker.records.domain.IdentityRules;
import org.civictracker.records.domain.SourceInput;
import org.civictracker.records.persistence.AgendaItem;
import org.civictracker.records.persistence.Artifact;
import org.civictracker.records.persistence.Document;
import org.civictracker.records.persistence.DocumentContext;
import org.civictracker.records.persistence.Meeting;
import org.civictracker.records.persistence.OriginalVersion;
import org.civictracker.records.persistence.SourceAlias;
import org.civictracker.records.persistence.SourceObservation;
import org.civictracker.records.storage.OriginalFileStore;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@Service
public class RecordsFoundation {
  private final EntityManager entities;
  private final JdbcTemplate jdbc;
  private final TransactionTemplate transactions;
  private final OriginalFileStore files;

  public RecordsFoundation(
      EntityManager entities,
      JdbcTemplate jdbc,
      PlatformTransactionManager manager,
      OriginalFileStore files) {
    this.entities = entities;
    this.jdbc = jdbc;
    this.transactions = new TransactionTemplate(manager);
    this.files = files;
  }

  public record Retained(
      UUID documentId,
      UUID versionId,
      String sha256,
      long byteLength,
      String storageKey,
      List<String> primaryIds,
      List<String> historicalIds,
      int versionCount) {}

  public Retained retain(SourceInput input, InputStream original) throws IOException {
    try (OriginalFileStore.Staged staged = files.stage(original)) {
      try {
        return transactions
            .execute(status -> save(input, staged, null, input.sourceUrl()))
            .current();
      } catch (UncheckedIOException error) {
        throw error.getCause();
      }
    }
  }

  private record Saved(Retained current, Receipt receipt) {}

  /** Commits an already staged original in the caller's short checkpoint transaction. */
  public Receipt retainStaged(
      SourceInput input, OriginalFileStore.Staged staged, String publicationDate, URI resolvedUrl) {
    return transactions
        .execute(status -> save(input, staged, publicationDate, resolvedUrl))
        .receipt();
  }

  private Saved save(
      SourceInput input, OriginalFileStore.Staged staged, String publicationDate, URI resolvedUrl) {
    Meeting meeting =
        meeting(
            input.portal(),
            input.meetingId(),
            input.meetingTitle(),
            input.meetingDate(),
            input.cancellationEvidence(),
            input.currentSource(),
            input.observedAt());
    UUID itemId = null;
    if (input.itemId() != null) {
      AgendaItem item =
          entities
              .createQuery(
                  "from AgendaItem where meetingId = :m and officialId = :i", AgendaItem.class)
              .setParameter("m", meeting.id)
              .setParameter("i", input.itemId())
              .getResultList()
              .stream()
              .findFirst()
              .orElseGet(() -> {
                AgendaItem created = new AgendaItem();
                created.meetingId = meeting.id;
                created.officialId = input.itemId();
                created.label = input.itemLabel();
                entities.persist(created);
                return created;
              });
      if (IdentityRules.canAdvance(input.currentSource(), input.observedAt(), item.observedAt)) {
        if (input.itemLabel() != null) {
          item.label = input.itemLabel();
        }
        item.observedAt = input.observedAt();
      }
      itemId = item.id;
    }
    entities.flush();
    Document document = match(input, meeting.id, itemId, staged.sha256());
    if (document == null) {
      document = new Document();
      document.meetingId = meeting.id;
      entities.persist(document);
    }
    UUID documentId = document.id;
    UUID previousVersionId = document.currentVersionId;
    UUID selectedItem = itemId;
    DocumentContext context =
        entities
            .createQuery("from DocumentContext where documentId = :d", DocumentContext.class)
            .setParameter("d", documentId)
            .getResultList()
            .stream()
            .filter(c ->
                c.meetingId.equals(meeting.id)
                    && java.util.Objects.equals(c.itemId, selectedItem)
                    && java.util.Objects.equals(c.templateId, input.templateId())
                    && java.util.Objects.equals(c.outputType, input.outputType())
                    && java.util.Objects.equals(c.language, input.language()))
            .findFirst()
            .orElse(null);
    if (context == null) {
      context = new DocumentContext();
      context.documentId = documentId;
      context.meetingId = meeting.id;
      context.itemId = itemId;
      context.templateId = input.templateId();
      context.outputType = input.outputType();
      context.language = input.language();
      entities.persist(context);
    }
    OriginalVersion version =
        entities
            .createQuery(
                "from OriginalVersion where documentId = :d and sha256 = :s", OriginalVersion.class)
            .setParameter("d", documentId)
            .setParameter("s", staged.sha256())
            .getResultList()
            .stream()
            .findFirst()
            .orElse(null);
    if (version == null) {
      Artifact artifact = new Artifact();
      try {
        artifact.storageKey = files.install(staged, artifact.id);
      } catch (IOException error) {
        throw new UncheckedIOException(error);
      }
      artifact.originalFilename = input.originalFilename();
      entities.persist(artifact);
      version = new OriginalVersion();
      version.documentId = documentId;
      version.sha256 = staged.sha256();
      version.byteLength = staged.byteLength();
      version.artifactId = artifact.id;
      entities.persist(version);
    }
    boolean advance =
        IdentityRules.canAdvance(
            input.currentSource(), input.observedAt(), document.currentObservedAt);
    if (input.portalId() != null) {
      List<SourceAlias> aliases =
          entities
              .createQuery("from SourceAlias where documentId = :d", SourceAlias.class)
              .setParameter("d", documentId)
              .getResultList();
      SourceAlias selected =
          aliases.stream()
              .filter(a ->
                  a.meetingId.equals(meeting.id)
                      && a.family.equals(input.identifierFamily())
                      && a.value.equals(input.portalId()))
              .findFirst()
              .orElse(null);
      if (advance) {
        aliases.forEach(a -> a.primaryReference = false);
        entities.flush();
      }
      if (selected == null) {
        selected = new SourceAlias();
        selected.documentId = documentId;
        selected.meetingId = meeting.id;
        selected.family = input.identifierFamily();
        selected.value = input.portalId();
        selected.primaryReference = false;
        entities.persist(selected);
      }
      if (advance || document.currentVersionId == null) {
        selected.primaryReference = true;
      }
    }
    if (advance || document.currentVersionId == null) {
      document.currentVersionId = version.id;
      if (input.currentSource()) {
        document.currentObservedAt = input.observedAt();
      }
    }
    SourceObservation observation = new SourceObservation();
    observation.documentId = documentId;
    observation.versionId = version.id;
    observation.sourceUrl = input.sourceUrl().toString();
    observation.sourceIdentifier = input.portalId();
    observation.family = input.identifierFamily();
    observation.observedAt = input.observedAt();
    observation.currentSource = input.currentSource();
    observation.meetingTitle = input.meetingTitle();
    observation.meetingDate = input.meetingDate();
    observation.cancellationEvidence = input.cancellationEvidence();
    observation.originalFilename = input.originalFilename();
    observation.publicationDate = publicationDate;
    observation.resolvedUrl = resolvedUrl.toString();
    observation.contextId = context.id;
    observation.itemLabel = input.itemLabel();
    entities.persist(observation);
    entities.flush();
    Retained current = snapshot(documentId);
    return new Saved(
        current,
        new Receipt(
            documentId,
            version.id,
            version.artifactId,
            observation.id,
            document.currentVersionId,
            version.sha256,
            version.byteLength,
            !java.util.Objects.equals(previousVersionId, document.currentVersionId)));
  }

  private Meeting meeting(
      String portal,
      String officialId,
      String title,
      String date,
      String cancellation,
      boolean current,
      Instant observedAt) {
    jdbc.queryForList("select pg_advisory_xact_lock(hashtext(?), hashtext(?))", portal, officialId);
    Meeting meeting =
        entities
            .createQuery("from Meeting where portal = :p and officialId = :i", Meeting.class)
            .setParameter("p", portal)
            .setParameter("i", officialId)
            .getResultList()
            .stream()
            .findFirst()
            .orElseGet(() -> {
              Meeting created = new Meeting();
              created.portal = portal;
              created.officialId = officialId;
              created.title = title;
              created.sourceDate = date;
              created.cancellationEvidence = cancellation;
              entities.persist(created);
              return created;
            });
    if (IdentityRules.canAdvance(current, observedAt, meeting.observedAt)) {
      if (title != null) {
        meeting.title = title;
      }
      if (date != null) {
        meeting.sourceDate = date;
      }
      if (cancellation != null) {
        meeting.cancellationEvidence = cancellation;
      }
      meeting.observedAt = observedAt;
    }
    return meeting;
  }

  /** Retains source metadata even when the meeting has no downloadable originals. */
  public UUID observeMeeting(Observation observation) {
    return transactions.execute(status -> {
      var input = observation.meeting();
      Meeting meeting =
          meeting(
              observation.portal(),
              input.officialId(),
              input.title(),
              input.sourceDate(),
              input.cancellationEvidence(),
              true,
              observation.observedAt());
      if (meeting.observedAt != null && !meeting.observedAt.isAfter(observation.observedAt())) {
        if (input.committeeId() != null) {
          meeting.committeeId = input.committeeId();
        }
        if (input.localDate() != null) {
          meeting.localDate = input.localDate();
        }
        if (input.publicationDate() != null) {
          meeting.publicationDate = input.publicationDate();
        }
        if (input.videoUrl() != null) {
          meeting.videoUrl = input.videoUrl();
        }
      }
      return meeting.id;
    });
  }

  /** Retains item identities discovered from captured packet HTML. */
  public void observeItems(UUID meetingId, List<Item> items, Instant observedAt) {
    transactions.executeWithoutResult(status -> {
      Meeting owner = entities.find(Meeting.class, meetingId);
      jdbc.queryForList(
          "select pg_advisory_xact_lock(hashtext(?), hashtext(?))", owner.portal, owner.officialId);
      for (var input : items) {
        AgendaItem item =
            entities
                .createQuery(
                    "from AgendaItem where meetingId=:m and officialId=:i", AgendaItem.class)
                .setParameter("m", meetingId)
                .setParameter("i", input.officialId())
                .getResultStream()
                .findFirst()
                .orElse(null);
        if (item == null) {
          item = new AgendaItem();
          item.meetingId = meetingId;
          item.officialId = input.officialId();
          entities.persist(item);
        }
        if (item.observedAt == null || !item.observedAt.isAfter(observedAt)) {
          if (input.label() != null) {
            item.label = input.label();
          }
          item.observedAt = observedAt;
        }
      }
    });
  }

  private Document match(SourceInput input, UUID meeting, UUID item, String hash) {
    if (input.portalId() != null) {
      SourceAlias known =
          entities
              .createQuery(
                  "from SourceAlias where meetingId = :m and family = :f and value = :v",
                  SourceAlias.class)
              .setParameter("m", meeting)
              .setParameter("f", input.identifierFamily())
              .setParameter("v", input.portalId())
              .getResultList()
              .stream()
              .findFirst()
              .orElse(null);
      if (known != null) {
        return entities.find(Document.class, known.documentId);
      }
    }
    String sql = "select distinct c.document_id from records.document_context c ";
    List<UUID> candidates;
    if (input.portalId() == null) {
      sql +=
          "join records.source_observation o on o.context_id=c.id "
              + "where c.meeting_id=? and c.item_id is not distinct from cast(? as uuid) "
              + "and o.source_url=?";
      candidates =
          jdbc.query(
              sql,
              (rs, n) -> rs.getObject(1, UUID.class),
              meeting,
              item,
              input.sourceUrl().toString());
    } else if (item != null) {
      sql +=
          "join records.original_version v on v.document_id=c.document_id "
              + "where c.meeting_id=? and c.item_id=? and v.sha256=?";
      candidates = jdbc.query(sql, (rs, n) -> rs.getObject(1, UUID.class), meeting, item, hash);
    } else if (input.templateId() != null
        && input.outputType() != null
        && input.language() != null) {
      sql +=
          "join records.original_version v on v.document_id=c.document_id "
              + "where c.meeting_id=? and c.item_id is null and c.template_id=? "
              + "and c.output_type=? and c.language=? and v.sha256=?";
      candidates =
          jdbc.query(
              sql,
              (rs, n) -> rs.getObject(1, UUID.class),
              meeting,
              input.templateId(),
              input.outputType(),
              input.language(),
              hash);
    } else {
      candidates = List.of();
    }
    return candidates.size() == 1 ? entities.find(Document.class, candidates.getFirst()) : null;
  }

  public Retained inspect(UUID documentId) {
    return transactions.execute(status -> snapshot(documentId));
  }

  public InputStream openOriginal(UUID documentId) throws IOException {
    return files.open(inspect(documentId).storageKey());
  }

  public record VersionDetails(
      UUID id,
      UUID documentId,
      String sha256,
      long byteLength,
      UUID artifactId,
      String storageKey,
      String originalFilename) {}

  public VersionDetails inspectVersion(UUID versionId) {
    return transactions.execute(status -> {
      OriginalVersion version = entities.find(OriginalVersion.class, versionId);
      if (version == null) {
        throw new IllegalArgumentException("Unknown original version");
      }
      Artifact artifact = entities.find(Artifact.class, version.artifactId);
      return new VersionDetails(
          version.id,
          version.documentId,
          version.sha256,
          version.byteLength,
          artifact.id,
          artifact.storageKey,
          artifact.originalFilename);
    });
  }

  public InputStream openVersion(UUID versionId) throws IOException {
    return files.open(inspectVersion(versionId).storageKey());
  }

  private Retained snapshot(UUID documentId) {
    Document document = entities.find(Document.class, documentId);
    if (document == null) {
      throw new IllegalArgumentException("Unknown document");
    }
    OriginalVersion version = entities.find(OriginalVersion.class, document.currentVersionId);
    Artifact artifact = entities.find(Artifact.class, version.artifactId);
    List<SourceAlias> aliases =
        entities
            .createQuery("from SourceAlias where documentId = :d", SourceAlias.class)
            .setParameter("d", documentId)
            .getResultList();
    int count =
        entities
            .createQuery("from OriginalVersion where documentId = :d", OriginalVersion.class)
            .setParameter("d", documentId)
            .getResultList()
            .size();
    return new Retained(
        document.id,
        version.id,
        version.sha256,
        version.byteLength,
        artifact.storageKey,
        aliases.stream().filter(a -> a.primaryReference).map(a -> a.value).sorted().toList(),
        aliases.stream().filter(a -> !a.primaryReference).map(a -> a.value).sorted().toList(),
        count);
  }
}
