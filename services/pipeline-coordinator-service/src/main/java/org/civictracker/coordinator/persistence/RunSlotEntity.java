package org.civictracker.coordinator.persistence;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.UUID;

@Entity
@Table(name = "run_slot", schema = "coordinator")
public class RunSlotEntity {
  @Id public int id = 1;
  public UUID runId;
}
