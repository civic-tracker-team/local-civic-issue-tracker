package org.civictracker.analysis.evaluation;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.regex.Pattern;
import tools.jackson.databind.JsonNode;

/** Reports saved trial diagnostics without changing acceptance or computing quality metrics. */
public final class TrialSummary {
  private TrialSummary() {}

  private static final Pattern TOKEN = Pattern.compile("[\\p{L}\\p{N}]+");

  private record KeyOrder(List<String> keys, Boolean matchesSchema) {}

  private record Citation(
      int developmentIndex,
      String at,
      String unit,
      Integer firstLine,
      Integer lastLine,
      Double lexicalOverlap) {}

  private record Selection(int developmentIndex, String at, JsonNode range) {}

  private record LinkCoverage(int developmentIndex, String at, String issue, Double nameCoverage) {}

  private record Trial(
      String trial,
      String contractRevision,
      JsonNode sampling,
      String state,
      Integer promptTokens,
      Integer outputTokens,
      Integer developments,
      Integer issues,
      Integer associations,
      Map<String, Integer> kinds,
      Map<String, Integer> dateRoles,
      Integer developmentsWithoutLinks,
      KeyOrder rawKeyOrder,
      List<Citation> citations,
      List<LinkCoverage> links) {}

  public static void main(String[] args) throws IOException {
    if (args.length < 2)
      throw new IllegalArgumentException("Usage: summarize SUMMARY_OUT TRIAL_DIR...");
    Path output = Path.of(args[0]);
    var trials = new ArrayList<Trial>();
    for (int i = 1; i < args.length; i++) trials.add(readTrial(Path.of(args[i])));
    Files.createDirectory(output);
    Files.writeString(
        output.resolve("summary.json"),
        EvaluationJson.MAPPER
                .writerWithDefaultPrettyPrinter()
                .writeValueAsString(Map.of("trials", trials))
            + "\n",
        StandardOpenOption.CREATE_NEW);
    String markdown = markdown(trials);
    Files.writeString(output.resolve("summary.md"), markdown, StandardOpenOption.CREATE_NEW);
    System.out.print(markdown);
  }

  private static Trial readTrial(Path folder) throws IOException {
    var receipt = read(folder.resolve("receipt.json"));
    var attempt = read(folder.resolve("attempt.json"));
    var candidates = optional(folder.resolve("candidates.json"));
    var resolutions = optional(folder.resolve("resolution.json"));
    var schema = optional(folder.resolve("schema.json"));
    JsonNode raw;
    try {
      raw = EvaluationJson.MAPPER.readTree(attempt.path("content").asText());
    } catch (RuntimeException invalid) {
      raw = EvaluationJson.MAPPER.createObjectNode();
    }
    if (raw == null) raw = EvaluationJson.MAPPER.createObjectNode();
    String revision = receipt.path("contractRevision").asText("NOT_RECORDED");
    var kinds = new TreeMap<String, Integer>();
    var dates = new TreeMap<String, Integer>();
    var developments = candidates.path("developments");
    for (var development : developments)
      for (var date : development.path("dates"))
        dates.merge(date.path("role").asText("NOT_RECORDED"), 1, Integer::sum);
    if (revision.startsWith("grounded-findings-"))
      for (var development : raw.path("developments"))
        kinds.merge(
            development.path("finding").path("kind").asText("NOT_RECORDED"), 1, Integer::sum);
    var linked = new HashSet<String>();
    for (var association : candidates.path("associations"))
      linked.add(association.path("developmentId").asText());
    int withoutLinks = 0;
    for (var development : developments)
      if (!linked.contains(development.path("id").asText())) withoutLinks++;
    var selections = selections(raw);
    var citations = new ArrayList<Citation>();
    for (int n = 0; n < selections.size(); n++) {
      var selection = selections.get(n);
      JsonNode trace = resolutions.isArray() && n < resolutions.size() ? resolutions.get(n) : null;
      if (trace != null && trace.has("at")) {
        trace = null;
        for (var saved : resolutions)
          if (saved.path("at").asText().equals(selection.at())) {
            trace = saved;
            break;
          }
      }
      var corpus = new HashSet<String>();
      if (trace != null)
        for (var span : trace.path("spans")) corpus.addAll(tokens(span.path("text").asText("")));
      JsonNode development =
          developments.isArray() && selection.developmentIndex() < developments.size()
              ? developments.get(selection.developmentIndex())
              : null;
      if (revision.startsWith("grounded-findings-"))
        development = raw.path("developments").path(selection.developmentIndex());
      Double overlap = null;
      if (trace != null
          && trace.path("spans").isArray()
          && !trace.path("spans").isEmpty()
          && development != null) {
        var terms =
            tokens(
                development
                    .path(revision.startsWith("grounded-findings-") ? "does" : "description")
                    .asText(""));
        if (!terms.isEmpty()) {
          long shared = terms.stream().filter(corpus::contains).count();
          overlap =
              BigDecimal.valueOf((double) shared / terms.size())
                  .setScale(2, RoundingMode.HALF_UP)
                  .doubleValue();
        }
      }
      var range = selection.range();
      citations.add(
          new Citation(
              selection.developmentIndex(),
              selection.at(),
              (revision.equals("grounded-lines-2") || revision.startsWith("grounded-findings-"))
                  ? (trace == null ? null : trace.path("unit").asText(null))
                  : range.path("unit").asText(),
              integer(range.path("firstLine")),
              integer(range.path("lastLine")),
              overlap));
    }
    var rawDevelopments = raw.path("developments");
    var keys =
        rawDevelopments.isArray() && !rawDevelopments.isEmpty()
            ? keys(rawDevelopments.get(0))
            : List.<String>of();
    var expected =
        keys(schema.path("properties").path("developments").path("items").path("properties"));
    Boolean matches = keys.isEmpty() || expected.isEmpty() ? null : keys.equals(expected);
    return new Trial(
        folder.getFileName().toString(),
        revision,
        receipt.get("sampling"),
        receipt.path("state").asText("NOT_RECORDED"),
        integer(receipt.path("promptTokens")),
        integer(attempt.path("receipt").path("response").path("tokens_predicted")),
        count(candidates, "developments"),
        count(candidates, "issues"),
        count(candidates, "associations"),
        kinds,
        dates,
        developments.isArray() ? withoutLinks : null,
        new KeyOrder(keys, matches),
        List.copyOf(citations),
        revision.startsWith("grounded-findings-") ? links(raw, resolutions) : List.of());
  }

