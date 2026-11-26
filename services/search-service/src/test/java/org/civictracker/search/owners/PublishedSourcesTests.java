package org.civictracker.search.owners;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class PublishedSourcesTests {
  static final UUID A = UUID.randomUUID();
  static final UUID B = UUID.randomUUID();

  @Test
  void indexesTheCitedPreparationOrTheOnlySealedOne() {
    assertEquals(A, PublishedSources.preparation(Set.of(A), Set.of(A, B)));
    assertEquals(A, PublishedSources.preparation(Set.of(), Set.of(A)));
    // Ambiguous or unsealed choices fall back to title-only search rather than guessing.
    assertNull(PublishedSources.preparation(Set.of(), Set.of(A, B)));
    assertNull(PublishedSources.preparation(Set.of(A, B), Set.of(A, B)));
    assertNull(PublishedSources.preparation(Set.of(A), Set.of(B)));
    assertNull(PublishedSources.preparation(Set.of(), Set.of()));
  }

  @Test
  void rejectsAManifestWhoseRevisionDisagreesWithItsDisposition() {
    for (var meeting :
        new String[] {
          "{\"meetingId\":\"" + A + "\",\"date\":\"2026-10-07\",\"disposition\":\"ANALYZED\"}",
          "{\"meetingId\":\"" + A + "\",\"date\":\"2026-10-07\",\"disposition\":\"SKIPPED\","
              + "\"revisionId\":\"" + B + "\"}"
        }) {
      var json =
          OwnerHttp.JSON.readTree(
              "{\"publicationId\":\"" + B + "\",\"coverage\":[],\"meetings\":[" + meeting + "]}");
      var error = assertThrows(OwnerUnavailable.class, () -> PublishedSources.manifest(json));
      assertEquals("INVALID_MANIFEST", error.code());
    }
  }

  @Test
  void labelsPdfPagesAndAgendaItems() {
    var pdf = OwnerHttp.JSON.readTree("{\"kind\":\"pdf\",\"page\":4,\"pageLabel\":\"iv\"}");
    var html = OwnerHttp.JSON.readTree("{\"kind\":\"html\",\"itemId\":\"9\"}");
    assertEquals("Page 4", PublishedSources.location(pdf, Map.of()));
    assertEquals("Budget", PublishedSources.location(html, Map.of("9", "Budget")));
    assertNull(PublishedSources.location(html, Map.of()));
  }

  @Test
  void removesControlCharactersButKeepsLineBreaks() {
    assertEquals("a\tb\nc\rd", Text.clean("a\tb\nc\rd\u0000\u0001\u0002\u007f"));
    assertNull(Text.clean(null));
  }

  @Test
  void ownerBaseUrlsMustBeHttpsOrExplicitLoopback() {
    var timeout = java.time.Duration.ofSeconds(1);
    for (var base :
        new String[] {
          "http://example.invalid", "http://localhost:1", "https://u@example.invalid",
          "https://example.invalid/prefix", "https://example.invalid?x=1"
        }) {
      assertThrows(
          IllegalArgumentException.class,
          () -> new OwnerHttp("records", java.net.URI.create(base), timeout, "u", "p"),
          base);
    }
    new OwnerHttp("records", java.net.URI.create("http://127.0.0.1:1"), timeout, "u", "p");
    new OwnerHttp("records", java.net.URI.create("https://records.invalid"), timeout, "u", "p");
    assertThrows(
        IllegalArgumentException.class,
        () -> new OwnerHttp("records", java.net.URI.create("https://r.invalid"), timeout, "a:b", "p"));
  }
}
