package org.civictracker.records;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.Color;
import java.awt.Font;
import java.awt.font.FontRenderContext;
import java.awt.geom.PathIterator;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.apache.pdfbox.pdmodel.graphics.image.LosslessFactory;
import org.civictracker.records.application.OperationProblem;
import org.civictracker.records.domain.PreparationData;
import org.civictracker.records.domain.PreparationData.Profile;
import org.civictracker.records.domain.PreparationData.Route;
import org.civictracker.records.domain.PreparationData.Routing;
import org.civictracker.records.preparation.HtmlContentExtractor;
import org.civictracker.records.preparation.PdfContentExtractor;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class PreparationExtractionTests {
  @TempDir Path directory;

  static Profile profile(long pixels) {
    return new Profile(
        "fixture-1", 20, 10000000, pixels, 200000, 10000, 150, "fixture-v1", List.of());
  }

  static void text(PDPageContentStream stream, String text, float x, float y) throws Exception {
    stream.beginText();
    stream.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 12);
    stream.newLineAtOffset(x, y);
    stream.showText(text);
    stream.endText();
  }

  static BufferedImage raster() {
    var image = new BufferedImage(100, 100, BufferedImage.TYPE_INT_RGB);
    var graphics = image.createGraphics();
    graphics.setColor(Color.WHITE);
    graphics.fillRect(0, 0, 100, 100);
    graphics.setColor(Color.BLACK);
    graphics.drawString("Road", 10, 45);
    graphics.dispose();
    return image;
  }

  static void outlinedText(PDPageContentStream stream) throws Exception {
    var glyphs =
        new Font("SansSerif", Font.PLAIN, 24)
            .createGlyphVector(new FontRenderContext(null, true, true), "Road extension");
    var outline = glyphs.getOutline(60, 300).getPathIterator(null, .25);
    var point = new float[6];
    while (!outline.isDone()) {
      switch (outline.currentSegment(point)) {
        case PathIterator.SEG_MOVETO -> stream.moveTo(point[0], point[1]);
        case PathIterator.SEG_LINETO -> stream.lineTo(point[0], point[1]);
        case PathIterator.SEG_CLOSE -> stream.closePath();
        default -> throw new AssertionError("Flattened glyph has a curve");
      }
      outline.next();
    }
    stream.fill();
  }

  @Test
  void nativeHeadingDoesNotHideOutlinedBodyContent() throws Exception {
    Path file = directory.resolve("outlined-body.pdf");
    try (var document = new PDDocument()) {
      var page = new PDPage();
      document.addPage(page);
      try (var stream = new PDPageContentStream(document, page)) {
        text(stream, "Project map", 40, 700);
        outlinedText(stream);
      }
      document.save(file.toFile());
    }
    try (var pdf = new PdfContentExtractor(file, profile(10000000))) {
      assertEquals(
          "PREPARATION_ROUTING_REQUIRED",
          assertThrows(OperationProblem.class, () -> pdf.page(0, null)).code());
      var rendered =
          pdf.page(
              0,
              new Routing(
                  UUID.randomUUID(),
                  1,
                  Route.OCR,
                  List.of(),
                  "Review found body text drawn as outlines"));
      assertEquals("OCR", rendered.nativeUnit().disposition());
      assertEquals(1, rendered.images().size());
      assertFalse(rendered.nativeUnit().segments().getFirst().selected());
    }
  }

  @Test
  void anEmbeddedImageDoesNotHideEarlierVectorContent() throws Exception {
    Path file = directory.resolve("image-and-vectors.pdf");
    try (var document = new PDDocument()) {
      var page = new PDPage();
      document.addPage(page);
      try (var stream = new PDPageContentStream(document, page)) {
        outlinedText(stream);
        stream.drawImage(LosslessFactory.createFromImage(document, raster()), 40, 100, 100, 100);
      }
      document.save(file.toFile());
    }
    try (var pdf = new PdfContentExtractor(file, profile(10000000))) {
      var image = pdf.page(0, null).images().getFirst();
      assertTrue(image.locator().image().width() > 100);
      assertEquals(0, image.locator().image().imageToPage().get(4));
      assertEquals(0, image.locator().image().imageToPage().get(5));
    }
  }

  @Test
  void nestedHtmlItemsKeepTheirOwnIdentityAndSurroundingTextOrder() throws Exception {
    Path file = directory.resolve("nested-items.html");
    Files.writeString(
        file,
        "<div id='MeetingContents'><section data-sectionid='s1'>"
            + "<div class='meeting-item' data-itemid='parent'>Before child"
            + "<div class='agenda-item' data-itemid='child'><h3>Child recommendation</h3> "
            + "<a href='/attachment'>Child report</a></div>After child</div></section></div>");
    var units = HtmlContentExtractor.extract(file, profile(10000000));
    assertEquals(3, units.size());
    assertEquals(
        List.of("Before child", "Child recommendation Child report", "After child"),
        units.stream().map(u -> u.nativeText().replaceAll("\\s+", " ").strip()).toList());
    var locators =
        units.stream()
            .map(u -> (PreparationData.HtmlLocator) u.segments().getFirst().locator())
            .toList();
    assertEquals(
        List.of("parent", "child", "parent"),
        locators.stream().map(PreparationData.HtmlLocator::itemId).toList());
    assertTrue(locators.stream().allMatch(l -> "s1".equals(l.sectionId())));
  }

  @Test
  void sectionsWithoutItemsKeepTheirSeparateSourceIdentities() throws Exception {
    Path file = directory.resolve("sections-without-items.html");
    Files.writeString(
        file,
        "<div id='MeetingContents'><section data-sectionid='a'>Opening remarks</section>"
            + "<section data-sectionid='b'>Closing remarks</section></div>");
    var units = HtmlContentExtractor.extract(file, profile(10000000));
    assertEquals(
        List.of("Opening remarks", "Closing remarks"),
        units.stream().map(PreparationData.Unit::nativeText).toList());
    assertEquals(
        List.of("a", "b"),
        units.stream()
            .map(u -> ((PreparationData.HtmlLocator) u.segments().getFirst().locator()).sectionId())
            .toList());
  }

  @Test
  void htmlPreservesItemsUnicodeAndRemovesControlsWithoutRunningMarkup() throws Exception {
    Path file = directory.resolve("agenda.html");
    Files.writeString(
        file,
        "<div id='MeetingContents'><h1>Council agenda</h1>"
            + "<script>deleteEverything()</script><input value='request-token'>"
            + "<section data-sectionid='s1'><div class='meeting-item' data-itemid='i1'>"
            + "<h2>Road proposal 😀</h2><p>Recommendation only.</p></div></section></div>");
    var units = HtmlContentExtractor.extract(file, profile(10000000));
    assertEquals(2, units.size());
    String content = units.get(1).segments().getFirst().text();
    assertTrue(content.contains("Recommendation only."));
    assertFalse(units.toString().contains("request-token"));
    assertFalse(units.toString().contains("deleteEverything"));
    assertEquals("😀", PreparationData.passage("A😀B", 1, 2));
    assertEquals(3, PreparationData.codePoints("A😀B"));
    assertThrows(IllegalArgumentException.class, () -> PreparationData.passage("A😀B", 2, 4));
  }

  @Test
  void nativeAndBlankPagesDoNotNeedOcr() throws Exception {
    Path file = directory.resolve("native.pdf");
    try (var document = new PDDocument()) {
      var page = new PDPage();
      document.addPage(page);
      document.addPage(new PDPage());
      try (var stream = new PDPageContentStream(document, page)) {
        text(stream, "Council adopted the proposal.", 40, 700);
      }
      document.save(file.toFile());
    }
    try (var pdf = new PdfContentExtractor(file, profile(10000000))) {
      assertTrue(pdf.page(0, null).images().isEmpty());
      assertTrue(pdf.page(0, null).nativeUnit().segments().getFirst().selected());
      assertEquals("EMPTY", pdf.page(1, null).nativeUnit().disposition());
    }
  }

  @Test
  void htmlKeepsSectionHeadingsBetweenTheirActualItems() throws Exception {
    Path file = directory.resolve("sections.html");
    Files.writeString(
        file,
        "<div id='MeetingContents'><section data-sectionid='a'><h2>First section</h2>"
            + "<div class='meeting-item' data-itemid='1'>First item</div></section>"
            + "<section data-sectionid='b'><h2>Second section</h2>"
            + "<div class='meeting-item' data-itemid='2'>Second item</div></section></div>");
    var units = HtmlContentExtractor.extract(file, profile(10000000));
    assertEquals(
        List.of("First section", "First item", "Second section", "Second item"),
        units.stream().map(u -> u.segments().getFirst().text()).toList());
  }

  @Test
  void footerDoesNotQualifyTheScannedBodyAsNativeContent() throws Exception {
    assertFalse(PdfContentExtractor.usableText("\u00a0\nPacket\u00a0Page.\u00a0114\n"));
    Path file = directory.resolve("scan.pdf");
    try (var document = new PDDocument()) {
      var page = new PDPage();
      document.addPage(page);
      try (var stream = new PDPageContentStream(document, page)) {
        stream.drawImage(LosslessFactory.createFromImage(document, raster()), 40, 100, 400, 500);
        text(stream, "Packet Pg. 114", 40, 20);
      }
      document.save(file.toFile());
    }
    try (var pdf = new PdfContentExtractor(file, profile(10000000))) {
      var result = pdf.page(0, null);
      assertEquals(1, result.images().size());
      assertEquals("OCR", result.nativeUnit().disposition());
      assertEquals(100, result.images().getFirst().locator().image().width());
    }
  }

  @Test
  void overlappingNativeAndImageContentRequiresARecordedSelection() throws Exception {
    Path file = directory.resolve("overlap.pdf");
    try (var document = new PDDocument()) {
      var page = new PDPage();
      document.addPage(page);
      try (var stream = new PDPageContentStream(document, page)) {
        stream.drawImage(LosslessFactory.createFromImage(document, raster()), 40, 100, 400, 500);
        text(stream, "Visible map title", 60, 200);
      }
      document.save(file.toFile());
    }
    try (var pdf = new PdfContentExtractor(file, profile(10000000))) {
      assertEquals(
          "PREPARATION_ROUTING_REQUIRED",
          assertThrows(OperationProblem.class, () -> pdf.page(0, null)).code());
      var reviewed =
          pdf.page(
              0,
              new Routing(
                  UUID.randomUUID(),
                  1,
                  Route.NATIVE,
                  List.of(),
                  "Fixture reviewed as native text with decorative background"));
      assertEquals("NATIVE", reviewed.nativeUnit().disposition());
      assertEquals(0, reviewed.images().size());
      assertTrue(reviewed.nativeUnit().segments().getFirst().selected());
      assertEquals(1, reviewed.nativeUnit().notes().size());
    }
  }

  @Test
  void mixedPagePreservesNativeTextAndExactEmbeddedImagePlacement() throws Exception {
    Path file = directory.resolve("mixed.pdf");
    try (var document = new PDDocument()) {
      var page = new PDPage(new PDRectangle(20, 30, 300, 400));
      page.setUserUnit(2);
      document.addPage(page);
      try (var stream = new PDPageContentStream(document, page)) {
        stream.drawImage(LosslessFactory.createFromImage(document, raster()), 40, 100, 100, 100);
        text(stream, "Park map", 40, 390);
      }
      document.save(file.toFile());
    }
    try (var pdf = new PdfContentExtractor(file, profile(10000000))) {
      var work = pdf.page(0, null);
      assertTrue(work.nativeUnit().segments().getFirst().selected());
      var location = work.images().getFirst().locator();
      assertEquals(600, location.width());
      assertEquals(800, location.height());
      assertEquals(List.of(2.0, 0.0, 0.0, 2.0, 40.0, 460.0), location.image().imageToPage());
    }
  }

  @Test
  void rotatedPagesUseDisplayCoordinatesAndRepeatedImagesKeepSeparatePlacements() throws Exception {
    Path file = directory.resolve("rotated.pdf");
    try (var document = new PDDocument()) {
      var page = new PDPage(new PDRectangle(300, 400));
      page.setRotation(90);
      document.addPage(page);
      var image = LosslessFactory.createFromImage(document, raster());
      try (var stream = new PDPageContentStream(document, page)) {
        stream.drawImage(image, 30, 60, 100, 100);
        stream.drawImage(image, 160, 200, 100, 100);
      }
      document.save(file.toFile());
    }
    try (var pdf = new PdfContentExtractor(file, profile(10000000))) {
      var work =
          pdf.page(
              0,
              new Routing(
                  UUID.randomUUID(),
                  1,
                  Route.OCR,
                  List.of(
                      new PreparationData.Region(0, 0, .5, 1),
                      new PreparationData.Region(.5, 0, .5, 1)),
                  "Fixture regions"));
      assertEquals(2, work.images().size());
      assertEquals(400, work.images().getFirst().locator().width());
      assertNotEquals(
          work.images().get(0).locator().image().imageToPage(),
          work.images().get(1).locator().image().imageToPage());
    }
  }

  @Test
  void limitsAndExplicitDispositionsDoNotSilentlyTruncate() throws Exception {
    Path file = directory.resolve("blank.pdf");
    try (var document = new PDDocument()) {
      document.addPage(new PDPage());
      document.save(file.toFile());
    }
    try (var pdf = new PdfContentExtractor(file, profile(5))) {
      assertEquals(
          "PREPARATION_PIXEL_LIMIT",
          assertThrows(OperationProblem.class, () -> pdf.page(0, null)).code());
      assertEquals(
          "NON_TEXT",
          pdf.page(
                  0,
                  new Routing(
                      UUID.randomUUID(), 1, Route.NON_TEXT, List.of(), "Reviewed non-text example"))
              .nativeUnit()
              .disposition());
    }
  }
}
