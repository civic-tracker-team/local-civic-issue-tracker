package org.civictracker.coordinator.web;

import java.net.URI;
import java.security.Principal;
import java.util.Map;
import java.util.UUID;
import org.civictracker.coordinator.run.RunProblem;
import org.civictracker.coordinator.run.RunView;
import org.civictracker.coordinator.run.Runs;
import org.civictracker.coordinator.run.StartRun;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Exposes durable run control using DTOs rather than persistence entities. */
@RestController
@RequestMapping("/internal/v1")
public class RunController {
  private final Runs runs;

  public RunController(Runs runs) {
    this.runs = runs;
  }

  @PostMapping(value = "/runs", consumes = "application/json")
  public ResponseEntity<RunView> start(@RequestBody StartRun command, Principal caller) {
    return accepted(runs.start(command, caller.getName()));
  }

  @GetMapping("/runs/active")
  public ResponseEntity<RunView> active() {
    return read(runs.active().orElseThrow(() -> new RunProblem(404, "NO_ACTIVE_RUN")));
  }

  @GetMapping("/runs/{id}")
  public ResponseEntity<RunView> view(@PathVariable UUID id) {
    return read(runs.view(id));
  }

  @PostMapping(value = "/runs/{id}/retry", consumes = "application/json")
  public ResponseEntity<RunView> retry(
      @PathVariable UUID id, @RequestBody Retry command, Principal caller) {
    if (command.haltId() == null) {
      throw new RunProblem(400, "INVALID_RETRY");
    }
    return accepted(runs.retry(id, command.haltId(), caller.getName()));
  }

  @PostMapping(value = "/runs/{id}/abandon", consumes = "application/json")
  public ResponseEntity<RunView> abandon(
      @PathVariable UUID id, @RequestBody Abandon command, Principal caller) {
    return accepted(runs.abandon(id, command.reason(), caller.getName()));
  }

  @PostMapping(value = "/runs/{id}/skip", consumes = "application/json")
  public ResponseEntity<RunView> skip(
      @PathVariable UUID id, @RequestBody Skip command, Principal caller) {
    if (command.haltId() == null) {
      throw new RunProblem(400, "INVALID_SKIP");
    }
    return accepted(runs.skip(id, command.haltId(), command.reason(), caller.getName()));
  }

  public record Skip(Integer haltId, String reason) {}

  @PostMapping("/runs/{id}/publish")
  public ResponseEntity<RunView> publish(@PathVariable UUID id) {
    return accepted(runs.publish(id));
  }

  @GetMapping("/csrf")
  public ResponseEntity<Map<String, String>> csrf(CsrfToken token) {
    return read(Map.of("headerName", token.getHeaderName(), "token", token.getToken()));
  }

  private static ResponseEntity<RunView> accepted(RunView view) {
    return ResponseEntity.accepted()
        .location(URI.create("/internal/v1/runs/" + view.runId()))
        .cacheControl(CacheControl.noStore())
        .body(view);
  }

  private static <T> ResponseEntity<T> read(T value) {
    return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(value);
  }

  public record Retry(Integer haltId) {}

  public record Abandon(String reason) {}
}
