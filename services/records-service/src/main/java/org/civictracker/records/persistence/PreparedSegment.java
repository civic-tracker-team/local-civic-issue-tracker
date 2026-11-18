package org.civictracker.records.persistence;

import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.util.UUID;

@Entity
@Table(name = "prepared_segment", schema = "records")
public class PreparedSegment extends AssignedEntity {
  public UUID unitId;
  public int ordinal;
  public String textValue;
  public String method;
  public String locatorJson;
  public Double score;
  public boolean selected;
}
