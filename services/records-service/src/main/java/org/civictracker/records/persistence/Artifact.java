package org.civictracker.records.persistence;

import jakarta.persistence.Entity;
import jakarta.persistence.Table;

@Entity
@Table(name = "file_artifact", schema = "records")
public class Artifact extends AssignedEntity {
  public String storageKey;
  public String originalFilename;
}
