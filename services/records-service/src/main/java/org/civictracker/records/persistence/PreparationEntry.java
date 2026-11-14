package org.civictracker.records.persistence;

import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.util.UUID;

@Entity
@Table(name = "preparation_entry", schema = "records")
public class PreparationEntry extends AssignedEntity {
  public UUID operationId;
  public int ordinal;
  public UUID acquisitionItemId;
  public UUID preparationId;
}
