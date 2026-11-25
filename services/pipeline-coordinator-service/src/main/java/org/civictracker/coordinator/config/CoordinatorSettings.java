package org.civictracker.coordinator.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("coordinator")
public record CoordinatorSettings(int maxMeetings) {
  public CoordinatorSettings {
    if (maxMeetings <= 0) {
      throw new IllegalArgumentException("coordinator.max-meetings must be positive");
    }
  }
}
