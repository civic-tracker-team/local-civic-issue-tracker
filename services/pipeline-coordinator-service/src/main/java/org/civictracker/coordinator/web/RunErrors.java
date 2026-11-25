package org.civictracker.coordinator.web;

import org.civictracker.coordinator.run.RunProblem;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

@RestControllerAdvice
public class RunErrors extends ResponseEntityExceptionHandler {
  @ExceptionHandler(RunProblem.class)
  public ResponseEntity<ProblemDetail> run(RunProblem error) {
    var detail = RunProblems.detail(error.status(), error.code());
    if (error.activeRunId() != null) {
      detail.setProperty("activeRunId", error.activeRunId());
    }
    return ResponseEntity.status(error.status()).body(detail);
  }

  @Override
  protected ResponseEntity<Object> handleExceptionInternal(
      Exception error,
      Object body,
      HttpHeaders headers,
      HttpStatusCode status,
      WebRequest request) {
    return new ResponseEntity<>(
        RunProblems.detail(status.value(), status.value() == 404 ? "NOT_FOUND" : "INVALID_REQUEST"),
        headers,
        status);
  }

  @ExceptionHandler(Exception.class)
  public ResponseEntity<ProblemDetail> unavailable(Exception error) {
    return ResponseEntity.internalServerError().body(RunProblems.detail(500, "REQUEST_FAILED"));
  }
}
