package org.civictracker.analysis;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class ImportFixtureLineEndingsTests {
  @Test
  void importResourcesContainNoCarriageReturns() throws Exception {
    Path root = Path.of("src/test/resources/import-trial");
    assertTrue(Files.isDirectory(root));
    try (var files = Files.walk(root)) {
      for (var file : files.filter(Files::isRegularFile).toList()) {
        for (byte value : Files.readAllBytes(file)) {
          assertNotEquals((byte) '\r', value, file.toString());
        }
      }
    }
  }
}
