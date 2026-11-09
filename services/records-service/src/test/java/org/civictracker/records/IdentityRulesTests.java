package org.civictracker.records;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.URI;
import java.time.Instant;
import org.civictracker.records.domain.IdentityRules;
import org.civictracker.records.domain.SourceInput;
import org.junit.jupiter.api.Test;

class IdentityRulesTests {
  @Test
  void advancementRequiresAnAuthoritativeStrictlyNewerObservation() {
    Instant now = Instant.parse("2026-11-08T12:00:00Z");
    assertTrue(IdentityRules.canAdvance(true, now, null));
    assertFalse(IdentityRules.canAdvance(false, now, null));
    assertFalse(IdentityRules.canAdvance(true, now, now));
    assertFalse(IdentityRules.canAdvance(true, now.minusSeconds(1), now));
    assertTrue(IdentityRules.canAdvance(true, now.plusSeconds(1), now));
  }

  @Test
  void unknownScopeIsNullRatherThanAnEmptyMatchingKey() {
    assertThrows(IllegalArgumentException.class, () -> input(" ", "https://example.invalid/file"));
    assertThrows(IllegalArgumentException.class, () -> input(null, "relative/file"));
    assertEquals(null, input(null, "HTTPS://example.invalid/file").itemId());
  }

  @Test
  void sourceUrlAndRawDatesArePreservedAndObservationUsesDatabasePrecision() {
    var source = input("item", "https://example.invalid/file?a=1&b=2#section");
    assertEquals("https://example.invalid/file?a=1&b=2#section", source.sourceUrl().toString());
    assertEquals("November 1, 2026; timezone unspecified", source.meetingDate());
    assertEquals(Instant.parse("2026-11-08T12:00:00.123456Z"), source.observedAt());
  }

  private SourceInput input(String item, String url) {
    return new SourceInput("portal", "meeting", null,
        "November 1, 2026; timezone unspecified", item, null, null, null, null,
        "attachment-history", "official-id", URI.create(url), "original.pdf",
        Instant.parse("2026-11-08T12:00:00.123456789Z"), true, null);
  }
}
