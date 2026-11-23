package org.civictracker.analysis.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "analysis_operation", schema = "analysis")
public class AnalysisOperation extends AssignedEntity {
  public enum State {
    ACCEPTED,
    RUNNING,
    SUCCEEDED,
    FAILED
  }

  @Enumerated(EnumType.STRING)
  public State state;

  public String inputJson;

  @Column(length = 64)
  public String commandSha256;

  public Instant acceptedAt;
  public Instant startedAt;
  public Instant completedAt;
  public UUID attemptId;
  public UUID workerId;
  public UUID blockedAttemptId;
  public String errorCode;
  public int attempts;
  public UUID revisionId;
}
