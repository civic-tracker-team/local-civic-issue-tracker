package org.civictracker.records.persistence;

import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "discovered_meeting", schema = "records")
public class DiscoveredMeeting extends AssignedEntity {
  public UUID operationId;
  public UUID meetingId;
  public int ordinal;
  public String payload;
  public Instant observedAt;
}
