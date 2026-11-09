package org.civictracker.records.persistence;

import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "agenda_item", schema = "records")
public class AgendaItem extends AssignedEntity {
  public UUID meetingId;
  public String officialId;
  public String label;
  public Instant observedAt;
  @Version public Long revision;
}
