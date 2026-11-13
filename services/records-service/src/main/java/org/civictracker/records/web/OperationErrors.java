package org.civictracker.records.web;

import org.civictracker.records.application.OperationProblem;
import org.springframework.core.annotation.Order;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@Order(0)
@RestControllerAdvice
public class OperationErrors {
  @ExceptionHandler(OperationProblem.class)
  public ResponseEntity<ProblemDetail> operation(OperationProblem error) {
    return ResponseEntity.status(error.status())
        .body(ReadProblems.detail(error.status(), error.code()));
  }
}
