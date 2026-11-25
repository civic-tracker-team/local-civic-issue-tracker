package org.civictracker.coordinator.persistence;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.UUID;

@Entity
@Table(name = "current_publication", schema = "coordinator")
public class CurrentPublicationEntity {
  @Id public int id = 1;
  public UUID publicationId;
}
