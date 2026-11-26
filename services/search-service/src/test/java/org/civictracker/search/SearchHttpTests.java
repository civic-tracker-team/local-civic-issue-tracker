package org.civictracker.search;

import static org.civictracker.search.SyntheticPublication.*;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.StreamSupport;
import org.civictracker.search.index.Indexer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import tools.jackson.databind.JsonNode;

/**
 * V-02, V-23 and V-29 checks against a real PostgreSQL index built from loopback owners. The
 * fixture's words outside the manifest ("skate", "zeppelin", "zebra") must never be found.
 */
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
class SearchHttpTests {
  static final OwnerStub OWNERS;

  static {
    try {
      OWNERS = new OwnerStub();
    } catch (java.io.IOException error) {
      throw new ExceptionInInitializerError(error);
    }
  }

  @DynamicPropertySource
  static void owners(DynamicPropertyRegistry registry) {
    registry.add("search.indexer.enabled", () -> "true");
    registry.add("search.indexer.initial-delay", () -> "PT24H");
    for (var owner : List.of("coordinator", "records", "analysis")) {
      registry.add("search.owners." + owner + ".base-url", OWNERS::baseUrl);
      registry.add("search.owners." + owner + ".user", () -> OwnerStub.USER);
      registry.add("search.owners." + owner + ".password", () -> OwnerStub.PASSWORD);
    }
  }

  @AfterAll
  static void stop() {
    OWNERS.close();
  }

  @LocalServerPort int port;
  @Autowired Indexer indexer;
  @Autowired JdbcTemplate jdbc;
  final HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
  final SyntheticPublication fixture = new SyntheticPublication(OWNERS);

  @BeforeEach
  void empty() {
    OWNERS.reset();
    jdbc.update("update search.current_index set build_id = null");
    jdbc.update("delete from search.index_build");
  }

  @Test
  void noPublishedIndexIsAnExplicitUnavailableAnswer() throws Exception {
    assertEquals(Indexer.Outcome.NO_PUBLICATION, indexer.refresh());
    var response = get("/api/v1/search?q=parking");
    assertEquals(503, response.statusCode());
    assertEquals("NO_PUBLISHED_INDEX", json(response).path("code").asString());
  }

  @Test
  void referenceQueriesReturnTheExpectedPublishedMatches() throws Exception {
    publishFirst();
    var parking = search("parking structure", null);
    // The sidewalk issue's own text never says "parking", so it is not a match.
    assertEquals(List.of(PARKING.toString()), ids(parking.path("issues")));
    // The issue is named by its latest pinned mention.
    assertEquals("Third Street Parking Structure", parking.path("issues").get(0).path("label").asString());
    assertEquals(
        Set.of(AGENDA.toString(), STAFF_REPORT.toString(), MINUTES.toString()),
        Set.copyOf(ids(parking.path("records"))));
    assertEquals(Set.of(M1.toString(), M2.toString()), Set.copyOf(ids(parking.path("meetings"))));
    assertEquals(3, parking.path("totals").path("records").asLong());

    var cost = search("cost estimate", "records");
    var staff = cost.path("records").get(0);
    assertEquals(STAFF_REPORT.toString(), staff.path("id").asString());
    assertEquals(V_STAFF.toString(), staff.path("versionId").asString());
    assertEquals("Page 2", staff.path("location").asString());
    assertEquals("Attachment", staff.path("details").path("recordType").asString());
    assertTrue(
        StreamSupport.stream(staff.path("snippet").spliterator(), false)
            .anyMatch(f -> f.path("match").asBoolean() && f.path("text").asString().equals("cost")));

    var agenda = search("third street design", "records").path("records").get(0);
    assertEquals(AGENDA.toString(), agenda.path("id").asString());
    assertEquals("Third Street Parking Structure", agenda.path("location").asString());
    assertEquals("Agenda", agenda.path("details").path("recordType").asString());
  }

  @Test
  void eachTypeSelectionReturnsOnlyTheSelectedTypes() throws Exception {
    publishFirst();
    var types = List.of("issues", "records", "meetings");
    for (int mask = 1; mask < 8; mask++) {
      var selected = new ArrayList<String>();
      for (int i = 0; i < 3; i++) if ((mask & (1 << i)) != 0) selected.add(types.get(i));
      var result = search("parking", String.join(",", selected));
      for (var type : types) {
        assertEquals(selected.contains(type), result.has(type), type + " in " + selected);
        assertEquals(selected.contains(type), result.path("totals").has(type), type + " total");
        if (selected.contains(type)) {
          assertFalse(result.path(type).isEmpty(), type + " should match parking");
        }
      }
      assertEquals(selected, strings(result.path("query").path("types")));
    }
    // Repeated parameters are the same selection as a comma-separated list.
    var repeated = json(get("/api/v1/search?q=parking&types=meetings&types=issues"));
    assertEquals(List.of("issues", "meetings"), strings(repeated.path("query").path("types")));
  }

