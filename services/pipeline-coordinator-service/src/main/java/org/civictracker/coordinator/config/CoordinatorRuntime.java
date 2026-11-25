package org.civictracker.coordinator.config;

import java.net.URI;
import java.time.Duration;
import org.civictracker.coordinator.owners.AnalysisClient;
import org.civictracker.coordinator.owners.RecordsClient;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

/** Creates configured owner clients without requiring a live owner at startup. */
@Component
public class CoordinatorRuntime {
  private final Environment environment;
  private RecordsClient records;
  private AnalysisClient analysis;

  public CoordinatorRuntime(Environment environment) {
    this.environment = environment;
  }

  public boolean automaticPublish() {
    return environment.getProperty("coordinator.publish.automatic", Boolean.class, false);
  }

  public String preparationProfileId() {
    return required("coordinator.preparation-profile-id");
  }

  public Duration stallAfter() {
    Duration value = Duration.parse(required("coordinator.stall-after"));
    if (value.isZero() || value.isNegative()) {
      throw new IllegalArgumentException("Invalid stall interval");
    }
    return value;
  }

  public synchronized RecordsClient records() {
    if (records == null) {
      records =
          new RecordsClient(
              base("records"),
              required("coordinator.records.username"),
              required("coordinator.records.password"),
              timeout("records"));
    }
    return records;
  }

  public synchronized AnalysisClient analysis() {
    if (analysis == null) {
      analysis =
          new AnalysisClient(
              base("analysis"),
              required("coordinator.analysis.username"),
              required("coordinator.analysis.password"),
              timeout("analysis"));
    }
    return analysis;
  }

  public URI statusUrl(boolean analysis, java.util.UUID id) {
    return base(analysis ? "analysis" : "records")
        .resolve("/internal/v1/" + (analysis ? "analysis-operations/" : "operations/") + id);
  }

  public void validate() {
    preparationProfileId();
    stallAfter();
    records();
    analysis();
  }

  private URI base(String owner) {
    return URI.create(required("coordinator." + owner + ".base-url"));
  }

  private Duration timeout(String owner) {
    return Duration.ofSeconds(
        Long.parseLong(required("coordinator." + owner + ".timeout-seconds")));
  }

  private String required(String key) {
    String value = environment.getProperty(key);
    if (value == null || value.isBlank()) {
      throw new IllegalArgumentException("Missing coordinator configuration");
    }
    return value;
  }
}
