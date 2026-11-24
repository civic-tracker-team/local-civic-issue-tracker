package org.civictracker.analysis.persistence;

import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.util.UUID;
import org.civictracker.analysis.domain.Candidates.Basis;

@Entity
@Table(name = "association", schema = "analysis")
public class AssociationEntity extends AssignedEntity {
  public UUID revisionId;
  public int ordinal;
  public UUID developmentId;
  public UUID issueId;

  @Enumerated(EnumType.STRING)
  public Basis basis;
}
