package org.civictracker.search.index;

import java.util.Objects;
import org.civictracker.search.owners.OwnerUnavailable;
import org.civictracker.search.owners.PublishedSources;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Keeps the index on the coordinator's current publication. A failed read or write leaves the
 * previous index serving searches (NFR-13); the next refresh tries again.
 */
public class Indexer {
  public enum Outcome {
    NO_PUBLICATION,
    UNCHANGED,
    BUILT,
    FAILED
  }

  private static final Logger LOG = LoggerFactory.getLogger(Indexer.class);

  private final PublishedSources sources;
  private final IndexStore store;

  public Indexer(PublishedSources sources, IndexStore store) {
    this.sources = Objects.requireNonNull(sources);
    this.store = store;
  }

  public synchronized Outcome refresh() throws InterruptedException {
    try {
      var current = sources.current();
      if (current.isEmpty()) {
        return Outcome.NO_PUBLICATION;
      }
      var manifest = current.get();
      if (manifest.publicationId().equals(store.currentPublication())) {
        return Outcome.UNCHANGED;
      }
      var plan = IndexPlan.build(sources.collect(manifest));
      store.save(plan);
      LOG.info(
          "Indexed publication {}: {} rows, {} versions searchable by title only",
          plan.publicationId(),
          plan.entries().size(),
          plan.untextedVersions());
      return Outcome.BUILT;
    } catch (OwnerUnavailable error) {
      LOG.warn("Search index not refreshed: {} ({})", error.code(), error.getMessage());
      return Outcome.FAILED;
    } catch (RuntimeException error) {
      LOG.warn("Search index not refreshed: {}", error.getClass().getSimpleName(), error);
      return Outcome.FAILED;
    }
  }
}
