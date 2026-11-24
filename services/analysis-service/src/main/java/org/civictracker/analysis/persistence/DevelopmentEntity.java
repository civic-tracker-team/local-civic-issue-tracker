package org.civictracker.analysis.persistence;

import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.util.UUID;
import org.civictracker.analysis.domain.Candidates.Status;
import org.civictracker.analysis.domain.FindingKind;

@Entity
@Table(name = "development", schema = "analysis")
public class DevelopmentEntity extends AssignedEntity {
  public UUID revisionId;
  public String localLabel;
  public int ordinal;
  public String description;

  @Enumerated(EnumType.STRING)
  public FindingKind kind;

  @Enumerated(EnumType.STRING)
  public Status status;
}
