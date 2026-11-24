package org.civictracker.analysis;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.core.env.Environment;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
class AnalysisServiceApplicationTests {

  @LocalServerPort private int port;

  @Autowired private Environment environment;
  @Autowired private RequestMappingHandlerMapping mappings;

  @Test
  @Timeout(30)
  void startsWithOnlyTheImplementedInternalRoutesAndNoDefaultIdentity() throws Exception {
    assertTrue(port > 0);
    assertEquals("analysis-service", environment.getProperty("spring.application.name"));
    HttpRequest request =
        HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + "/__baseline_probe__"))
            .timeout(Duration.ofSeconds(10))
            .GET()
            .build();
    try (HttpClient client =
        HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build()) {
      HttpResponse<Void> response = client.send(request, HttpResponse.BodyHandlers.discarding());
      assertEquals(401, response.statusCode());
    }
    var internal =
        mappings.getHandlerMethods().keySet().stream()
            .flatMap(mapping -> mapping.getPatternValues().stream())
            .filter(path -> path.startsWith("/internal/") || path.startsWith("/api/"))
            .collect(java.util.stream.Collectors.toSet());
    assertEquals(
        java.util.Set.of(
            "/internal/v1/analysis-operations",
            "/internal/v1/analysis-operations/{id}/resume",
            "/internal/v1/analysis-operations/{id}",
            "/internal/v1/meeting-analyses/{revisionId}",
            "/internal/v1/issues/{id}",
            "/internal/v1/csrf"),
        internal);
  }
}
