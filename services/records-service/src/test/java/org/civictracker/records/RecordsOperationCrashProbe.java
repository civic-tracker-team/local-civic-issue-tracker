package org.civictracker.records;

import org.civictracker.records.application.RecordsOperations;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;

/** A separate process leaves a durable claim behind when the test terminates it. */
public final class RecordsOperationCrashProbe {
  public static void main(String[] args) throws Exception {
    try (var context =
        new SpringApplicationBuilder(RecordsServiceApplication.class)
            .web(WebApplicationType.NONE)
            .run("--records.operations.worker-enabled=false")) {
      var claimed = context.getBean(RecordsOperations.class).claim();
      if (claimed == null) {
        throw new IllegalStateException("No operation to claim");
      }
      System.out.println("DURABLE_OPERATION_CLAIMED " + claimed.id);
      System.out.flush();
      Thread.sleep(60000);
    }
  }
}
