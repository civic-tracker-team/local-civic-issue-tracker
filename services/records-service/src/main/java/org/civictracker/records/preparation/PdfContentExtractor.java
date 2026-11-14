package org.civictracker.records.preparation;

import java.awt.BasicStroke;
import java.awt.geom.Area;
import java.awt.geom.GeneralPath;
import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import javax.imageio.ImageIO;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.contentstream.PDFGraphicsStreamEngine;
import org.apache.pdfbox.cos.COSName;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.encryption.InvalidPasswordException;
import org.apache.pdfbox.pdmodel.graphics.image.PDImage;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.pdfbox.text.TextPosition;
import org.civictracker.records.application.OperationProblem;
import org.civictracker.records.domain.Hashes;
import org.civictracker.records.domain.PreparationData.PdfLocator;
import org.civictracker.records.domain.PreparationData.Placement;
import org.civictracker.records.domain.PreparationData.Point;
import org.civictracker.records.domain.PreparationData.Profile;
import org.civictracker.records.domain.PreparationData.Region;
import org.civictracker.records.domain.PreparationData.Route;
import org.civictracker.records.domain.PreparationData.Routing;
import org.civictracker.records.domain.PreparationData.Segment;
import org.civictracker.records.domain.PreparationData.Unit;

/** Keeps page rendering and source-coordinate conversion in one adapter. */
public final class PdfContentExtractor implements AutoCloseable {
  private final PDDocument document;
  private final Profile profile;

  public record ImageInput(byte[] bytes, PdfLocator locator) {}

  public record Work(Unit nativeUnit, List<ImageInput> images) {}

  public PdfContentExtractor(Path input, Profile profile) throws IOException {
    this.profile = profile;
    try {
      // An owner-password-only PDF opens with the empty user password; any other password fails.
      document = Loader.loadPDF(input.toFile());
    } catch (InvalidPasswordException locked) {
      throw new OperationProblem(422, "PDF_PREPARATION_UNSUPPORTED");
    }
    if (!document.getCurrentAccessPermission().canExtractContent()
        || document.getNumberOfPages() < 1
        || document.getNumberOfPages() > profile.maxPages()) {
      document.close();
      throw new OperationProblem(422, "PDF_PREPARATION_UNSUPPORTED");
    }
  }

  public int size() {
    return document.getNumberOfPages();
  }

