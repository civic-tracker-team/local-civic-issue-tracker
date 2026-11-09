package org.civictracker.records.persistence;

import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.util.UUID;

@Entity
@Table(name = "original_version", schema = "records")
public class OriginalVersion extends AssignedEntity {
  public UUID documentId;
  public String sha256;
  public Long byteLength;
  public UUID artifactId;
}
