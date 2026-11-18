package org.civictracker.records;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;
import java.util.UUID;
import org.civictracker.records.api.OperationViews;
import org.civictracker.records.domain.PreparationData.OcrRegion;
import org.civictracker.records.domain.PreparationData.OcrResult;
import org.civictracker.records.domain.PreparationData.Placement;
import org.civictracker.records.domain.PreparationData.Point;
import org.civictracker.records.ocr.OcrClient;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

class OcrContractTests {
  @Test
  void ordinaryPreparationRequestsDoNotRequireTheOptionalCorrectiveRerunFlag() {
    var json = new ObjectMapper();
    var command =
        json.readValue(
            "{\"operationId\":\""
                + UUID.randomUUID()
                + "\",\"acquisitionOperationId\":\""
                + UUID.randomUUID()
                + "\",\"profileId\":\"fixture\"}",
            OperationViews.PreparationCommand.class);
    assertFalse(command.freshRevision());
  }

  @Test
  void nonOcrResumeRequestsRemainCompatibleWithTheExistingContract() {
    var json = new ObjectMapper();
    var command =
        json.readValue(
            "{\"attemptId\":\""
                + UUID.randomUUID()
                + "\",\"previousWorkerId\":\""
                + UUID.randomUUID()
                + "\",\"previousWorkerStopped\":true}",
            OperationViews.Resume.class);
    assertFalse(command.previousOcrStopped());
  }

  @Test
  void malformedModelGeometryAndIdentityDoNotBecomeUsableText() {
    UUID execution = UUID.randomUUID();
    var image = new Placement("a".repeat(64), 10, 10, List.of(1., 0., 0., 1., 0., 0.));
    var polygon = List.of(new Point(0, 0), new Point(10, 0), new Point(10, 10), new Point(0, 10));
    var profile = PreparationExtractionTests.profile(10000);
    for (var region :
        List.of(
            new OcrRegion("text", Double.NaN, polygon),
            new OcrRegion(
                "text",
                .9,
                List.of(
                    new Point(-1, 0), new Point(10, 0), new Point(10, 10), new Point(0, 10))))) {
      var result =
          new OcrResult(
              execution,
              image.imageSha256(),
              "fixture-v1",
              10,
              10,
              List.of(region),
              java.util.Map.of("fixture", true));
      assertThrows(
          IllegalArgumentException.class,
          () -> OcrClient.validate(result, execution, image, profile));
    }
    var wrong =
        new OcrResult(
            UUID.randomUUID(),
            image.imageSha256(),
            "fixture-v1",
            10,
            10,
            List.of(new OcrRegion("text", .9, polygon)),
            java.util.Map.of("fixture", true));
    assertThrows(
        IllegalArgumentException.class, () -> OcrClient.validate(wrong, execution, image, profile));
  }
}
