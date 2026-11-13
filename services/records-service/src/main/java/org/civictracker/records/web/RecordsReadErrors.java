package org.civictracker.records.web;

import jakarta.persistence.PersistenceException;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.civictracker.records.application.RecordUnavailable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.CannotCreateTransactionException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.ServletWebRequest;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

/** Keeps implementation details out of API failures. */
@RestControllerAdvice
public class RecordsReadErrors extends ResponseEntityExceptionHandler {
  private static final Logger LOG = LoggerFactory.getLogger(RecordsReadErrors.class);

  @Override
  protected ResponseEntity<Object> handleExceptionInternal(Exception exception, Object body,
      HttpHeaders headers, HttpStatusCode status, WebRequest request) {
    if (request instanceof ServletWebRequest servlet
        && servlet.getResponse() != null && servlet.getResponse().isCommitted()) {
      return null;
    }
    String code = status.value() == 404 ? "RECORD_NOT_FOUND" : "INVALID_REQUEST";
    return new ResponseEntity<>(ReadProblems.detail(status.value(), code), headers, status);
  }

  @ExceptionHandler(RecordUnavailable.class)
  ResponseEntity<Object> missing(RecordUnavailable error) {
    return response(404, "RECORD_NOT_FOUND");
  }

  @ExceptionHandler({IOException.class, DataAccessException.class, PersistenceException.class,
      CannotCreateTransactionException.class})
  ResponseEntity<Object> unavailable(Exception error, HttpServletResponse response) {
    if (response.isCommitted()) {
      LOG.warn("Retained artifact response ended before completion", error);
      return null;
    }
    return failure(503, "RECORDS_UNAVAILABLE", error);
  }

  @ExceptionHandler(Exception.class)
  ResponseEntity<Object> unexpected(Exception error, HttpServletResponse response) {
    if (response.isCommitted()) {
      LOG.warn("Records response ended before completion", error);
      return null;
    }
    return failure(500, "RECORDS_ERROR", error);
  }

  private ResponseEntity<Object> failure(int status, String code, Exception error) {
    var problem = ReadProblems.detail(status, code);
    LOG.warn("Records read failed [{}]", problem.getProperties().get("correlationId"), error);
    return ResponseEntity.status(status).body(problem);
  }

  private ResponseEntity<Object> response(int status, String code) {
    return ResponseEntity.status(status).body(ReadProblems.detail(status, code));
  }
}
