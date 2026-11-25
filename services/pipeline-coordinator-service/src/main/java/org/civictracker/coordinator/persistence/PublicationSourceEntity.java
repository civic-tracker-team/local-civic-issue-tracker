package org.civictracker.coordinator.persistence;

import jakarta.persistence.Embeddable;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.PostLoad;
import jakarta.persistence.PostPersist;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;
import java.util.UUID;

@Entity
@Table(name = "publication_source", schema = "coordinator")
public class PublicationSourceEntity
    implements org.springframework.data.domain.Persistable<PublicationSourceEntity.Key> {
  @Embeddable
  public record Key(UUID publicationId, UUID meetingId, UUID versionId)
      implements java.io.Serializable {}

  @EmbeddedId public Key id;
  @Transient private boolean fresh = true;

  @Override
  public Key getId() {
    return id;
  }

  @Override
  public boolean isNew() {
    return fresh;
  }

  @PostLoad
  @PostPersist
  void persisted() {
    fresh = false;
  }

  public UUID documentId;
}
