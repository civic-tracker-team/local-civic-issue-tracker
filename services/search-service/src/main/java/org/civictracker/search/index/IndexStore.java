package org.civictracker.search.index;

import jakarta.persistence.EntityManager;
import java.time.Clock;
import java.time.Instant;
import java.util.UUID;
import org.civictracker.search.persistence.IndexBuild;
import org.civictracker.search.persistence.SearchEntry;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.json.JsonMapper;

/**
 * Writes a complete index and moves the current pointer in the same transaction, so searches see
 * either the previous collection or the whole new one. Older builds are removed afterwards; a
 * search that already read them finishes on its own snapshot.
 */
@Service
public class IndexStore {
  private static final JsonMapper JSON = JsonMapper.builder().build();
  private static final int FLUSH_EVERY = 500;

  private final EntityManager entities;
  private final JdbcTemplate jdbc;
  private final TransactionTemplate transactions;
  private final Clock clock;

  public IndexStore(
      EntityManager entities, JdbcTemplate jdbc, PlatformTransactionManager manager, Clock clock) {
    this.entities = entities;
    this.jdbc = jdbc;
    transactions = new TransactionTemplate(manager);
    this.clock = clock;
  }

  /** The publication the current index was built from, or null before the first build. */
  public UUID currentPublication() {
    return jdbc
        .queryForList(
            "select b.publication_id from search.current_index c join search.index_build b"
                + " on b.id = c.build_id where c.id = 1",
            UUID.class)
        .stream()
        .findFirst()
        .orElse(null);
  }

  /** Saves the plan as the current index. Rebuilding a publication already saved repoints it. */
  public void save(IndexPlan.Plan plan) {
    transactions.executeWithoutResult(tx -> {
      jdbc.queryForList("select id from search.current_index where id = 1 for update");
      var existing =
          jdbc.queryForList(
              "select id from search.index_build where publication_id = ?",
              UUID.class,
              plan.publicationId());
      UUID buildId;
      if (!existing.isEmpty()) {
        buildId = existing.getFirst();
      } else {
        var build = new IndexBuild();
        build.publicationId = plan.publicationId();
        build.coverageJson = JSON.writeValueAsString(plan.coverage());
        build.recordTypesJson = JSON.writeValueAsString(plan.recordTypes());
        build.meetingCount = plan.meetingCount();
        build.untextedVersions = plan.untextedVersions();
        build.builtAt = Instant.now(clock);
        entities.persist(build);
        buildId = build.id;
        int written = 0;
        for (var planned : plan.entries()) {
          var entry = new SearchEntry();
          entry.buildId = buildId;
          entry.kind = planned.kind();
          entry.targetId = planned.targetId();
          entry.versionId = planned.versionId();
          entry.part = planned.part();
          entry.label = planned.label();
          entry.context = planned.context();
          entry.body = planned.body();
          entry.location = planned.location();
          entry.detailsJson = JSON.writeValueAsString(planned.details());
          entry.sortDate = planned.sortDate();
          entities.persist(entry);
          if (++written % FLUSH_EVERY == 0) {
            entities.flush();
            entities.clear();
          }
        }
        entities.flush();
        entities.clear();
      }
      jdbc.update("update search.current_index set build_id = ? where id = 1", buildId);
      jdbc.update("delete from search.index_build where id <> ?", buildId);
    });
  }
}
