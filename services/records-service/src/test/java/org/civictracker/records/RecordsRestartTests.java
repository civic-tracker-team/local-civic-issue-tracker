package org.civictracker.records;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;

class RecordsRestartTests {
  @Test
  @Timeout(80)
  void identityVersionsAndOriginalBytesSurviveASeparateJvmRestart(@TempDir Path scratch)
      throws Exception {
    String seeded = probe(scratch.resolve("seed.log"), List.of("seed"));
    var result = Pattern.compile("FOUNDATION_PROBE ([0-9a-f-]+) ([0-9a-f-]+) ([0-9a-f-]+)")
        .matcher(seeded);
    assertTrue(result.find(), seeded);
    String checked = probe(scratch.resolve("read.log"),
        List.of("read", result.group(1), result.group(2), result.group(3)));
    assertTrue(checked.contains("FOUNDATION_RESTART_OK"), checked);
  }

  private String probe(Path log, List<String> arguments) throws Exception {
    String executable = System.getProperty("os.name").startsWith("Windows") ? "java.exe" : "java";
    var command = new ArrayList<String>();
    command.add(Path.of(System.getProperty("java.home"), "bin", executable).toString());
    command.add("-cp");
    command.add(System.getProperty(
        "surefire.test.class.path", System.getProperty("java.class.path")));
    command.add(RecordsRestartProbe.class.getName());
    command.addAll(arguments);
    Process process = new ProcessBuilder(command).redirectErrorStream(true)
        .redirectOutput(log.toFile()).start();
    try {
      assertTrue(process.waitFor(35, TimeUnit.SECONDS), "Restart probe exceeded its deadline");
      String output = Files.readString(log);
      assertEquals(0, process.exitValue(), output);
      return output;
    } finally {
      if (process.isAlive()) {
        process.destroyForcibly().waitFor();
      }
    }
  }
}
