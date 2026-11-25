package org.civictracker.coordinator.persistence;

import jakarta.persistence.Embeddable;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.PostLoad;
import jakarta.persistence.PostPersist;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;
import java.time.LocalDate;
import java.util.UUID;
import org.civictracker.coordinator.publication.Disposition;

@Entity
@Table(name = "publication_meeting", schema = "coordinator")
public class PublicationMeetingEntity
    implements org.springframework.data.domain.Persistable<PublicationMeetingEntity.Key> {
  @Embeddable
  public record Key(UUID publicationId, UUID meetingId) implements java.io.Serializable {}

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

  public String officialId;
  public LocalDate meetingDate;
  public String title;

  @Enumerated(EnumType.STRING)
  public Disposition disposition;

  public UUID revisionId;
  public String skipReason;
}
