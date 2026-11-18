package org.civictracker.records.portal.primegov;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import org.civictracker.records.application.OperationProblem;
import org.civictracker.records.domain.AcquisitionData.Format;
import org.civictracker.records.domain.AcquisitionData.Item;
import org.civictracker.records.domain.AcquisitionData.Meeting;
import org.civictracker.records.domain.AcquisitionData.Packet;
import org.civictracker.records.domain.AcquisitionData.Selection;
import org.civictracker.records.domain.AcquisitionData.Target;
import org.jsoup.Jsoup;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/** Maps official source fields without guessing civic meaning or missing identities. */
@Component
public class PrimeGovParser {
  /** Agenda items as acquisition records them; preparation must use the same rule. */
  public static final String ITEM_SELECTOR = ".meeting-item[data-itemid]";

  private final ObjectMapper json;

  public PrimeGovParser(ObjectMapper json) {
    this.json = json;
  }

  public List<Meeting> listing(InputStream body, Selection selection) throws IOException {
    JsonNode root = json.readTree(body);
    if (!root.isArray()) {
      throw new OperationProblem(503, "INVALID_SOURCE_LISTING");
    }
    List<Meeting> result = new ArrayList<>();
    for (JsonNode row : root) {
      String committee = required(row, "committeeId");
      if (!selection.committeeIds().contains(committee)) {
        continue;
      }
      String date = required(row, "dateTime");
      LocalDate local;
      try {
        local = LocalDate.from(DateTimeFormatter.ISO_DATE_TIME.parse(date));
      } catch (RuntimeException invalid) {
        throw new OperationProblem(503, "UNRESOLVED_SOURCE_DATE");
      }
      if (local.isBefore(selection.from()) || local.isAfter(selection.to())) {
        continue;
      }
      String meeting = required(row, "id");
      List<Target> documents = new ArrayList<>();
      String cancellation = null;
      JsonNode list = row.get("documentList");
      if (list == null || !list.isArray()) {
        throw new OperationProblem(503, "INVALID_SOURCE_LISTING");
      }
      for (JsonNode document : list) {
        String language = value(document, "language");
        if (language != null && !language.toLowerCase(Locale.ROOT).startsWith("en")) {
          continue;
        }
        String title = value(document, "templateName");
        String id = value(document, "id");
        String template = value(document, "templateId");
        String output = value(document, "compileOutputType");
        String link = value(document, "link");
        URI url;
        Format format =
            "3".equals(output) ? Format.HTML : "1".equals(output) ? Format.PDF : Format.UNSUPPORTED;
        if (link != null && !link.isBlank()) {
          url = selection.profile().baseUrl().resolve(link);
        } else {
          if (id == null) {
            throw new OperationProblem(503, "UNRESOLVED_SOURCE_DOCUMENT");
          }
          boolean positiveTemplate = template != null && template.matches("[1-9][0-9]*");
          String query =
              positiveTemplate
                  ? "meetingTemplateId=" + template
                  : "compiledMeetingDocumentFileId=" + id;
          String path =
              format == Format.HTML
                  ? "/Portal/Meeting?" + query
                  : "/Public/CompiledDocument?" + query + "&compileOutputType=" + output;
          url = selection.profile().baseUrl().resolve(path);
        }
        documents.add(
            new Target(
                "document-list",
                id,
                url,
                format,
                null,
                null,
                template,
                output,
                language,
                title,
                value(document, "publishDate")));
        if (title != null && title.equalsIgnoreCase("Notice of Cancellation")) {
          cancellation = title + ": " + url;
        }
      }
      documents.sort(
          Comparator.comparing((Target t) -> t.url().toString())
              .thenComparing(t -> t.officialId() == null ? "" : t.officialId()));
      String title = value(row, "title");
      if (cancellation == null
          && title != null
          && title.toLowerCase(Locale.ROOT).matches(".*[-–]\\s*cancelled\\s*$")) {
        cancellation = title;
      }
      result.add(
          new Meeting(
              meeting,
              committee,
              title,
              date,
              local,
              value(row, "publishDate"),
              value(row, "videoUrl"),
              cancellation,
              documents));
    }
    return result;
  }

  public Packet packet(InputStream file, URI source) throws IOException {
    var html = Jsoup.parse(file, "UTF-8", source.toString());
    if (html.selectFirst("#MeetingContents") == null) {
      throw new OperationProblem(503, "INVALID_SOURCE_PACKET");
    }
    var items = new LinkedHashMap<String, Item>();
    for (var node : html.select(ITEM_SELECTOR)) {
      String id = node.attr("data-itemid");
      if (id.isBlank()) {
        throw new OperationProblem(503, "INVALID_SOURCE_ITEM");
      }
      String label = node.hasAttr("data-itemlabel") ? node.attr("data-itemlabel") : null;
      items.putIfAbsent(id, new Item(id, label));
    }
    var attachments = new LinkedHashMap<String, Target>();
    for (var anchor : html.select("a[href]")) {
      URI url;
      try {
        url = source.resolve(anchor.attr("href"));
      } catch (IllegalArgumentException invalid) {
        continue;
      }
      String history = parameter(url, "historyId");
      if (history == null
          || !url.getPath()
              .toLowerCase(Locale.ROOT)
              .contains("/api/compilemeetingattachmenthistory/historyattachment")) {
        continue;
      }
      var parent = anchor.closest(ITEM_SELECTOR);
      if (parent == null || history.isBlank()) {
        throw new OperationProblem(503, "INVALID_ATTACHMENT_CONTEXT");
      }
      String item = parent.attr("data-itemid");
      String key = item + "\n" + history;
      Target target =
          new Target(
              "attachment-history",
              history,
              url,
              Format.PDF,
              item,
              items.get(item).label(),
              null,
              null,
              null,
              anchor.text(),
              null);
      Target prior = attachments.putIfAbsent(key, target);
      if (prior != null && !prior.url().equals(target.url())) {
        throw new OperationProblem(503, "CONFLICTING_ATTACHMENT_REFERENCE");
      }
    }
    for (var node : html.select(ITEM_SELECTOR)) {
      if (node.attr("data-hasattachments").equalsIgnoreCase("true")
          && node.select("a[href*=historyId]").isEmpty()) {
        throw new OperationProblem(503, "INCOMPLETE_SOURCE_PACKET");
      }
    }
    return new Packet(List.copyOf(items.values()), List.copyOf(attachments.values()));
  }

  private static String parameter(URI uri, String name) {
    if (uri.getRawQuery() == null) {
      return null;
    }
    for (String part : uri.getRawQuery().split("&")) {
      String[] pair = part.split("=", 2);
      if (URLDecoder.decode(pair[0], StandardCharsets.UTF_8).equals(name) && pair.length == 2) {
        return URLDecoder.decode(pair[1], StandardCharsets.UTF_8);
      }
    }
    return null;
  }

  private static String required(JsonNode node, String key) {
    String value = value(node, key);
    if (value == null || value.isBlank()) {
      throw new OperationProblem(503, "INVALID_SOURCE_LISTING");
    }
    return value;
  }

  private static String value(JsonNode node, String key) {
    JsonNode value = node.get(key);
    if (value == null || value.isNull()) {
      return null;
    }
    if (!(value.isString() || value.isNumber())) {
      throw new OperationProblem(503, "INVALID_SOURCE_LISTING");
    }
    return value.isString() ? value.asString() : value.toString();
  }
}
