package org.civictracker.analysis.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "meeting_analysis", schema = "analysis")
public class AnalysisRevision extends AssignedEntity {
  public enum Origin {
    OPERATION,
    IMPORTED
  }

  public UUID operationId;

  @Enumerated(EnumType.STRING)
  public Origin origin;

  public UUID meetingId;
  public String officialId;
  public LocalDate meetingDate;
  public String title;
  public String contractRevision;
  public String contractDetailJson;

  @Column(length = 64)
  public String profileFingerprint;

  @Column(length = 64)
  public String inputSha256;

  @Column(length = 64)
  public String modelInputSha256;

  @Column(length = 64)
  public String candidatesSha256;

  @Column(length = 64)
  public String receiptSha256;

  public long promptTokens;
  public String artifactKey;
  public Instant createdAt;
}
