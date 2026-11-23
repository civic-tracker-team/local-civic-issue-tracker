package org.civictracker.analysis.web;

import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;

/** Supplies safe error details for MVC and security failures. */
public final class ReadProblems {
  private ReadProblems() {}

  public static ProblemDetail detail(int status, String code) {
    String correlationId = UUID.randomUUID().toString();
    ProblemDetail problem = ProblemDetail.forStatus(status);
    problem.setTitle(HttpStatus.valueOf(status).getReasonPhrase());
    problem.setDetail("The analysis request could not be completed.");
    problem.setProperty("code", code);
    problem.setProperty("correlationId", correlationId);
    problem.setInstance(java.net.URI.create("urn:uuid:" + correlationId));
    return problem;
  }

  public static void write(HttpServletResponse response, int status, String code)
      throws IOException {
    response.setStatus(status);
    response.setContentType("application/problem+json");
    response.setCharacterEncoding("UTF-8");
    response
        .getWriter()
        .write(
            "{\"type\":\"about:blank\",\"title\":\""
                + HttpStatus.valueOf(status).getReasonPhrase()
                + "\",\"status\":"
                + status
                + ",\"detail\":\"The analysis request could not be completed.\",\"code\":\""
                + code
                + "\",\"correlationId\":\""
                + UUID.randomUUID()
                + "\"}");
  }
}
