package org.civictracker.coordinator.persistence;

import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "publication", schema = "coordinator")
public class PublicationEntity extends AssignedEntity {
  public UUID runId;
  public UUID previousId;
  public Instant publishedAt;
}
