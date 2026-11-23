package org.civictracker.analysis;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class AnalysisServiceApplication {

  public static void main(String[] args) throws Exception {
    if (args.length > 0 && args[0].equals("import-trial")) {
      org.civictracker.analysis.application.ImportTrialCli.main(
          java.util.Arrays.copyOfRange(args, 1, args.length));
      return;
    }
    if (args.length > 0 && args[0].equals("summarize")) {
      org.civictracker.analysis.evaluation.TrialSummary.main(
          java.util.Arrays.copyOfRange(args, 1, args.length));
      return;
    }
    if (args.length > 0 && args[0].equals("score")) {
      org.civictracker.analysis.evaluation.ReviewCli.main(
          java.util.Arrays.copyOfRange(args, 1, args.length));
      return;
    }
    if (args.length > 0 && args[0].equals("evaluate")) {
      org.civictracker.analysis.evaluation.EvaluationCli.main(
          java.util.Arrays.copyOfRange(args, 1, args.length));
      return;
    }
    SpringApplication.run(AnalysisServiceApplication.class, args);
  }
}
