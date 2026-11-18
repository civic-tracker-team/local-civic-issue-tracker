package org.civictracker.analysis;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;

import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.time.Duration;
import org.civictracker.analysis.evaluation.JsonHttp;
import org.junit.jupiter.api.Test;

class JsonHttpTests {
  @Test
  void responseDeadlineCoversTheBodyAfterHeaders() throws Exception {
    var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    server.createContext(
        "/slow",
        exchange -> {
          exchange.sendResponseHeaders(200, 2);
          exchange.getResponseBody().flush();
          try {
            Thread.sleep(500);
          } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
          }
          try {
            exchange.getResponseBody().write("{}".getBytes());
          } finally {
            exchange.close();
          }
        });
    server.start();
    try {
      var http =
          new JsonHttp(
              URI.create("http://127.0.0.1:" + server.getAddress().getPort()),
              Duration.ofMillis(100),
              null);
      assertTimeoutPreemptively(
          Duration.ofSeconds(2), () -> assertThrows(IOException.class, () -> http.get("/slow")));
    } finally {
      server.stop(0);
    }
  }
}
