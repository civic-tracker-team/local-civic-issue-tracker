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

@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
class AnalysisServiceApplicationTests {

  @LocalServerPort private int port;

  @Autowired private Environment environment;

  @Test
  @Timeout(30)
  void startsAnIndependentHttpServerWithoutProductRoutes() throws Exception {
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
      assertEquals(404, response.statusCode());
    }
  }
}
