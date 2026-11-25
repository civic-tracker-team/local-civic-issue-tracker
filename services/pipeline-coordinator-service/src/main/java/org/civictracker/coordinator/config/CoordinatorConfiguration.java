package org.civictracker.coordinator.config;

import java.time.Clock;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

@Configuration
@EnableConfigurationProperties(CoordinatorSettings.class)
@EnableScheduling
public class CoordinatorConfiguration {
  @Bean
  Clock coordinatorClock() {
    return Clock.systemUTC();
  }
}
