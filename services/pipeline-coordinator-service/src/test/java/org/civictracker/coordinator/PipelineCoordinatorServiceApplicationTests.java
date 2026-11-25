package org.civictracker.coordinator;

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

@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
class PipelineCoordinatorServiceApplicationTests {

  @LocalServerPort private int port;

  @Autowired private Environment environment;

  @Autowired
  private org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping
      mappings;

  @Test
  @Timeout(30)
  void startsWithOnlyTheImplementedInternalRunRoutesAndNoDefaultIdentity() throws Exception {
    assertTrue(port > 0);
    assertEquals(
        "pipeline-coordinator-service", environment.getProperty("spring.application.name"));
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
    var paths =
        mappings.getHandlerMethods().keySet().stream()
            .flatMap(m -> m.getPatternValues().stream())
            .filter(path -> path.startsWith("/internal/") || path.startsWith("/api/"))
            .collect(java.util.stream.Collectors.toSet());
    assertEquals(
        java.util.Set.of(
            "/internal/v1/runs",
            "/internal/v1/runs/active",
            "/internal/v1/runs/{id}",
            "/internal/v1/runs/{id}/retry",
            "/internal/v1/runs/{id}/abandon",
            "/internal/v1/runs/{id}/skip",
            "/internal/v1/runs/{id}/publish",
            "/internal/v1/publications/current",
            "/internal/v1/publications/{id}",
            "/internal/v1/csrf"),
        paths);
  }
}
