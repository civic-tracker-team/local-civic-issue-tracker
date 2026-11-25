package org.civictracker.coordinator.persistence;

import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "run_event", schema = "coordinator")
public class RunEventEntity extends AssignedEntity {
  public enum Kind {
    STARTED,
    HALTED,
    RETRIED,
    SKIPPED,
    ABANDONED,
    PUBLISHED
  }

  public UUID runId;

  @Enumerated(EnumType.STRING)
  public Kind kind;

  public Integer haltId;
  public String maintainer;
  public String detail;
  public Instant at;
}
