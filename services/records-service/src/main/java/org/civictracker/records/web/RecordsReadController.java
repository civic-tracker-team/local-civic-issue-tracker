package org.civictracker.records.web;

import java.io.IOException;
import java.util.UUID;
import org.civictracker.records.api.MeetingViews;
import org.civictracker.records.api.OperationViews;
import org.civictracker.records.api.RecordViews;
import org.civictracker.records.application.RecordsOperations;
import org.civictracker.records.application.RecordsReadService;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Exposes authenticated processing reads without a public publication view. */
@RestController
@RequestMapping("/internal/v1")
public class RecordsReadController {
  private final RecordsReadService reads;
  private final RecordsOperations operations;

  public RecordsReadController(RecordsReadService reads, RecordsOperations operations) {
    this.reads = reads;
    this.operations = operations;
  }

  @GetMapping("/meetings")
  public OperationViews.Page<MeetingViews.Summary> meetings(
      @RequestParam UUID discoveryId,
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "20") int size) {
    var result = operations.meetings(discoveryId, page, size);
    String sourceId = operations.sourceId(discoveryId);
    return new OperationViews.Page<>(
        result.items().stream()
            .map(item -> {
              var source = item.source();
              return new MeetingViews.Summary(
                  item.id(),
                  sourceId,
                  source.officialId(),
                  source.committeeId(),
                  source.title(),
                  source.sourceDate(),
                  source.publicationDate(),
                  source.videoUrl(),
                  source.cancellationEvidence());
            })
            .toList(),
        page,
        size,
        result.hasNext());
  }

  @GetMapping("/meetings/{id}")
  public MeetingViews.Detail meeting(@PathVariable UUID id) {
    return reads.meeting(id);
  }

  @GetMapping("/documents/{id}")
  public RecordViews.Document document(@PathVariable UUID id) {
    return reads.document(id);
  }

  @GetMapping("/document-versions/{id}")
  public RecordViews.Version version(@PathVariable UUID id) {
    return reads.version(id);
  }

  @GetMapping("/artifacts/{id}/bytes")
  public ResponseEntity<InputStreamResource> bytes(@PathVariable UUID id) throws IOException {
    var artifact = reads.artifact(id);
    var stream = reads.open(artifact);
    return ResponseEntity.ok()
        .contentType(MediaType.APPLICATION_OCTET_STREAM)
        .contentLength(artifact.byteLength())
        .header(
            HttpHeaders.CONTENT_DISPOSITION,
            ContentDisposition.attachment().filename(id + ".bin").build().toString())
        .body(new InputStreamResource(stream));
  }
}
