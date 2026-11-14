package org.civictracker.records.preparation;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.civictracker.records.application.OperationProblem;
import org.civictracker.records.domain.PreparationData.HtmlLocator;
import org.civictracker.records.domain.PreparationData.Profile;
import org.civictracker.records.domain.PreparationData.Segment;
import org.civictracker.records.domain.PreparationData.Unit;
import org.civictracker.records.portal.primegov.PrimeGovParser;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Element;
import org.jsoup.nodes.Node;
import org.jsoup.nodes.TextNode;
import org.jsoup.select.NodeTraversor;
import org.jsoup.select.NodeVisitor;

/** Reads retained agenda structure without executing imported markup. */
public final class HtmlContentExtractor {
  private static final String ITEMS = PrimeGovParser.ITEM_SELECTOR;
  private static final String BOUNDARIES = ITEMS + ",[data-sectionid]";

  /**
   * Elements whose edges separate text. Listed here rather than taken from the parser's tag table
   * so that the retained text does not change with the parser's own classification.
   */
  private static final Set<String> BLOCKS =
      Set.of(
          "address", "article", "aside", "blockquote", "caption", "center", "dd", "details",
          "dialog", "div", "dl", "dt", "fieldset", "figcaption", "figure", "footer", "form", "h1",
          "h2", "h3", "h4", "h5", "h6", "header", "hgroup", "hr", "legend", "li", "main", "menu",
          "nav", "ol", "p", "pre", "section", "summary", "table", "tbody", "tfoot", "thead", "ul");

  private static final Set<String> CELLS = Set.of("td", "th");

  private HtmlContentExtractor() {}

  public static List<Unit> extract(Path file, Profile profile) throws IOException {
    var document = Jsoup.parse(file.toFile(), "UTF-8");
    document
        .select(
            "script,style,noscript,input,button,select,textarea,nav,.social-sharing,.share-buttons")
        .remove();
    Element root = document.getElementById("MeetingContents");
    if (root == null) {
      throw new OperationProblem(422, "HTML_CONTENT_STRUCTURE_UNSUPPORTED");
    }
    var units = new ArrayList<Unit>();
    var pending = new ArrayDeque<Node>();
    pending.add(root);
    while (!pending.isEmpty()) {
      Node node = pending.removeFirst();
      if (node instanceof Element element) {
        if (element.select(BOUNDARIES).stream().noneMatch(boundary -> boundary != element)) {
          add(units, text(element), locator(element, element.cssSelector()), profile);
        } else {
          var children = element.childNodes();
          for (int n = children.size() - 1; n >= 0; n--) {
            pending.addFirst(children.get(n));
          }
        }
      } else if (node instanceof TextNode text && !text.isBlank()) {
        Element parent = (Element) text.parent();
        add(
            units,
            text.getWholeText(),
            locator(parent, parent.cssSelector() + "/text()[" + text.siblingIndex() + "]"),
            profile);
      }
      if (units.size() > profile.maxPages()) {
        throw new OperationProblem(422, "PREPARATION_UNIT_LIMIT");
      }
    }
    if (units.isEmpty()) {
      throw new OperationProblem(422, "HTML_READABLE_CONTENT_MISSING");
    }
    if (units.size() > profile.maxPages()) {
      throw new OperationProblem(422, "PREPARATION_UNIT_LIMIT");
    }
    return List.copyOf(units);
  }

  /**
   * Reads an element's text with a separator at each block edge: a newline between blocks and
   * table rows, and a tab between cells of one row. Whitespace that the source places at a block
   * edge is folded into that separator, so adjacent blocks are never merged and already-spaced
   * sources do not gain blank lines. Inline elements add nothing, and a line break stays a newline.
   */
  static String text(Element element) {
    var reader = new Separated();
    NodeTraversor.traverse(reader, element);
    return reader.out.toString();
  }

  private static final class Separated implements NodeVisitor {
    private final StringBuilder out = new StringBuilder();
    // Edges seen since the last text. A row edge starts a new line and discards trailing empty
    // cells; each later cell edge in that row adds a tab, so empty cells keep their columns.
    private boolean row;
    private boolean block;
    private int cells;

    @Override
    public void head(Node node, int depth) {
      if (node instanceof TextNode text) {
        String value = text.getWholeText();
        if (row || block || cells > 0) {
          if (text.isBlank()) {
            return;
          }
          int start = 0;
          while (start < value.length() && whitespace(value.charAt(start))) {
            start++;
          }
          value = value.substring(start);
          if (!out.isEmpty()) {
            int end = out.length();
            while (end > 0 && whitespace(out.charAt(end - 1))) {
              end--;
            }
            out.setLength(end);
            if (cells > 0) {
              // A cell edge outranks a block edge inside either cell.
              if (row) {
                out.append('\n');
              }
              out.append("\t".repeat(cells));
            } else {
              out.append('\n');
            }
          }
          row = false;
          block = false;
          cells = 0;
        }
        out.append(value);
      } else if (node instanceof Element element) {
        String name = element.normalName();
        if (name.equals("br")) {
          out.append('\n');
        } else if (name.equals("tr")) {
          rowEdge();
        } else if (CELLS.contains(name)) {
          if (element.previousElementSibling() == null) {
            rowEdge();
          } else {
            cells++;
          }
        } else if (BLOCKS.contains(name)) {
          block = true;
        }
      }
    }

    @Override
    public void tail(Node node, int depth) {
      if (node instanceof Element element) {
        String name = element.normalName();
        if (name.equals("tr")) {
          rowEdge();
        } else if (BLOCKS.contains(name)) {
          block = true;
        }
      }
    }

    private void rowEdge() {
      row = true;
      cells = 0;
    }

    private static boolean whitespace(char c) {
      return c == ' ' || c == '\t' || c == '\n' || c == '\f' || c == '\r';
    }
  }

  private static HtmlLocator locator(Element element, String path) {
    Element item = element.closest(ITEMS);
    Element section = element.closest("[data-sectionid]");
    return new HtmlLocator(
        item == null ? null : item.attr("data-itemid"),
        section == null ? null : section.attr("data-sectionid"),
        path);
  }

  private static void add(List<Unit> units, String raw, HtmlLocator locator, Profile profile) {
    String text = raw.replace("\r\n", "\n").strip();
    if (text.isEmpty()) {
      return;
    }
    if (text.contains("\ufffd")) {
      throw new OperationProblem(422, "HTML_TEXT_UNUSABLE");
    }
    if (text.codePointCount(0, text.length()) > profile.maxCharactersPerUnit()) {
      throw new OperationProblem(422, "PREPARATION_TEXT_LIMIT");
    }
    units.add(
        new Unit(
            units.size(),
            "NATIVE",
            text,
            List.of(new Segment(UUID.randomUUID(), text, "HTML_NATIVE", locator, null, true)),
            List.of()));
  }
}
