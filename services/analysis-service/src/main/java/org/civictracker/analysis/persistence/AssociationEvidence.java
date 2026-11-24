package org.civictracker.analysis.persistence;

import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.util.UUID;

@Entity
@Table(name = "association_evidence", schema = "analysis")
public class AssociationEvidence extends AssignedEntity {
  public UUID revisionId;
  public UUID associationId;
  public UUID evidenceId;
  public int ordinal;
}