  public Work page(int ordinal, Routing selection) throws IOException {
    PDPage page = document.getPage(ordinal);
    Frame frame = new Frame(page);
    var stripper = new Locations(profile.maxCharactersPerUnit());
    stripper.setSortByPosition(true);
    stripper.setStartPage(ordinal + 1);
    stripper.setEndPage(ordinal + 1);
    String nativeText = stripper.getText(document).replace("\r\n", "\n").strip();
    if (nativeText.codePointCount(0, nativeText.length()) > profile.maxCharactersPerUnit()) {
      throw new OperationProblem(422, "PREPARATION_TEXT_LIMIT");
    }
    Route route = selection == null ? Route.AUTO : selection.route();
    List<String> notes = selection == null ? List.of() : List.of(selection.reason());
    PdfLocator locator = locator(ordinal, frame, List.of(), null);
    boolean body = usableText(nativeText);
    var segments =
        nativeText.isEmpty()
            ? List.<Segment>of()
            : List.of(
                new Segment(
                    UUID.randomUUID(),
                    nativeText,
                    "PDF_NATIVE",
                    locator,
                    null,
                    route == Route.NATIVE
                        || body && (route != Route.OCR || !selection.regions().isEmpty())));
    if (route == Route.EMPTY || route == Route.NON_TEXT) {
      return new Work(
          new Unit(
              ordinal,
              route.name(),
              nativeText,
              segments.stream()
                  .map(
                      s -> new Segment(s.id(), s.text(), s.method(), s.locator(), s.score(), false))
                  .toList(),
              notes),
          List.of());
    }
    if (route == Route.NATIVE) {
      if (nativeText.matches("(?U)\\s*") || nativeText.contains("\ufffd")) {
        throw new OperationProblem(422, "NATIVE_CONTENT_UNUSABLE");
      }
      return new Work(new Unit(ordinal, "NATIVE", nativeText, segments, notes), List.of());
    }
    var graphics = new Graphics(page, frame, profile);
    graphics.processPage(page);
    if (route == Route.AUTO && body && graphics.vectorContent) {
      throw new OperationProblem(422, "PREPARATION_ROUTING_REQUIRED");
    }
    if (route == Route.AUTO && body && graphics.images.isEmpty()) {
      return new Work(new Unit(ordinal, "NATIVE", nativeText, segments, notes), List.of());
    }
    var inputs = new ArrayList<ImageInput>();
    if (route == Route.AUTO && body) {
      BufferedImage rendered = render(ordinal, frame);
      for (Picture image : graphics.images) {
        if (strippingOverlaps(image.bounds(), stripper.positions, frame)) {
          throw new OperationProblem(422, "PREPARATION_ROUTING_REQUIRED");
        }
        if (graphics.images.size() == 1 && image.direct()) {
          checkPixels(image.image().getWidth(), image.image().getHeight());
          inputs.add(encode(image.image().getImage(), ordinal, frame, image.transform()));
        } else {
          inputs.add(crop(rendered, image.bounds(), ordinal, frame));
        }
      }
    } else if (route == Route.OCR && !selection.regions().isEmpty()) {
      BufferedImage rendered = render(ordinal, frame);
      for (Region region : selection.regions()) {
        inputs.add(
            crop(
                rendered,
                new Rectangle2D.Double(
                    region.x() * frame.width,
                    region.y() * frame.height,
                    region.width() * frame.width,
                    region.height() * frame.height),
                ordinal,
                frame));
      }
    } else if (route == Route.AUTO
        && !graphics.vectorContent
        && graphics.images.size() == 1
        && graphics.images.getFirst().direct()) {
      Picture image = graphics.images.getFirst();
      checkPixels(image.image().getWidth(), image.image().getHeight());
      inputs.add(encode(image.image().getImage(), ordinal, frame, image.transform()));
    } else {
      BufferedImage rendered = render(ordinal, frame);
      if (route == Route.AUTO && nativeText.matches("(?U)\\s*") && white(rendered)) {
        return new Work(
            new Unit(ordinal, "EMPTY", nativeText, List.of(), List.of("RENDERED_PAGE_IS_WHITE")),
            List.of());
      }
      inputs.add(
          encode(
              rendered,
              ordinal,
              frame,
              List.of(
                  frame.width / rendered.getWidth(),
                  0.0,
                  0.0,
                  frame.height / rendered.getHeight(),
                  0.0,
                  0.0)));
      segments =
          segments.stream()
              .map(s -> new Segment(s.id(), s.text(), s.method(), s.locator(), s.score(), false))
              .toList();
    }
    if (inputs.isEmpty()) {
      throw new OperationProblem(422, "PREPARATION_ROUTING_REQUIRED");
    }
    return new Work(new Unit(ordinal, "OCR", nativeText, segments, notes), List.copyOf(inputs));
  }

  private BufferedImage render(int ordinal, Frame frame) throws IOException {
    double width = Math.ceil(frame.width / frame.unit * profile.renderDpi() / 72);
    double height = Math.ceil(frame.height / frame.unit * profile.renderDpi() / 72);
    checkPixels(width, height);
    BufferedImage image =
        new PDFRenderer(document).renderImageWithDPI(ordinal, (float) profile.renderDpi());
    checkPixels(image.getWidth(), image.getHeight());
    return image;
  }

  private void checkPixels(double width, double height) {
    if (!Double.isFinite(width * height)
        || width < 1
        || height < 1
        || width * height > profile.maxPixels()) {
      throw new OperationProblem(422, "PREPARATION_PIXEL_LIMIT");
    }
  }

  private ImageInput crop(BufferedImage image, Rectangle2D region, int ordinal, Frame frame)
      throws IOException {
    int x = Math.max(0, (int) Math.floor(region.getMinX() / frame.width * image.getWidth()));
    int y = Math.max(0, (int) Math.floor(region.getMinY() / frame.height * image.getHeight()));
    int right =
        Math.min(
            image.getWidth(), (int) Math.ceil(region.getMaxX() / frame.width * image.getWidth()));
    int bottom =
        Math.min(
            image.getHeight(),
            (int) Math.ceil(region.getMaxY() / frame.height * image.getHeight()));
    if (right <= x || bottom <= y) {
      throw new OperationProblem(422, "INVALID_IMAGE_PLACEMENT");
    }
    double sx = frame.width / image.getWidth();
    double sy = frame.height / image.getHeight();
    return encode(
        image.getSubimage(x, y, right - x, bottom - y),
        ordinal,
        frame,
        List.of(sx, 0.0, 0.0, sy, x * sx, y * sy));
  }

  private ImageInput encode(BufferedImage image, int ordinal, Frame frame, List<Double> transform)
      throws IOException {
    checkPixels(image.getWidth(), image.getHeight());
    var output = new ByteArrayOutputStream();
    if (!ImageIO.write(image, "png", output)) {
      throw new IOException("PNG encoding unavailable");
    }
    byte[] bytes = output.toByteArray();
    Placement placement =
        new Placement(Hashes.sha256(bytes), image.getWidth(), image.getHeight(), transform);
    return new ImageInput(bytes, locator(ordinal, frame, List.of(), placement));
  }

