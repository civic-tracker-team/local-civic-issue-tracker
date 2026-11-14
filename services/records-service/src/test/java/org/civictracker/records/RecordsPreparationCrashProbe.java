package org.civictracker.records;

import org.civictracker.records.application.RecordsOperationWorker;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;

/** Child JVM used to interrupt preparation after durable inference acceptance. */
public final class RecordsPreparationCrashProbe {
  private RecordsPreparationCrashProbe() {}

  public static void main(String[] args) {
    try (var context =
        new SpringApplicationBuilder(RecordsServiceApplication.class)
            .web(WebApplicationType.NONE)
            .run()) {
      context.getBean(RecordsOperationWorker.class).processNext();
    }
  }
}
