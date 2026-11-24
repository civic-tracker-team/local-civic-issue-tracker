package org.civictracker.analysis.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.util.UUID;

@Entity
@Table(name = "evidence_span", schema = "analysis")
public class EvidenceSpanEntity extends AssignedEntity {
  public UUID revisionId;
  public String localLabel;
  public int ordinal;
  public UUID documentId;
  public UUID versionId;
  public UUID preparationId;

  @Column(length = 64)
  public String profileFingerprint;

  public UUID segmentId;
  public int start;

  @Column(name = "\"end\"")
  public int end;

  public String text;
  public int unitOrdinal;
  public int segmentOrdinal;
  public String locatorJson;
}