  private PdfLocator locator(int ordinal, Frame frame, List<Point> polygon, Placement placement)
      throws IOException {
    String[] labels =
        document.getDocumentCatalog().getPageLabels() == null
            ? null
            : document.getDocumentCatalog().getPageLabels().getLabelsByPageIndices();
    return new PdfLocator(
        ordinal + 1,
        labels == null ? null : labels[ordinal],
        frame.width,
        frame.height,
        polygon,
        placement);
  }

  private static boolean strippingOverlaps(
      Rectangle2D image, List<TextPosition> positions, Frame frame) {
    return positions.stream()
        .anyMatch(p -> image.contains(p.getX() * frame.unit, p.getY() * frame.unit));
  }

  private static boolean white(BufferedImage image) {
    for (int y = 0; y < image.getHeight(); y++) {
      for (int x = 0; x < image.getWidth(); x++) {
        if ((image.getRGB(x, y) & 0xffffff) != 0xffffff) {
          return false;
        }
      }
    }
    return true;
  }

  public static boolean usableText(String value) {
    return !value
            .replaceAll("(?Uim)^\\s*(?:packet\\s*(?:page|pg\\.?)\\s*[:.]?\\s*)?\\d+\\s*$", "")
            .replaceAll("(?U)\\s+", "")
            .isEmpty()
        && !value.contains("\ufffd");
  }

  @Override
  public void close() throws IOException {
    document.close();
  }

  private static final class Locations extends PDFTextStripper {
    final List<TextPosition> positions = new ArrayList<>();
    private final int limit;
    private long characters;

    Locations(int limit) throws IOException {
      this.limit = limit;
    }

    @Override
    protected void processTextPosition(TextPosition position) {
      characters += position.getUnicode().codePointCount(0, position.getUnicode().length());
      if (characters > limit) {
        throw new OperationProblem(422, "PREPARATION_TEXT_LIMIT");
      }
      positions.add(position);
      super.processTextPosition(position);
    }
  }

  private static final class Frame {
    final double width;
    final double height;
    final double unit;
    final PDPage page;

    Frame(PDPage page) {
      this.page = page;
      if (Math.floorMod(page.getRotation(), 90) != 0) {
        throw new OperationProblem(422, "INVALID_PAGE_ROTATION");
      }
      unit = page.getUserUnit();
      var crop = page.getCropBox();
      boolean rotated = Math.floorMod(page.getRotation(), 180) != 0;
      width = (rotated ? crop.getHeight() : crop.getWidth()) * unit;
      height = (rotated ? crop.getWidth() : crop.getHeight()) * unit;
      if (!Double.isFinite(width * height) || width <= 0 || height <= 0) {
        throw new OperationProblem(422, "INVALID_PAGE_GEOMETRY");
      }
    }

    Point display(double x, double y) {
      var crop = page.getCropBox();
      double u = x - crop.getLowerLeftX();
      double v = y - crop.getLowerLeftY();
      return switch (Math.floorMod(page.getRotation(), 360)) {
        case 0 -> new Point(u * unit, (crop.getHeight() - v) * unit);
        case 90 -> new Point(v * unit, u * unit);
        case 180 -> new Point((crop.getWidth() - u) * unit, v * unit);
        case 270 -> new Point((crop.getHeight() - v) * unit, (crop.getWidth() - u) * unit);
        default -> throw new OperationProblem(422, "INVALID_PAGE_ROTATION");
      };
    }
  }

  private record Picture(
      PDImage image, Rectangle2D bounds, List<Double> transform, boolean direct) {}

  private static final class Graphics extends PDFGraphicsStreamEngine {
    final List<Picture> images = new ArrayList<>();
    final GeneralPath path = new GeneralPath();
    final Frame frame;
    final Profile profile;
    boolean vectorContent;

    Graphics(PDPage page, Frame frame, Profile profile) {
      super(page);
      this.frame = frame;
      this.profile = profile;
    }

