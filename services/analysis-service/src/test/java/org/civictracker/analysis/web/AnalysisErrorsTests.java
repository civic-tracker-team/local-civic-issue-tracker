package org.civictracker.analysis.web;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.http.ProblemDetail;
import org.springframework.mock.web.MockHttpServletResponse;

class AnalysisErrorsTests {
  @Test
  void databaseFailureDoesNotExposeImplementationDetails() {
    var response =
        new AnalysisErrors()
            .unavailable(
                new DataAccessResourceFailureException("private-database-detail"),
                new MockHttpServletResponse());
    assertEquals(503, response.getStatusCode().value());
    var body = (ProblemDetail) response.getBody();
    assertEquals("ANALYSIS_UNAVAILABLE", body.getProperties().get("code"));
    assertFalse(body.toString().contains("private-database-detail"));
  }

  @Test
  void streamFailureAfterHeadersDoesNotAppendAJsonError() throws Exception {
    var response = new MockHttpServletResponse();
    response.flushBuffer();
    assertEquals(
        null, new AnalysisErrors().unavailable(new java.io.IOException("interrupted"), response));
    assertEquals("", response.getContentAsString());
  }

  @Test
  void connectionAndProviderFailuresRemainServiceUnavailable() {
    for (Exception error :
        new Exception[] {
          new org.springframework.transaction.CannotCreateTransactionException("private-connect"),
          new jakarta.persistence.PersistenceException("private-provider")
        }) {
      var response = new AnalysisErrors().unavailable(error, new MockHttpServletResponse());
      assertEquals(503, response.getStatusCode().value());
      assertFalse(response.getBody().toString().contains(error.getMessage()));
    }
  }

  @Test
  void unexpectedFailureIsSanitized() {
    var response =
        new AnalysisErrors()
            .unexpected(new RuntimeException("private-detail"), new MockHttpServletResponse());
    assertEquals(500, response.getStatusCode().value());
    assertFalse(response.getBody().toString().contains("private-detail"));
  }
}