  @Test
  void aRecordWithoutAnIssueRemainsFindableAndOpenable() throws Exception {
    publishFirst();
    var library = search("weekend library", "records,issues");
    assertEquals(0, library.path("totals").path("issues").asLong());
    var record = library.path("records").get(0);
    assertEquals(LIBRARY.toString(), record.path("id").asString());
    assertEquals(V_LIBRARY.toString(), record.path("versionId").asString());
    assertTrue(record.path("details").path("issues").isEmpty());
    assertEquals(M1.toString(), record.path("details").path("meetingId").asString());
    assertTrue(record.path("details").path("textSearched").asBoolean());
    // Snippets are plain-text fragments: nothing is escaped or returned as markup, and the
    // internal match markers never leak. ts_headline drops tag-like text such as <b>.
    var snippet = new StringBuilder();
    record.path("snippet").forEach(f -> snippet.append(f.path("text").asString()));
    var text = snippet.toString();
    assertTrue(text.contains("Read") && text.contains("& enjoy"), text);
    assertFalse(text.contains("&amp;") || text.contains("\u0001") || text.contains("\u0002"), text);
  }

  @Test
  void inferredIssueLinksAreMarkedOnRecordsAndIssues() throws Exception {
    publishFirst();
    var minutes = search("sidewalk", "records,issues");
    var issues = minutes.path("records").get(0).path("details").path("issues");
    assertEquals(2, issues.size());
    for (var issue : issues) {
      assertEquals(
          issue.path("id").asString().equals(SIDEWALK.toString()),
          issue.path("inferred").asBoolean());
    }
    var sidewalk = minutes.path("issues").get(0);
    assertEquals(SIDEWALK.toString(), sidewalk.path("id").asString());
    assertTrue(sidewalk.path("details").path("inferredOnly").asBoolean());
  }

  @Test
  void nothingOutsideThePinnedManifestIsIndexed() throws Exception {
    publishFirst();
    for (var word : List.of("skate", "zeppelin", "zebra", "garage")) {
      var result = search(word, null);
      for (var type : List.of("issues", "records", "meetings")) {
        assertEquals(0, result.path("totals").path(type).asLong(), word + " in " + type);
      }
    }
    assertFalse(OWNERS.requested.contains("/internal/v1/meeting-analyses/" + R_ABANDONED));
    assertFalse(OWNERS.requested.stream().anyMatch(p -> p.contains(V_STAFF_NEWER.toString())));
    assertFalse(OWNERS.requested.stream().anyMatch(p -> p.startsWith("/internal/v1/issues/")));
  }

  @Test
  void emptyResultsStillDescribeThePublishedCollection() throws Exception {
    publishFirst();
    var result = search("xylophone", null);
    for (var type : List.of("issues", "records", "meetings")) {
      assertEquals(0, result.path("totals").path(type).asLong());
      assertTrue(result.path(type).isEmpty());
    }
    var collection = result.path("collection");
    assertEquals(P1.toString(), collection.path("publicationId").asString());
    assertEquals("2026-10-01", collection.path("coverage").get(0).path("from").asString());
    assertEquals("2026-10-31", collection.path("coverage").get(0).path("to").asString());
    assertEquals(List.of("Agenda", "Attachment", "Minutes"), strings(collection.path("recordTypes")));
    assertEquals(3, collection.path("meetingCount").asInt());
    assertEquals(0, collection.path("recordsSearchedByTitleOnly").asInt());
  }

  @Test
  void aCancelledMeetingIsPartOfThePublishedCollection() throws Exception {
    publishFirst();
    var meeting = search("planning commission", "meetings").path("meetings").get(0);
    assertEquals(M3.toString(), meeting.path("id").asString());
    assertEquals("CANCELLED", meeting.path("details").path("disposition").asString());
    assertEquals("2026-10-28", meeting.path("date").asString());
  }

