package org.civictracker.analysis.persistence;

import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.util.UUID;

@Entity
@Table(name = "development_evidence", schema = "analysis")
public class DevelopmentEvidence extends AssignedEntity {
  public UUID revisionId;
  public UUID developmentId;
  public UUID evidenceId;
  public int ordinal;
}
