package org.civictracker.analysis.web;

import java.net.URI;
import java.util.Map;
import java.util.UUID;
import org.civictracker.analysis.api.AnalysisCommands.*;
import org.civictracker.analysis.application.AnalysisOperations;
import org.civictracker.analysis.application.FindingsStore;
import org.civictracker.analysis.domain.IssueHistory;
import org.civictracker.analysis.domain.SavedFindings;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.*;

/** Exposes analysis commands and immutable findings through internal DTOs. */
@RestController
@RequestMapping("/internal/v1")
public class AnalysisController {
  private final AnalysisOperations operations;
  private final FindingsStore findings;

  public AnalysisController(AnalysisOperations operations, FindingsStore findings) {
    this.operations = operations;
    this.findings = findings;
  }

  @PostMapping(value = "/analysis-operations", consumes = "application/json")
  public ResponseEntity<Status> analyze(@RequestBody Command command) {
    return accepted(operations.accept(command));
  }

  @PostMapping(value = "/analysis-operations/{id}/resume", consumes = "application/json")
  public ResponseEntity<Status> resume(@PathVariable UUID id, @RequestBody Resume confirmation) {
    return accepted(operations.resume(id, confirmation));
  }

  @GetMapping("/analysis-operations/{id}")
  public ResponseEntity<Status> status(@PathVariable UUID id) {
    return read(operations.status(id));
  }

  @GetMapping("/meeting-analyses/{revisionId}")
  public ResponseEntity<SavedFindings> findings(@PathVariable UUID revisionId) {
    return read(findings.read(revisionId));
  }

  @GetMapping("/issues/{id}")
  public ResponseEntity<IssueHistory> issue(@PathVariable UUID id) {
    return read(findings.issueHistory(id));
  }

  @GetMapping("/csrf")
  public ResponseEntity<Map<String, String>> csrf(CsrfToken token) {
    return read(Map.of("headerName", token.getHeaderName(), "token", token.getToken()));
  }

  private static ResponseEntity<Status> accepted(Status status) {
    return ResponseEntity.accepted()
        .location(URI.create("/internal/v1/analysis-operations/" + status.id()))
        .cacheControl(CacheControl.noStore())
        .body(status);
  }

  private static <T> ResponseEntity<T> read(T body) {
    return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(body);
  }
}
