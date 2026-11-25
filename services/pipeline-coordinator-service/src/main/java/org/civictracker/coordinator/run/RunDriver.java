package org.civictracker.coordinator.run;

import org.civictracker.coordinator.config.CoordinatorRuntime;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Polls owner operations only on an explicitly enabled coordinator. */
@Component
@ConditionalOnProperty(name = "coordinator.driver.enabled", havingValue = "true")
public class RunDriver {
  private static final Logger LOG = LoggerFactory.getLogger(RunDriver.class);
  private final Runs runs;

  public RunDriver(Runs runs, CoordinatorRuntime runtime) {
    runtime.validate();
    this.runs = runs;
  }

  @Scheduled(fixedDelayString = "${coordinator.driver.delay:PT5S}")
  public void drive() {
    try {
      runs.tick();
    } catch (RuntimeException failure) {
      LOG.warn(
          "Coordinator driver cannot reconcile durable state: {}",
          failure.getClass().getSimpleName());
    }
  }
}
