package org.civictracker.records.persistence;

import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import org.civictracker.records.domain.AcquisitionData.Kind;
import org.civictracker.records.domain.AcquisitionData.State;

@Entity
@Table(name = "record_operation", schema = "records")
public class RecordOperation extends AssignedEntity {
  @Enumerated(EnumType.STRING)
  public Kind kind;

  @Enumerated(EnumType.STRING)
  public State state;

  public String inputJson;
  public Instant acceptedAt;
  public Instant startedAt;
  public Instant completedAt;
  public UUID attemptId;
  public UUID workerId;
  public UUID replacesId;
  public UUID blockedExecutionId;
  public UUID preparingVersionId;
  public Integer preparingUnitOrdinal;
  public String errorCode;
  public int attempts;
}