  private static List<LinkCoverage> links(JsonNode raw, JsonNode resolutions) {
    var names = new java.util.HashMap<String, String>();
    for (var issue : raw.path("issues"))
      names.put(issue.path("id").asText(), issue.path("name").asText(""));
    var traces = new java.util.HashMap<String, JsonNode>();
    for (var trace : resolutions) traces.put(trace.path("at").asText(), trace);
    var result = new ArrayList<LinkCoverage>();
    var developments = raw.path("developments");
    for (int d = 0; d < developments.size(); d++) {
      var links = developments.get(d).path("links");
      for (int a = 0; a < links.size(); a++) {
        var link = links.get(a);
        String at = "developments[" + d + "].links[" + a + "]";
        String name = names.getOrDefault(link.path("issueId").asText(), "");
        var terms = tokens(name);
        var corpus = new HashSet<String>();
        boolean resolved = true;
        for (int e = 0; e < link.path("evidence").size(); e++) {
          var trace = traces.get(at + ".evidence[" + e + "]");
          if (trace == null
              || (trace.has("problem") && !trace.path("problem").isNull())
              || !trace.path("spans").isArray()
              || trace.path("spans").isEmpty()) {
            resolved = false;
          } else {
            for (var span : trace.path("spans"))
              corpus.addAll(tokens(span.path("text").asText("")));
          }
        }
        Double coverage =
            resolved && !terms.isEmpty()
                ? BigDecimal.valueOf(
                        (double) terms.stream().filter(corpus::contains).count() / terms.size())
                    .setScale(2, RoundingMode.HALF_UP)
                    .doubleValue()
                : null;
        result.add(new LinkCoverage(d, at, name, coverage));
      }
    }
    return List.copyOf(result);
  }

  private static List<Selection> selections(JsonNode raw) {
    var result = new ArrayList<Selection>();
    var developments = raw.path("developments");
    for (int d = 0; d < developments.size(); d++) {
      var development = developments.get(d);
      String prefix = "developments[" + d + "]";
      String field = development.has("evidence") ? "evidence" : "citations";
      add(result, development.path(field), d, prefix + "." + field);
      String links = development.has("links") ? "links" : "associations";
      var associations = development.path(links);
      for (int a = 0; a < associations.size(); a++) {
        var association = associations.get(a);
        String citations = association.has("evidence") ? "evidence" : "citations";
        add(
            result,
            association.path(citations),
            d,
            prefix + "." + links + "[" + a + "]." + citations);
      }
    }
    return result;
  }

