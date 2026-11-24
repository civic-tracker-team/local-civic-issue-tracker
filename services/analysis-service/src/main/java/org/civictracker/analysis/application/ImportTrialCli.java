package org.civictracker.analysis.application;

import java.nio.file.Path;
import org.civictracker.analysis.AnalysisServiceApplication;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;

/** Imports a saved trial without starting an HTTP server. */
public final class ImportTrialCli {
  private ImportTrialCli() {}

  public static void main(String[] args) throws Exception {
    if (args.length != 1) throw new IllegalArgumentException("Usage: import-trial TRIAL_DIR");
    try (var context =
        new SpringApplicationBuilder(AnalysisServiceApplication.class)
            .web(WebApplicationType.NONE)
            .run("--analysis.operations.worker-enabled=false")) {
      System.out.println(context.getBean(TrialImporter.class).importTrial(Path.of(args[0])));
    }
  }
}
