package org.civictracker.analysis;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;
import org.civictracker.analysis.configuration.AnalysisSecurity.Credentials;
import org.civictracker.analysis.web.ReadProblems;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletResponse;

class AnalysisSecurityTests {
  @Test
  void emptyConfigurationGrantsNoIdentities() {
    assertTrue(new Credentials(null, null, null).callers().isEmpty());
    assertTrue(new Credentials(Map.of(), Map.of(), Map.of()).callers().isEmpty());
  }

  @Test
  void rejectsPlaintextAndMalformedHashesRatherThanStartingWithWeakCredentials() {
    for (String value :
        new String[] {
          "plain-password",
          "{noop}plain-password",
          "{bcrypt}bad",
          "{bcrypt}$2a$00$" + "a".repeat(53)
        }) {
      assertThrows(
          IllegalArgumentException.class,
          () -> new Credentials(Map.of("caller", value), Map.of(), Map.of()));
    }
  }

  @Test
  void securityProblemContainsSafeStableFields() throws Exception {
    var response = new MockHttpServletResponse();
    ReadProblems.write(response, 403, "ACCESS_DENIED");
    assertEquals(403, response.getStatus());
    assertTrue(response.getContentAsString().contains("correlationId"));
    assertEquals("application/problem+json;charset=UTF-8", response.getContentType());
  }
}
