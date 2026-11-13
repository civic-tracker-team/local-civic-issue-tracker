package org.civictracker.records.persistence;

import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "source_observation", schema = "records")
public class SourceObservation extends AssignedEntity {
  public String publicationDate;
  public String resolvedUrl;

  public UUID documentId;
  public UUID versionId;
  public String sourceUrl;
  public String sourceIdentifier;
  public String family;
  public Instant observedAt;
  public Boolean currentSource;
  public String meetingTitle;
  public String meetingDate;
  public String cancellationEvidence;
  public String originalFilename;
  public UUID contextId;
  public String itemLabel;
}
