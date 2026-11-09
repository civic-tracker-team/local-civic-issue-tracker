package org.civictracker.records.persistence;

import jakarta.persistence.Id;
import jakarta.persistence.MappedSuperclass;
import jakarta.persistence.PostLoad;
import jakarta.persistence.PostPersist;
import jakarta.persistence.Transient;
import java.util.UUID;
import org.springframework.data.domain.Persistable;

@MappedSuperclass
public abstract class AssignedEntity implements Persistable<UUID> {
  @Id public UUID id = UUID.randomUUID();
  @Transient private boolean fresh = true;

  @Override
  public UUID getId() {
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
}
