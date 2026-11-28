package org.civictracker.search.persistence;

import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.time.LocalDate;
import java.util.UUID;
import org.civictracker.search.index.IndexPlan;

/** One searchable row. The database derives its weighted text vector. */
@Entity
@Table(name = "entry", schema = "search")
public class SearchEntry extends AssignedEntity {
  public UUID buildId;

  @Enumerated(EnumType.STRING)
  public IndexPlan.Kind kind;

  public UUID targetId;
  public UUID versionId;
  public int part;
  public String label;
  public String context;
  public String body;
  public String location;
  public String detailsJson;
  public LocalDate sortDate;
}