    @Override
    public void drawImage(PDImage image) throws IOException {
      if (images.size() >= profile.maxRegions()
          || image.getWidth() < 1
          || image.getHeight() < 1
          || (long) image.getWidth() * image.getHeight() > profile.maxPixels()) {
        throw new OperationProblem(422, "PREPARATION_PIXEL_LIMIT");
      }
      var matrix = getGraphicsState().getCurrentTransformationMatrix();
      var a = matrix.transformPoint(0, 1);
      var b = matrix.transformPoint(1, 1);
      var c = matrix.transformPoint(0, 0);
      Point origin = frame.display(a.getX(), a.getY());
      Point across = frame.display(b.getX(), b.getY());
      Point down = frame.display(c.getX(), c.getY());
      double ax = (across.x() - origin.x()) / image.getWidth();
      double ay = (across.y() - origin.y()) / image.getWidth();
      double bx = (down.x() - origin.x()) / image.getHeight();
      double by = (down.y() - origin.y()) / image.getHeight();
      double fourthX = across.x() + down.x() - origin.x();
      double fourthY = across.y() + down.y() - origin.y();
      double minX = Math.min(Math.min(origin.x(), across.x()), Math.min(down.x(), fourthX));
      double minY = Math.min(Math.min(origin.y(), across.y()), Math.min(down.y(), fourthY));
      double maxX = Math.max(Math.max(origin.x(), across.x()), Math.max(down.x(), fourthX));
      double maxY = Math.max(Math.max(origin.y(), across.y()), Math.max(down.y(), fourthY));
      var bounds = new Rectangle2D.Double(minX, minY, maxX - minX, maxY - minY);
      var raw =
          matrix.createAffineTransform().createTransformedShape(new Rectangle2D.Double(0, 0, 1, 1));
      var visible = new Area(raw);
      visible.intersect(getGraphicsState().getCurrentClippingPath());
      if (visible.isEmpty()) {
        return;
      }
      boolean direct =
          image instanceof PDImageXObject xobject
              && xobject.getCOSObject().getDictionaryObject(COSName.SMASK) == null
              && xobject.getCOSObject().getDictionaryObject(COSName.MASK) == null
              && ax > 0
              && by > 0
              && Math.abs(ay) < 0.00001
              && Math.abs(bx) < 0.00001
              && getGraphicsState().getCurrentClippingPath().contains(raw.getBounds2D());
      images.add(
          new Picture(image, bounds, List.of(ax, ay, bx, by, origin.x(), origin.y()), direct));
    }

    @Override
    public void appendRectangle(Point2D a, Point2D b, Point2D c, Point2D d) {
      path.moveTo(a.getX(), a.getY());
      path.lineTo(b.getX(), b.getY());
      path.lineTo(c.getX(), c.getY());
      path.lineTo(d.getX(), d.getY());
      path.closePath();
    }

    @Override
    public void clip(int rule) {
      path.setWindingRule(rule);
      getGraphicsState().intersectClippingPath(path);
    }

    @Override
    public void moveTo(float x, float y) {
      path.moveTo(x, y);
    }

    @Override
    public void lineTo(float x, float y) {
      path.lineTo(x, y);
    }

    @Override
    public void curveTo(float a, float b, float c, float d, float e, float f) {
      path.curveTo(a, b, c, d, e, f);
    }

    @Override
    public Point2D getCurrentPoint() {
      return path.getCurrentPoint();
    }

    @Override
    public void closePath() {
      path.closePath();
    }

    @Override
    public void endPath() {
      path.reset();
    }

    @Override
    public void strokePath() {
      paint(false, true);
    }

    @Override
    public void fillPath(int rule) {
      path.setWindingRule(rule);
      paint(true, false);
    }

    @Override
    public void fillAndStrokePath(int rule) {
      path.setWindingRule(rule);
      paint(true, true);
    }

    @Override
    public void shadingFill(COSName name) {
      vectorContent |= !getGraphicsState().getCurrentClippingPath().isEmpty();
      for (int n = 0; n < images.size(); n++) {
        Picture image = images.get(n);
        images.set(n, new Picture(image.image(), image.bounds(), image.transform(), false));
      }
    }

    private void paint(boolean fill, boolean stroke) {
      var visible = fill ? new Area(path) : new Area();
      if (stroke) {
        var outline = new BasicStroke(Math.max(1, getGraphicsState().getLineWidth()));
        visible.add(new Area(outline.createStrokedShape(path)));
      }
      visible.intersect(getGraphicsState().getCurrentClippingPath());
      vectorContent |= !visible.isEmpty();
      overlay();
      path.reset();
    }

    private void overlay() {
      Rectangle2D bounds = path.getBounds2D();
      Point a = frame.display(bounds.getMinX(), bounds.getMinY());
      Point b = frame.display(bounds.getMaxX(), bounds.getMaxY());
      var display = new Rectangle2D.Double();
      display.setFrameFromDiagonal(a.x(), a.y(), b.x(), b.y());
      for (int n = 0; n < images.size(); n++) {
        Picture image = images.get(n);
        if (display.intersects(image.bounds()) || image.bounds().contains(a.x(), a.y())) {
          images.set(n, new Picture(image.image(), image.bounds(), image.transform(), false));
        }
      }
    }
  }
}
