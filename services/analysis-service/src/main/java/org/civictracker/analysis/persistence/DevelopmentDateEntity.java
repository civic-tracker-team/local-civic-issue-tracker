package org.civictracker.analysis.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.util.UUID;
import org.civictracker.analysis.domain.Candidates.Precision;
import org.civictracker.analysis.domain.Candidates.Role;

@Entity
@Table(name = "development_date", schema = "analysis")
public class DevelopmentDateEntity extends AssignedEntity {
  public UUID developmentId;
  public int ordinal;

  @Enumerated(EnumType.STRING)
  public Role role;

  @Enumerated(EnumType.STRING)
  public Precision precision;

  public String start;

  @Column(name = "\"end\"")
  public String end;

  public boolean approximate;
}
