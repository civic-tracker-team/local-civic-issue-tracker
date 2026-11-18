package org.civictracker.records.domain;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.civictracker.records.domain.AcquisitionData.Format;

/** Exact text and locators belong to one immutable preparation. */
public final class PreparationData {
  private PreparationData() {}

  public enum Route {
    AUTO,
    NATIVE,
    OCR,
    EMPTY,
    NON_TEXT
  }

  public record Point(double x, double y) {}

  public record Region(double x, double y, double width, double height) {
    public Region {
      if (!Double.isFinite(x + y + width + height)
          || x < 0
          || y < 0
          || width <= 0
          || height <= 0
          || x + width > 1
          || y + height > 1) {
        throw new IllegalArgumentException("Regions require normalized page coordinates");
      }
    }
  }

  public record Routing(
      UUID versionId, int page, Route route, List<Region> regions, String reason) {
    public Routing {
      if (versionId == null || page < 1 || route == null || reason == null || reason.isBlank()) {
        throw new IllegalArgumentException("Routing selections require source identity and reason");
      }
      regions = regions == null ? List.of() : List.copyOf(regions);
      if (route != Route.OCR && !regions.isEmpty()) {
        throw new IllegalArgumentException("Only OCR selections accept regions");
      }
    }
  }

  public record Profile(
      String revision,
      int maxPages,
      long maxInputBytes,
      long maxPixels,
      int maxCharactersPerUnit,
      int maxRegions,
      double renderDpi,
      String ocrFingerprint,
      List<Routing> routing) {
    public Profile {
      maxPages = maxPages == 0 ? 5000 : maxPages;
      maxInputBytes = maxInputBytes == 0 ? 268435456 : maxInputBytes;
      maxPixels = maxPixels == 0 ? 40000000 : maxPixels;
      maxCharactersPerUnit = maxCharactersPerUnit == 0 ? 200000 : maxCharactersPerUnit;
      maxRegions = maxRegions == 0 ? 10000 : maxRegions;
      renderDpi = renderDpi == 0 ? 150 : renderDpi;
      routing = routing == null ? List.of() : List.copyOf(routing);
      if (revision == null
          || revision.isBlank()
          || ocrFingerprint == null
          || ocrFingerprint.isBlank()
          || maxPages < 1
          || maxInputBytes < 1
          || maxPixels < 1
          || maxCharactersPerUnit < 1
          || maxRegions < 1
          || !Double.isFinite(renderDpi)
          || renderDpi < 1
          || renderDpi > 600
          || routing.stream().map(r -> r.versionId() + ":" + r.page()).distinct().count()
              != routing.size()) {
        throw new IllegalArgumentException("Invalid preparation profile");
      }
    }
  }

  public record Specification(
      Profile profile, String pdfVersion, String htmlVersion, String pipeline) {}

  public record Input(
      UUID acquisitionItemId,
      UUID documentId,
      UUID versionId,
      UUID artifactId,
      String sha256,
      long byteLength,
      Format format) {}

  public record Selection(
      UUID acquisitionOperationId,
      UUID runId,
      String profileId,
      boolean freshRevision,
      Specification specification,
      String fingerprint,
      List<Input> inputs) {
    public Selection {
      inputs = List.copyOf(inputs);
    }
  }

  @JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "kind")
  @JsonSubTypes({
    @JsonSubTypes.Type(value = PdfLocator.class, name = "pdf"),
    @JsonSubTypes.Type(value = HtmlLocator.class, name = "html")
  })
  public sealed interface Locator permits PdfLocator, HtmlLocator {}

  public record Placement(String imageSha256, int width, int height, List<Double> imageToPage) {
    public Placement {
      imageToPage = List.copyOf(imageToPage);
    }
  }

  public record PdfLocator(
      int page, String pageLabel, double width, double height, List<Point> polygon, Placement image)
      implements Locator {
    public PdfLocator {
      polygon = polygon == null ? List.of() : List.copyOf(polygon);
    }
  }

  public record HtmlLocator(String itemId, String sectionId, String path) implements Locator {}

  public record Segment(
      UUID id, String text, String method, Locator locator, Double score, boolean selected) {}

  public record Unit(
      int ordinal,
      String disposition,
      String nativeText,
      List<Segment> segments,
      List<String> notes) {
    public Unit {
      segments = List.copyOf(segments);
      notes = List.copyOf(notes);
    }
  }

  public record OcrRegion(String text, double score, List<Point> polygon) {}

  public record OcrResult(
      UUID executionId,
      String imageSha256,
      String fingerprint,
      int width,
      int height,
      List<OcrRegion> regions,
      Map<String, Object> provenance) {}

  public static int codePoints(String text) {
    return text.codePointCount(0, text.length());
  }

  public static String passage(String text, int start, int end) {
    if (start < 0 || end < start || end > codePoints(text)) {
      throw new IllegalArgumentException("Invalid code-point offsets");
    }
    return text.substring(text.offsetByCodePoints(0, start), text.offsetByCodePoints(0, end));
  }
}
