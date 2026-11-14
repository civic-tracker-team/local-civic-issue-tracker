package org.civictracker.records.configuration;

import java.net.URI;
import java.util.Map;
import org.civictracker.records.domain.PreparationData.Profile;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(PreparationSettings.Settings.class)
public class PreparationSettings {
  @ConfigurationProperties("records.preparation")
  public record Settings(
      Map<String, Profile> profiles,
      URI ocrBaseUrl,
      String ocrToken,
      long ocrTimeoutMillis,
      long maxOcrBytes,
      int maxOcrResponseBytes,
      boolean allowLoopback,
      int maxInputs,
      int maxReadUnits) {
    public Settings {
      profiles = profiles == null ? Map.of() : Map.copyOf(profiles);
      ocrTimeoutMillis = ocrTimeoutMillis == 0 ? 120000 : ocrTimeoutMillis;
      maxOcrBytes = maxOcrBytes == 0 ? 67108864 : maxOcrBytes;
      maxOcrResponseBytes = maxOcrResponseBytes == 0 ? 4194304 : maxOcrResponseBytes;
      maxInputs = maxInputs == 0 ? 20000 : maxInputs;
      maxReadUnits = maxReadUnits == 0 ? 20 : maxReadUnits;
      if (ocrTimeoutMillis < 1
          || maxOcrBytes < 1
          || maxOcrResponseBytes < 1
          || maxInputs < 1
          || maxInputs == Integer.MAX_VALUE
          || maxReadUnits < 1) {
        throw new IllegalArgumentException("Preparation bounds must be positive");
      }
    }

    @Override
    public String toString() {
      return "Preparation settings with " + profiles.size() + " profiles";
    }
  }
}
