package org.civictracker.records.persistence;

import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;

@Entity
@Table(name = "source_meeting", schema = "records")
public class Meeting extends AssignedEntity {
  public String committeeId;
  public java.time.LocalDate localDate;
  public String publicationDate;
  public String videoUrl;

  public String portal;
  public String officialId;
  public String title;
  public String sourceDate;
  public String cancellationEvidence;
  public Instant observedAt;
  @Version public Long revision;
}
