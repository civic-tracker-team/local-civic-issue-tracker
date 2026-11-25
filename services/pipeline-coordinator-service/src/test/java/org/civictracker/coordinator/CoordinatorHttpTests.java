package org.civictracker.coordinator;

import static org.junit.jupiter.api.Assertions.*;

import java.net.CookieManager;
import java.net.CookiePolicy;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDate;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.civictracker.coordinator.run.RunState;
import org.civictracker.coordinator.run.Runs;
import org.civictracker.coordinator.run.StartRun;
import org.civictracker.coordinator.run.StepKind;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import tools.jackson.databind.JsonNode;

@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = {
      "coordinator.preparation-profile-id=synthetic-profile",
      "coordinator.stall-after=PT1M",
      "coordinator.records.username=owner",
      "coordinator.records.password=synthetic-password",
      "coordinator.records.timeout-seconds=5",
      "coordinator.analysis.username=owner",
      "coordinator.analysis.password=synthetic-password",
      "coordinator.analysis.timeout-seconds=5"
    })
@Timeout(40)
class CoordinatorHttpTests {
  static final CoordinatorOwners OWNERS = new CoordinatorOwners();
  static final String PASSWORD = UUID.randomUUID().toString();
  static final String HASH = "{bcrypt}" + new BCryptPasswordEncoder(4).encode(PASSWORD);

  @DynamicPropertySource
  static void properties(DynamicPropertyRegistry r) {
    r.add("coordinator.records.base-url", OWNERS.records::base);
    r.add("coordinator.analysis.base-url", OWNERS.analysis::base);
    r.add("coordinator.security.maintainers.maintainer", () -> HASH);
    r.add("coordinator.security.publication-readers.reader", () -> HASH);
  }

  @AfterAll
  static void closeOwners() {
    OWNERS.close();
  }

  @LocalServerPort int port;
  @Autowired Runs runs;
  @Autowired JdbcTemplate jdbc;
  HttpClient client;
  String csrfHeader;

  @BeforeEach
  void open() {
    runs.active().ifPresent(r -> runs.abandon(r.runId(), "HTTP fixture cleanup", "maintainer"));
    OWNERS.reset();
    client =
        HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .cookieHandler(new CookieManager(null, CookiePolicy.ACCEPT_ALL))
            .build();
  }

  @AfterEach
  void close() {
    runs.active().ifPresent(r -> runs.abandon(r.runId(), "HTTP fixture cleanup", "maintainer"));
    client.close();
    assertNull(OWNERS.records.failure.get());
    assertNull(OWNERS.analysis.failure.get());
  }

  StartRun command() {
    return new StartRun(
        UUID.randomUUID(),
        "synthetic",
        LocalDate.of(2026, 1, 1),
        LocalDate.of(2026, 1, 31),
        List.of("council"));
  }

  JsonNode read(HttpResponse<String> response) {
    return CoordinatorOwners.JSON.readTree(response.body());
  }

  String csrf() throws Exception {
    var response = send("GET", "/csrf", "maintainer", null, null);
    assertEquals(200, response.statusCode());
    csrfHeader = read(response).get("headerName").stringValue();
    return read(response).get("token").stringValue();
  }

