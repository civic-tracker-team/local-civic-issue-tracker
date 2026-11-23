package org.civictracker.analysis.persistence;

import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.util.UUID;

@Entity
@Table(name = "analysis_issue", schema = "analysis")
public class AnalysisIssue extends AssignedEntity {
  public UUID firstRevisionId;
  public String firstLabel;
}
