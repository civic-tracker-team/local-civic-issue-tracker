package org.civictracker.coordinator.web;

import java.util.UUID;
import org.civictracker.coordinator.publication.Manifest;
import org.civictracker.coordinator.publication.Publications;
import org.civictracker.coordinator.run.RunProblem;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/internal/v1/publications")
public class PublicationController {
  private final Publications publications;

  public PublicationController(Publications publications) {
    this.publications = publications;
  }

  @GetMapping("/current")
  public ResponseEntity<Manifest> current() {
    return ResponseEntity.ok()
        .header("Cache-Control", "no-store")
        .body(publications.current().orElseThrow(() -> new RunProblem(404, "NO_PUBLICATION")));
  }

  @GetMapping("/{id}")
  public ResponseEntity<Manifest> byId(@PathVariable UUID id) {
    return ResponseEntity.ok()
        .body(
            publications.byId(id).orElseThrow(() -> new RunProblem(404, "PUBLICATION_NOT_FOUND")));
  }
}
