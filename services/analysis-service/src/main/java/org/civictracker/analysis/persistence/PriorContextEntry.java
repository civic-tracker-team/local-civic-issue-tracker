package org.civictracker.analysis.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "prior_context", schema = "analysis")
public class PriorContextEntry extends AssignedEntity {
  public UUID revisionId;
  public int ordinal;
  public String priorLabel;
  public UUID issueId;
  public String description;
  public LocalDate reviewedThrough;

  @Column(length = 64)
  public String snapshotSha256;
}
