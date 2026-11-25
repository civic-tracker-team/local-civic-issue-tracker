package org.civictracker.coordinator.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import org.civictracker.coordinator.run.ObservedState;
import org.civictracker.coordinator.run.RunState;
import org.civictracker.coordinator.run.StepKind;

@Entity
@Table(name = "run", schema = "coordinator")
public class RunEntity extends AssignedEntity {
  @Column(length = 64)
  public String commandSha256;

  public String sourceId;
  public LocalDate rangeFrom;
  public LocalDate rangeTo;
  public String committeeIds;
  public int maxMeetings;
  public String preparationProfileId;
  public UUID basePublicationId;

  @Enumerated(EnumType.STRING)
  public RunState state;

  public int discoveryAttempt = 1;
  public boolean discovered;
  public int haltId;

  @Enumerated(EnumType.STRING)
  public StepKind haltKind;

  public Integer haltOrdinal;
  public UUID haltOperationId;
  public String haltCode;
  public Instant haltedAt;
  public UUID observedOperationId;

  @Enumerated(EnumType.STRING)
  public ObservedState observedState;

  public UUID observedBlockedId;
  public Instant observedAt;
  public String acceptedBy;
  public Instant acceptedAt;
  public Instant completedAt;
  public String abandonReason;
  @Version public long version;
}
