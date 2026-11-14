package org.civictracker.records.persistence;

import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "prepared_content", schema = "records")
public class PreparedContent extends AssignedEntity {
  public UUID documentId;
  public UUID versionId;
  public UUID artifactId;
  public String inputSha256;
  public String profileFingerprint;
  public String profileJson;
  public Integer unitCount;
  public Instant sealedAt;
}
