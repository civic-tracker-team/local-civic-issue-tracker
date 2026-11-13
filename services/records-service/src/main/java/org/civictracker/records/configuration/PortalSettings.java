package org.civictracker.records.configuration;

import java.util.List;
import java.util.Map;
import org.civictracker.records.application.OperationProblem;
import org.civictracker.records.domain.AcquisitionData.Profile;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/** Portal access is opt-in through a configured source profile. */
@Configuration
@EnableConfigurationProperties(PortalSettings.Sources.class)
public class PortalSettings {
  @ConfigurationProperties("records.portal")
  public record Sources(Map<String, Profile> sources) {
    public Sources {
      sources = sources == null ? Map.of() : Map.copyOf(sources);
      sources.forEach((name, p) -> {
        if (name.isBlank()
            || p.baseUrl() == null
            || p.baseUrl().getHost() == null
            || p.revision() == null
            || p.revision().isBlank()
            || p.requestIntervalMillis() < 0
            || p.connectTimeoutMillis() <= 0
            || p.transferTimeoutMillis() <= 0
            || p.maxBytes() <= 0
            || p.maxRedirects() < 0
            || p.baseUrl().getUserInfo() != null
            || p.baseUrl().getQuery() != null
            || p.baseUrl().getFragment() != null
            || !(p.baseUrl().getPath().isEmpty() || p.baseUrl().getPath().equals("/"))) {
          throw new IllegalArgumentException("Invalid configured portal profile");
        }
        List<String> hosts = p.allowedHosts();
        if (!hosts.contains(p.baseUrl().getHost()) || hosts.stream().anyMatch(String::isBlank)) {
          throw new IllegalArgumentException("Portal hosts must include the configured base");
        }
        if (!p.baseUrl().getScheme().equals("https") && !p.allowLoopback()) {
          throw new IllegalArgumentException("Official portal access requires HTTPS");
        }
      });
    }

    public Profile required(String sourceId) {
      Profile profile = sources.get(sourceId);
      if (profile == null) {
        throw new OperationProblem(400, "UNKNOWN_SOURCE");
      }
      return profile;
    }
  }
}
