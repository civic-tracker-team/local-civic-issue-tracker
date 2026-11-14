package org.civictracker.records.persistence;

import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.util.UUID;

@Entity
@Table(name = "prepared_unit", schema = "records")
public class PreparedUnit extends AssignedEntity {
  public UUID preparationId;
  public int ordinal;
  public String disposition;
  public String nativeText;
  public String notesJson;
}
