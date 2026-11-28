package org.civictracker.search.configuration;

import java.net.URI;
import java.time.Clock;
import java.time.Duration;
import org.civictracker.search.index.IndexStore;
import org.civictracker.search.index.Indexer;
import org.civictracker.search.owners.OwnerHttp;
import org.civictracker.search.owners.PublishedSources;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;

/** Wires owner reads and the scheduled indexer only when the indexer is enabled. */
@Configuration
@EnableConfigurationProperties(SearchRuntime.Owners.class)
public class SearchRuntime {
  @ConfigurationProperties("search.owners")
  public record Owners(Duration timeout, Owner coordinator, Owner records, Owner analysis) {}

  public record Owner(String baseUrl, String user, String password) {
    OwnerHttp http(String name, Duration timeout) {
      if (baseUrl == null || baseUrl.isBlank()) {
        throw new IllegalArgumentException("Owner " + name + " base URL is not configured");
      }
      return new OwnerHttp(name, URI.create(baseUrl), timeout, user, password);
    }
  }

  @Bean
  @ConditionalOnMissingBean
  Clock clock() {
    return Clock.systemUTC();
  }

  @Configuration
  @EnableScheduling
  @ConditionalOnProperty(name = "search.indexer.enabled", havingValue = "true")
  static class Indexing {
    @Bean
    PublishedSources publishedSources(Owners owners) {
      if (owners.coordinator() == null || owners.records() == null || owners.analysis() == null) {
        throw new IllegalArgumentException("The indexer needs coordinator, records and analysis");
      }
      return new PublishedSources(
          owners.coordinator().http("coordinator", owners.timeout()),
          owners.records().http("records", owners.timeout()),
          owners.analysis().http("analysis", owners.timeout()));
    }

    @Bean
    Indexer indexer(PublishedSources sources, IndexStore store) {
      return new Indexer(sources, store);
    }

    @Bean
    Schedule indexSchedule(Indexer indexer) {
      return new Schedule(indexer);
    }
  }

  /** Polls the coordinator's current publication. One refresh runs at a time. */
  static final class Schedule {
    private final Indexer indexer;

    Schedule(Indexer indexer) {
      this.indexer = indexer;
    }

    @Scheduled(
        fixedDelayString = "${search.indexer.delay}",
        initialDelayString = "${search.indexer.initial-delay:PT0S}")
    void refresh() throws InterruptedException {
      indexer.refresh();
    }
  }
}
