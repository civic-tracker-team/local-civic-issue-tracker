package org.civictracker.analysis.persistence;

import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.util.UUID;

@Entity
@Table(name = "issue_mention", schema = "analysis")
public class IssueMention extends AssignedEntity {
  public UUID revisionId;
  public int ordinal;
  public UUID issueId;
  public String localLabel;
  public String name;
  public String priorLabel;
}
