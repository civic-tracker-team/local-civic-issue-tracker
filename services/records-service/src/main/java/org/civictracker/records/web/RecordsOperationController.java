package org.civictracker.records.web;

import java.net.URI;
import java.util.Map;
import java.util.UUID;
import org.civictracker.records.api.OperationViews;
import org.civictracker.records.application.PreparedContentReads;
import org.civictracker.records.application.RecordPreparation;
import org.civictracker.records.application.RecordsOperations;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Exposes durable processing commands behind explicit service permissions. */
@RestController
@RequestMapping("/internal/v1")
public class RecordsOperationController {
  private final RecordsOperations operations;
  private final RecordPreparation preparation;
  private final PreparedContentReads content;

  public RecordsOperationController(
      RecordsOperations operations, RecordPreparation preparation, PreparedContentReads content) {
    this.operations = operations;
    this.preparation = preparation;
    this.content = content;
  }

  @PostMapping(value = "/preparation-operations", consumes = "application/json")
  public ResponseEntity<OperationViews.Status> prepare(
      @RequestBody OperationViews.PreparationCommand command) {
    return accepted(preparation.accept(command));
  }

  @GetMapping("/operations/{id}/preparations")
  public OperationViews.Page<OperationViews.PreparationResult> preparations(
      @PathVariable UUID id,
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "20") int size) {
    return content.results(id, page, size);
  }

  @GetMapping("/csrf")
  public ResponseEntity<Map<String, String>> csrf(CsrfToken token) {
    return ResponseEntity.ok()
        .cacheControl(CacheControl.noStore())
        .body(Map.of("headerName", token.getHeaderName(), "token", token.getToken()));
  }

  @PostMapping(value = "/discovery-operations", consumes = "application/json")
  public ResponseEntity<OperationViews.Status> discover(
      @RequestBody OperationViews.DiscoveryCommand command) {
    return accepted(operations.discover(command));
  }

  @PostMapping(value = "/acquisition-operations", consumes = "application/json")
  public ResponseEntity<OperationViews.Status> acquire(
      @RequestBody OperationViews.AcquisitionCommand command) {
    return accepted(operations.acquire(command));
  }

  @PostMapping(value = "/operations/{id}/resume", consumes = "application/json")
  public ResponseEntity<OperationViews.Status> resume(
      @PathVariable UUID id, @RequestBody OperationViews.Resume confirmation) {
    return accepted(operations.resume(id, confirmation));
  }

  @GetMapping("/operations/{id}")
  public OperationViews.Status status(@PathVariable UUID id) {
    return operations.status(id);
  }

  @GetMapping("/operations/{id}/meetings")
  public OperationViews.Page<OperationViews.Discovered> meetings(
      @PathVariable UUID id,
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "20") int size) {
    return operations.meetings(id, page, size);
  }

  @GetMapping("/operations/{id}/originals")
  public OperationViews.Page<OperationViews.Original> originals(
      @PathVariable UUID id,
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "20") int size) {
    return operations.originals(id, page, size);
  }

  private ResponseEntity<OperationViews.Status> accepted(OperationViews.Status status) {
    return ResponseEntity.accepted()
        .location(URI.create("/internal/v1/operations/" + status.id()))
        .cacheControl(CacheControl.noStore())
        .body(status);
  }
}
