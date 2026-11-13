package org.civictracker.records.persistence;

import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.util.UUID;

@Entity
@Table(name = "acquisition_item", schema = "records")
public class AcquisitionItem extends AssignedEntity {
  public UUID operationId;
  public int ordinal;
  public String targetJson;
  public String targetKey;
  public UUID documentId;
  public UUID versionId;
  public UUID artifactId;
  public UUID observationId;
  public UUID currentVersionId;
  public String sha256;
  public Long byteLength;
  public Boolean currentChanged;
}
