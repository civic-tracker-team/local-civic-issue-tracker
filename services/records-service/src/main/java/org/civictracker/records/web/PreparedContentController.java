package org.civictracker.records.web;

import java.util.UUID;
import org.civictracker.records.application.PreparedContentReads;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class PreparedContentController {
  private final PreparedContentReads content;

  public PreparedContentController(PreparedContentReads content) {
    this.content = content;
  }

  @GetMapping("/internal/v1/document-versions/{id}/content")
  public PreparedContentReads.Content content(
      @PathVariable UUID id,
      @RequestParam(required = false) UUID preparationId,
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "20") int size) {
    return content.content(id, preparationId, page, size);
  }
}
