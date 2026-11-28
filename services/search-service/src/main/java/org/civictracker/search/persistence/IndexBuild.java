package org.civictracker.search.persistence;

import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/** A complete index of one publication. Never updated after it is written. */
@Entity
@Table(name = "index_build", schema = "search")
public class IndexBuild extends AssignedEntity {
  public UUID publicationId;
  public String coverageJson;
  public String recordTypesJson;
  public int meetingCount;
  public int untextedVersions;
  public Instant builtAt;
}
