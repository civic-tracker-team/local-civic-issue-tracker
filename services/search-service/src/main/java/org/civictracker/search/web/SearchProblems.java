package org.civictracker.search.web;

import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;

/** RFC 9457 problems with a stable code and correlation ID, and no exception text. */
public final class SearchProblems {
  private SearchProblems() {}

  public static ProblemDetail problem(int status, String code) {
    var problem = ProblemDetail.forStatus(status);
    problem.setTitle(HttpStatus.valueOf(status).getReasonPhrase());
    problem.setProperty("code", code);
    problem.setProperty("correlationId", UUID.randomUUID().toString());
    return problem;
  }

  /** Writes a problem from a filter, before MVC handles the request. */
  public static void write(HttpServletResponse response, int status, String code)
      throws IOException {
    response.setStatus(status);
    response.setContentType("application/problem+json");
    response.setHeader("Cache-Control", "no-store");
    response.getWriter()
        .write(
            "{\"type\":\"about:blank\",\"title\":\""
                + HttpStatus.valueOf(status).getReasonPhrase()
                + "\",\"status\":"
                + status
                + ",\"code\":\""
                + code
                + "\",\"correlationId\":\""
                + UUID.randomUUID()
                + "\"}");
  }
}