  HttpResponse<String> send(String method, String path, String user, Object body, String token)
      throws Exception {
    // Basic authentication clears the preceding cookie. Each write needs a fresh token.
    if (token != null) {
      token = csrf();
    }
    var request =
        HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + "/internal/v1" + path))
            .timeout(Duration.ofSeconds(10));
    if (user != null) {
      request.header(
          "Authorization",
          "Basic "
              + Base64.getEncoder()
                  .encodeToString((user + ":" + PASSWORD).getBytes(StandardCharsets.UTF_8)));
    }
    if (token != null) {
      request.header(csrfHeader, token);
    }
    if (method.equals("GET")) {
      request.GET();
    } else {
      request
          .header("Content-Type", "application/json")
          .POST(
              HttpRequest.BodyPublishers.ofString(
                  body instanceof String text
                      ? text
                      : CoordinatorOwners.JSON.writeValueAsString(body)));
    }
    return client.send(request.build(), HttpResponse.BodyHandlers.ofString());
  }

  @Test
  void maintainerStartAndReadUseDurableDtosAndLocationWithoutEntityFields() throws Exception {
    var command = command();
    String token = csrf();
    var response = send("POST", "/runs", "maintainer", command, token);
    assertEquals(202, response.statusCode());
    assertEquals(
        "/internal/v1/runs/" + command.runId(),
        response.headers().firstValue("Location").orElseThrow());
    assertEquals("no-store", response.headers().firstValue("cache-control").orElseThrow());
    var body = read(response);
    assertEquals(command.runId().toString(), body.get("runId").stringValue());
    assertEquals("ACTIVE", body.get("state").stringValue());
    assertEquals("DISCOVERING", body.get("phase").stringValue());
    assertEquals(202, send("POST", "/runs", "maintainer", command, token).statusCode());
    assertEquals(
        command.runId().toString(),
        read(send("GET", "/runs/active", "maintainer", null, null)).get("runId").stringValue());
    assertEquals(body, read(send("GET", "/runs/" + command.runId(), "maintainer", null, null)));
    for (String forbidden :
        List.of(
            "isNew",
            "fresh",
            "commandSha256",
            "frozenRequestJson",
            "acceptedBy",
            "hibernate",
            "jdbc:")) {
      assertFalse(response.body().contains(forbidden), forbidden);
    }
    assertEquals(
        "maintainer",
        jdbc.queryForObject(
            "select accepted_by from coordinator.run where id=?", String.class, command.runId()));
  }

  @Test
  void readersCannotControlRunsAndUnknownRoutesAndWritesAreDenied() throws Exception {
    var unauthenticated = send("GET", "/runs/active", null, null, null);
    assertEquals(401, unauthenticated.statusCode());
    assertEquals("AUTHENTICATION_REQUIRED", read(unauthenticated).get("code").stringValue());
    assertEquals(403, send("GET", "/csrf", "reader", null, null).statusCode());
    assertEquals(403, send("GET", "/runs/active", "reader", null, null).statusCode());
    String token = csrf();
    assertEquals(403, send("POST", "/runs", "reader", command(), token).statusCode());
    assertEquals(403, send("GET", "/not-implemented", "maintainer", null, null).statusCode());
    assertEquals(
        403,
        send("POST", "/runs/" + UUID.randomUUID() + "/resume", "maintainer", Map.of(), token)
            .statusCode());
    assertEquals(
        404,
        send("POST", "/runs/" + UUID.randomUUID() + "/publish", "maintainer", Map.of(), token)
            .statusCode());
    assertEquals(404, send("GET", "/publications/current", "reader", null, null).statusCode());
    assertTrue(runs.active().isEmpty());
  }

  @Test
  void csrfIsRequiredOnEveryRunWrite() throws Exception {
    var command = command();
    assertEquals(403, send("POST", "/runs", "maintainer", command, null).statusCode());
    assertTrue(runs.active().isEmpty());
    String token = csrf();
    assertEquals(202, send("POST", "/runs", "maintainer", command, token).statusCode());
    assertEquals(
        403,
        send(
                "POST",
                "/runs/" + command.runId() + "/abandon",
                "maintainer",
                Map.of("reason", "Synthetic stop"),
                null)
            .statusCode());
    assertEquals(RunState.ACTIVE, runs.view(command.runId()).state());
    assertEquals(
        202,
        send(
                "POST",
                "/runs/" + command.runId() + "/abandon",
                "maintainer",
                Map.of("reason", "Synthetic stop"),
                token)
            .statusCode());
    assertTrue(runs.active().isEmpty());
  }

  @Test
  void missingIdsMalformedBodiesAndInvalidDatesHaveSanitizedErrors() throws Exception {
    assertEquals(
        "NO_ACTIVE_RUN",
        read(send("GET", "/runs/active", "maintainer", null, null)).get("code").stringValue());
    assertEquals(
        "RUN_NOT_FOUND",
        read(send("GET", "/runs/" + UUID.randomUUID(), "maintainer", null, null))
            .get("code")
            .stringValue());
    assertEquals(400, send("GET", "/runs/not-a-uuid", "maintainer", null, null).statusCode());
    String token = csrf();
    var command = command();
    for (Object bad :
        List.of(
            "{",
            "{\"runId\":\"not-a-uuid\"}",
            new StartRun(
                command.runId(),
                command.sourceId(),
                command.to(),
                command.from(),
                command.committeeIds()),
            new StartRun(command.runId(), "", command.from(), command.to(), List.of()))) {
      var response = send("POST", "/runs", "maintainer", bad, token);
      assertEquals(400, response.statusCode(), response.body());
      var body = read(response);
      assertTrue(body.has("code"));
      assertTrue(body.has("correlationId"));
      assertFalse(response.body().contains("org.civictracker"));
      assertFalse(response.body().contains("Exception"));
      assertFalse(response.body().contains(PASSWORD));
    }
    assertTrue(runs.active().isEmpty());
  }

  @Test
  void busyAndInputConflictResponsesDoNotReplaceTheAcceptedRun() throws Exception {
    String token = csrf();
    var command = command();
    send("POST", "/runs", "maintainer", command, token);
    var busy = send("POST", "/runs", "maintainer", command(), token);
    assertEquals(409, busy.statusCode());
    assertEquals(command.runId().toString(), read(busy).get("activeRunId").stringValue());
    var different =
        new StartRun(
            command.runId(), "different", command.from(), command.to(), command.committeeIds());
    var conflict = send("POST", "/runs", "maintainer", different, token);
    assertEquals(409, conflict.statusCode());
    assertEquals("RUN_ID_CONFLICT", read(conflict).get("code").stringValue());
    assertEquals(command.sourceId(), runs.view(command.runId()).command().sourceId());
  }

  @Test
  void retryAndAbandonRoutesPreserveTheHaltGuardAndAuditIdentity() throws Exception {
    String token = csrf();
    var command = command();
    OWNERS.records.failKind = StepKind.ACQUIRE;
    send("POST", "/runs", "maintainer", command, token);
    for (int n = 0; n < 15 && runs.view(command.runId()).state() == RunState.ACTIVE; n++) {
      runs.tick();
    }
    var halted = runs.view(command.runId());
    assertEquals(RunState.HALTED, halted.state());
    String route = "/runs/" + command.runId();
    assertEquals(400, send("POST", route + "/retry", "maintainer", Map.of(), token).statusCode());
    var response =
        send(
            "POST",
            route + "/retry",
            "maintainer",
            Map.of("haltId", halted.halt().haltId()),
            token);
    assertEquals(202, response.statusCode());
    assertEquals("ACTIVE", read(response).get("state").stringValue());
    assertEquals(2, runs.view(command.runId()).meetings().getFirst().attempts().acquire());
    assertEquals(
        400,
        send("POST", route + "/abandon", "maintainer", Map.of("reason", " "), token).statusCode());
    assertEquals(
        202,
        send("POST", route + "/abandon", "maintainer", Map.of("reason", "Synthetic reason"), token)
            .statusCode());
    assertEquals(
        "maintainer",
        jdbc.queryForObject(
            "select maintainer from coordinator.run_event where run_id=? and kind='ABANDONED'",
            String.class,
            command.runId()));
  }
}