  @Test
  void aFailedRebuildKeepsServingThePreviousCollection() throws Exception {
    publishFirst();
    fixture.publishSecond();
    OWNERS.fail(SyntheticPublication.contentPath(V_BUDGET, "P-budget", 0), 503);
    assertEquals(Indexer.Outcome.FAILED, indexer.refresh());
    var stale = search("parking", "meetings");
    assertEquals(P1.toString(), stale.path("collection").path("publicationId").asString());
    assertEquals(0, search("surplus", null).path("totals").path("records").asLong());

    OWNERS.reset();
    fixture.publishFirst();
    fixture.publishSecond();
    fixture.citeUnpinnedVersion();
    assertEquals(Indexer.Outcome.FAILED, indexer.refresh());
    assertEquals(P1.toString(), search("parking", null).path("collection").path("publicationId").asString());

    fixture.restoreFirstRevision();
    assertEquals(Indexer.Outcome.BUILT, indexer.refresh());
    var fresh = search("surplus", null);
    assertEquals(P2.toString(), fresh.path("collection").path("publicationId").asString());
    assertEquals(BUDGET.toString(), fresh.path("records").get(0).path("id").asString());
    assertEquals(BUDGET_ISSUE.toString(), fresh.path("issues").get(0).path("id").asString());
    assertEquals("2026-11-30", fresh.path("collection").path("coverage").get(0).path("to").asString());
    assertEquals(1, jdbc.queryForObject("select count(*) from search.index_build", Integer.class));
    assertEquals(Indexer.Outcome.UNCHANGED, indexer.refresh());
  }

  @Test
  void requestsOutsideTheContractAreRejectedWithoutSessions() throws Exception {
    publishFirst();
    assertProblem(get("/api/v1/search?q=%20%20"), 400, "INVALID_QUERY");
    assertProblem(get("/api/v1/search?q=" + "a".repeat(201)), 400, "INVALID_QUERY");
    assertProblem(get("/api/v1/search?q=parking&types=documents"), 400, "INVALID_TYPE");
    assertProblem(get("/api/v1/search?q=parking&types="), 400, "INVALID_TYPE");
    assertProblem(get("/api/v1/search?q=parking&limit=0"), 400, "INVALID_PAGE");
    assertProblem(get("/api/v1/search?q=parking&limit=51"), 400, "INVALID_PAGE");
    assertProblem(get("/api/v1/search?q=parking&offset=-1"), 400, "INVALID_PAGE");
    assertProblem(get("/api/v1/search?q=parking&limit=ten"), 400, "INVALID_REQUEST");
    assertProblem(get("/api/v1/search"), 400, "INVALID_REQUEST");
    assertProblem(get("/internal/v1/anything"), 404, "NOT_FOUND");
    var post =
        client.send(
            HttpRequest.newBuilder(uri("/api/v1/search?q=parking"))
                .POST(HttpRequest.BodyPublishers.noBody())
                .build(),
            HttpResponse.BodyHandlers.ofString());
    assertEquals(404, post.statusCode());
    var ok = get("/api/v1/search?q=parking");
    assertEquals(200, ok.statusCode());
    assertTrue(ok.headers().allValues("Set-Cookie").isEmpty());
    assertEquals("no-store", ok.headers().firstValue("Cache-Control").orElseThrow());
    // Stop words alone match nothing rather than failing.
    assertEquals(0, search("the", "records").path("totals").path("records").asLong());
  }

  @Test
  void pagingAppliesWithinEachSelectedType() throws Exception {
    publishFirst();
    var all = ids(search("parking", "records").path("records"));
    var first = json(get("/api/v1/search?q=parking&types=records&limit=1"));
    var second = json(get("/api/v1/search?q=parking&types=records&limit=1&offset=1"));
    assertEquals(List.of(all.get(0)), ids(first.path("records")));
    assertEquals(List.of(all.get(1)), ids(second.path("records")));
    assertEquals(all.size(), second.path("totals").path("records").asLong());
  }

  private void publishFirst() throws Exception {
    fixture.publishFirst();
    assertEquals(Indexer.Outcome.BUILT, indexer.refresh());
  }

  private JsonNode search(String q, String types) throws Exception {
    var path = "/api/v1/search?q=" + URLEncoder.encode(q, StandardCharsets.UTF_8)
        + (types == null ? "" : "&types=" + types);
    var response = get(path);
    assertEquals(200, response.statusCode(), response.body());
    return json(response);
  }

  private HttpResponse<String> get(String path) throws Exception {
    return client.send(
        HttpRequest.newBuilder(uri(path)).timeout(Duration.ofSeconds(10)).GET().build(),
        HttpResponse.BodyHandlers.ofString());
  }

  private URI uri(String path) {
    return URI.create("http://127.0.0.1:" + port + path);
  }

  private void assertProblem(HttpResponse<String> response, int status, String code) {
    assertEquals(status, response.statusCode(), response.body());
    assertEquals(code, json(response).path("code").asString());
    assertTrue(json(response).path("correlationId").isString());
  }

  private static JsonNode json(HttpResponse<String> response) {
    return OwnerStub.JSON.readTree(response.body());
  }

  private static List<String> ids(JsonNode hits) {
    var ids = new ArrayList<String>();
    hits.forEach(h -> ids.add(h.path("id").asString()));
    return ids;
  }

  private static List<String> strings(JsonNode values) {
    var result = new ArrayList<String>();
    values.forEach(v -> result.add(v.asString()));
    return result;
  }
}