  private static void add(List<Selection> result, JsonNode ranges, int development, String at) {
    for (int i = 0; i < ranges.size(); i++)
      result.add(new Selection(development, at + "[" + i + "]", ranges.get(i)));
  }

  private static Set<String> tokens(String text) {
    var tokens = new HashSet<String>();
    var matcher = TOKEN.matcher(text.toLowerCase(Locale.ROOT));
    while (matcher.find()) {
      String word = matcher.group();
      if (word.codePointCount(0, word.length()) >= 3) tokens.add(word);
    }
    return tokens;
  }

  private static List<String> keys(JsonNode object) {
    return object.isObject()
        ? object.properties().stream().map(Map.Entry::getKey).toList()
        : List.of();
  }

  private static Integer count(JsonNode object, String field) {
    return object.path(field).isArray() ? object.path(field).size() : null;
  }

  private static Integer integer(JsonNode value) {
    return value.isIntegralNumber() && value.canConvertToInt() ? value.asInt() : null;
  }

  private static JsonNode read(Path file) throws IOException {
    return EvaluationJson.MAPPER.readTree(Files.readString(file));
  }

  private static JsonNode optional(Path file) throws IOException {
    return Files.exists(file) ? read(file) : EvaluationJson.MAPPER.createObjectNode();
  }

  private static String markdown(List<Trial> trials) {
    var out =
        new StringBuilder(
            "# Trial summary\n\n"
                + "Offline diagnostics. Lexical overlap does not determine acceptance.\n\n");
    out.append(
        "| Trial | Revision | Sampling | State | Prompt | Output | Developments | Issues |"
            + " Associations | Kinds | Date roles | Without links | Raw keys | Order matches |\n");
    out.append("|---|---|---|---|---:|---:|---:|---:|---:|---|---|---:|---|---|\n");
    for (var trial : trials) {
      var sampling = new ArrayList<String>();
      for (String key :
          List.of("temperature", "topP", "topK", "minP", "presencePenalty", "repeatPenalty"))
        sampling.add(
            trial.sampling() == null
                ? "not recorded"
                : trial.sampling().path(key).asText("not recorded"));
      row(
          out,
          trial.trial(),
          trial.contractRevision(),
          String.join(" / ", sampling),
          trial.state(),
          trial.promptTokens(),
          trial.outputTokens(),
          trial.developments(),
          trial.issues(),
          trial.associations(),
          trial.kinds(),
          trial.dateRoles(),
          trial.developmentsWithoutLinks(),
          String.join(", ", trial.rawKeyOrder().keys()),
          trial.rawKeyOrder().matchesSchema());
    }
    out.append(
        "\n"
            + "## Citations\n\n"
            + "| Trial | Development index | Citation | Unit | Lines | Lexical overlap |\n"
            + "|---|---:|---|---|---|---:|\n");
    for (var trial : trials)
      for (var citation : trial.citations())
        row(
            out,
            trial.trial(),
            citation.developmentIndex(),
            citation.at(),
            citation.unit(),
            citation.firstLine() + "-" + citation.lastLine(),
            citation.lexicalOverlap() == null
                ? null
                : String.format(Locale.ROOT, "%.2f", citation.lexicalOverlap()));
    out.append(
        "\n"
            + "## Links\n\n"
            + "Name coverage is lexical and does not establish support. It does not determine"
            + " acceptance.\n\n"
            + "| Trial | Development index | Link path | Issue | Name coverage |\n"
            + "|---|---:|---|---|---:|\n");
    for (var trial : trials)
      for (var link : trial.links())
        row(
            out,
            trial.trial(),
            link.developmentIndex(),
            link.at(),
            link.issue(),
            link.nameCoverage() == null
                ? "n/a"
                : String.format(Locale.ROOT, "%.2f", link.nameCoverage()));
    return out.toString();
  }

  private static void row(StringBuilder out, Object... values) {
    out.append('|');
    for (var value : values)
      out.append(' ')
          .append(
              value == null
                  ? "not available"
                  : value.toString().replace("|", "\\|").replace("\r", " ").replace("\n", " "))
          .append(" |");
    out.append('\n');
  }
}
