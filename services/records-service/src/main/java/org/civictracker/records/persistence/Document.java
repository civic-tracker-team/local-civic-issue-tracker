package org.civictracker.records.persistence;

import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "source_document", schema = "records")
public class Document extends AssignedEntity {
  public UUID meetingId;
  public UUID currentVersionId;
  public Instant currentObservedAt;
  @Version
  public Long revision;
}
