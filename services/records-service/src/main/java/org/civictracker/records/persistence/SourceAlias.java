package org.civictracker.records.persistence;

import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.util.UUID;

@Entity
@Table(name = "source_alias", schema = "records")
public class SourceAlias extends AssignedEntity {
  public UUID documentId;
  public UUID meetingId;
  public String family;
  public String value;
  public Boolean primaryReference;
}
