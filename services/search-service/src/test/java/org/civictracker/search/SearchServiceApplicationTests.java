package org.civictracker.search;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Set;
import java.util.stream.Collectors;
import org.civictracker.search.index.Indexer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.ApplicationContext;
import org.springframework.core.env.Environment;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
class SearchServiceApplicationTests {

  @LocalServerPort private int port;

  @Autowired private Environment environment;
  @Autowired private RequestMappingHandlerMapping mappings;
  @Autowired private ApplicationContext context;

  @Test
  @Timeout(30)
  void startsWithOnlyThePublicSearchRouteAndNoIndexerByDefault() throws Exception {
    assertTrue(port > 0);
    assertEquals("search-service", environment.getProperty("spring.application.name"));
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
    var routes =
        mappings.getHandlerMethods().keySet().stream()
            .flatMap(mapping -> mapping.getPatternValues().stream())
            .filter(path -> path.startsWith("/internal/") || path.startsWith("/api/"))
            .collect(Collectors.toSet());
    assertEquals(Set.of("/api/v1/search"), routes);
    assertTrue(context.getBeansOfType(Indexer.class).isEmpty());
  }
}
