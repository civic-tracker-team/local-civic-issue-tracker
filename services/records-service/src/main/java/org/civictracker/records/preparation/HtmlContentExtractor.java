package org.civictracker.records.preparation;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.civictracker.records.application.OperationProblem;
import org.civictracker.records.domain.PreparationData.HtmlLocator;
import org.civictracker.records.domain.PreparationData.Profile;
import org.civictracker.records.domain.PreparationData.Segment;
import org.civictracker.records.domain.PreparationData.Unit;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Element;
import org.jsoup.nodes.Node;
import org.jsoup.nodes.TextNode;

/** Reads retained agenda structure without executing imported markup. */
public final class HtmlContentExtractor {
  private static final String ITEMS = ".meeting-item[data-itemid],.agenda-item[data-itemid]";
  private static final String BOUNDARIES = ITEMS + ",[data-sectionid]";

  private HtmlContentExtractor() {}

  public static List<Unit> extract(Path file, Profile profile) throws IOException {
    var document = Jsoup.parse(file.toFile(), "UTF-8");
    document
        .select("script,style,noscript,input,button,nav,.social-sharing,.share-buttons")
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
          add(units, element.wholeText(), locator(element, element.cssSelector()), profile);
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
