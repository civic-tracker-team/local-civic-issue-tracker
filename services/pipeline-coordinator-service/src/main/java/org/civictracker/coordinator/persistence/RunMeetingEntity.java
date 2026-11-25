package org.civictracker.coordinator.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.PostLoad;
import jakarta.persistence.PostPersist;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import org.civictracker.coordinator.run.NoInputReason;
import org.civictracker.coordinator.run.ProgressState;

@Entity
@Table(name = "run_meeting", schema = "coordinator")
public class RunMeetingEntity
    implements org.springframework.data.domain.Persistable<RunMeetingEntity.Key> {
  @Embeddable
  public record Key(UUID runId, int ordinal) implements java.io.Serializable {}

  @EmbeddedId public Key id;
  @Transient private boolean fresh = true;

  @Override
  public Key getId() {
    return id;
  }

  @Override
  public boolean isNew() {
    return fresh;
  }

  @PostLoad
  @PostPersist
  void persisted() {
    fresh = false;
  }

  public UUID meetingId;
  public String officialId;
  public LocalDate meetingDate;
  public String title;
  public boolean cancellationEvidence;

  @Enumerated(EnumType.STRING)
  public ProgressState progress;

  @Enumerated(EnumType.STRING)
  public NoInputReason noInputReason;

  public int acquireAttempt = 1;
  public int prepareAttempt = 1;
  public int analyzeAttempt = 1;
  public UUID revisionId;
  public String skipReason;
  public String skippedBy;
  public Instant skippedAt;
  public String frozenRequestJson;

  @Column(length = 64)
  public String frozenRequestSha256;
}
