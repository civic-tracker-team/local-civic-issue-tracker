package org.civictracker.analysis.model;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import org.civictracker.analysis.domain.CandidateValidator;
import org.civictracker.analysis.domain.Candidates;
import org.civictracker.analysis.domain.CitationIndex;
import org.civictracker.analysis.domain.MeetingInput;
import org.civictracker.analysis.domain.Problem;
import org.civictracker.analysis.evaluation.EvaluationJson;
import tools.jackson.databind.JsonNode;

/** One versioned wire format and complete, source-addressable input. */
public final class ModelContract {
  public static final String REVISION = "grounded-findings-3";
  public static final String REVISION_DETAIL = "unit-6c-synthetic-wording";

  private ModelContract() {}

  private record LinkKey(String issueId, Candidates.Basis basis) {}

  public record Request(List<Map<String, String>> messages, JsonNode schema, String inputText) {
    public Request {
      messages = messages.stream().map(Map::copyOf).toList();
    }
  }

  public static Request request(CitationIndex index) {
    var schema = schema(index);
    var messages = messages(index, schema);
    return new Request(messages, schema, messages.get(1).get("content"));
  }

  public static Interpretation interpret(String content, CitationIndex index, MeetingInput input) {
    JsonNode raw;
    Wire.Reply reply;
    try {
      raw = EvaluationJson.MAPPER.readTree(content);
      if (!raw.isObject())
        return new Interpretation.Malformed(
            List.of(new Problem(Problem.Code.MALFORMED_REPLY, "$")));
      reply = EvaluationJson.MAPPER.treeToValue(raw, Wire.Reply.class);
    } catch (RuntimeException exception) {
      return new Interpretation.Malformed(List.of(new Problem(Problem.Code.MALFORMED_REPLY, "$")));
    }
    var resolution = new Resolution(index);
    var developments = new ArrayList<Candidates.Development>();
    var associations = new ArrayList<Candidates.Association>();
    var mergeNotes = new ArrayList<Problem>();
    for (int d = 0; d < reply.developments().size(); d++) {
      var value = reply.developments().get(d);
      String id = "d" + (d + 1);
      String at = "developments[" + d + "]";
      var refs = resolution.resolve(value.evidence(), at + ".evidence");
      developments.add(
          new Candidates.Development(
              id,
              Wire.render(value.finding(), value.does()),
              value.finding().status(),
              value.finding().dates(input.meetingDate()),
              refs));
      var links = new LinkedHashMap<LinkKey, LinkedHashSet<String>>();
      for (int a = 0; a < value.links().size(); a++) {
        var association = value.links().get(a);
        String location = at + ".links[" + a + "]";
        var evidence = resolution.resolve(association.evidence(), location + ".evidence");
        var key = new LinkKey(association.issueId(), association.basis());
        if (links.containsKey(key))
          mergeNotes.add(new Problem(Problem.Code.LINKS_MERGED, location));
        links.computeIfAbsent(key, ignored -> new LinkedHashSet<>()).addAll(evidence);
      }
      for (var link : links.entrySet())
        associations.add(
            new Candidates.Association(
                id, link.getKey().issueId(), link.getKey().basis(), List.copyOf(link.getValue())));
    }
    if (resolution.unresolved)
      return new Interpretation.Unresolved(raw, resolution.traces, resolution.problems);
    resolution.problems.addAll(mergeNotes);
    var issues =
        reply.issues().stream()
            .map(issue -> new Candidates.Issue(issue.id(), issue.name(), issue.priorIssueId()))
            .toList();
    var candidates =
        new Candidates(
            List.copyOf(resolution.evidence.values()), developments, issues, associations);
    for (int d = 0; d < reply.developments().size(); d++)
      if (CitationIndex.isBlank(reply.developments().get(d).does()))
        resolution.problems.add(
            new Problem(Problem.Code.EMPTY_TEXT, "developments[" + d + "].does"));
    resolution.problems.addAll(new CandidateValidator().validate(candidates, input).problems());
    var linked =
        associations.stream()
            .map(Candidates.Association::issueId)
            .collect(java.util.stream.Collectors.toSet());
    for (int n = 0; n < issues.size(); n++)
      if (!linked.contains(issues.get(n).id()))
        resolution.problems.add(new Problem(Problem.Code.UNUSED_ISSUE, "issues[" + n + "]"));
    candidates =
        new Candidates(
            candidates.evidence(),
            developments,
            issues.stream().filter(issue -> linked.contains(issue.id())).toList(),
            associations);
    return new Interpretation.Interpreted(raw, resolution.traces, candidates, resolution.problems);
  }

  private static final class Resolution {
    private final CitationIndex index;
    private final LinkedHashMap<CitationIndex.Span, Candidates.Evidence> evidence =
        new LinkedHashMap<>();
    private final List<Interpretation.Trace> traces = new ArrayList<>();
    private final List<Problem> problems = new ArrayList<>();
    private boolean unresolved;

    private Resolution(CitationIndex index) {
      this.index = index;
    }

    private List<String> resolve(List<Wire.Citation> citations, String at) {
      if (citations.isEmpty()) {
        problems.add(new Problem(Problem.Code.MISSING_EVIDENCE, at));
        unresolved = true;
      }
      var refs = new LinkedHashSet<String>();
      for (int c = 0; c < citations.size(); c++) {
        var citation = citations.get(c);
        String location = at + "[" + c + "]";
        switch (index.resolveLines(citation.firstLine(), citation.lastLine())) {
          case CitationIndex.Invalid invalid -> {
            unresolved = true;
            problems.add(new Problem(invalid.code(), location));
            traces.add(
                new Interpretation.Trace(
                    location,
                    null,
                    citation.firstLine(),
                    citation.lastLine(),
                    List.of(),
                    invalid.code()));
          }
          case CitationIndex.Found found -> {
            traces.add(
                new Interpretation.Trace(
                    location,
                    index.unitForLine(citation.firstLine()),
                    citation.firstLine(),
                    citation.lastLine(),
                    found.spans(),
                    null));
            for (var span : found.spans()) {
              if (CitationIndex.isBlank(span.text())) continue;
              var retained =
                  evidence.computeIfAbsent(
                      span, s -> new Candidates.Evidence("e" + (evidence.size() + 1), s));
              refs.add(retained.id());
            }
          }
        }
      }
      return List.copyOf(refs);
    }
  }

  private static JsonNode schema(CitationIndex index) {
    return SchemaSpec.schema(index);
  }

  private static List<Map<String, String>> messages(CitationIndex index, JsonNode schema) {
    String instructions =
        resource("instructions.txt").replace("{{GLOSSARY}}", glossary())
            + "\nThe required JSON schema is:\n"
            + EvaluationJson.MAPPER.writeValueAsString(schema)
            + "\nControlled worked examples:\n"
            + examples();
    String input = render(index);
    return List.of(
        Map.of("role", "system", "content", instructions),
        Map.of("role", "user", "content", input));
  }

  private static String render(CitationIndex index) {
    var view = index.view();
    var out =
        new StringBuilder("MEETING ")
            .append(escape(view.officialId()))
            .append(" | ")
            .append(view.meetingDate())
            .append(" | ")
            .append(escape(view.title()))
            .append('\n');
    if (view.priorIssues().isEmpty()) out.append("PRIOR ISSUES: none\n");
    else
      for (var issue : view.priorIssues())
        out.append("PRIOR ISSUE ")
            .append(escape(issue.id()))
            .append(" | reviewed through ")
            .append(issue.reviewedThrough())
            .append(" | ")
            .append(escape(issue.description()))
            .append('\n');
    var units = index.units();
    for (int start = 0; start < units.size(); ) {
      var first = units.get(start);
      int end = start + 1;
      while (end < units.size() && units.get(end).document().equals(first.document())) end++;
      out.append("\n=== ")
          .append(first.document())
          .append(" | ")
          .append(escape(first.disposition()))
          .append(" ===\n");
      String disposition = first.disposition();
      for (int u = start; u < end; u++) {
        var unit = units.get(u);
        out.append("--- section ").append(u - start + 1).append(" of ").append(unit.document());
        if (!unit.disposition().equals(disposition)) {
          out.append(" | ").append(escape(unit.disposition()));
          disposition = unit.disposition();
        }
        out.append(" ---\n");
        for (var line : unit.lines()) {
          String text = line.text();
          if (text.endsWith("\n")) {
            text = text.substring(0, text.length() - 1);
            if (text.endsWith("\r")) text = text.substring(0, text.length() - 1);
          }
          out.append('L').append(line.number());
          if (!CitationIndex.isBlank(text)) out.append(' ').append(escape(text));
          out.append('\n');
        }
      }
      start = end;
    }
    return out.toString();
  }

  private static String examples() {
    var out = new StringBuilder();
    int first = 90001;
    for (String name : List.of("EX1", "EX2", "EX3", "EX4", "EX5")) {
      var example = EvaluationJson.MAPPER.readTree(resource("examples/" + name + ".json"));
      String text = "";
      for (var line : example.path("source").path("lines"))
        text += line.path("text").asText() + "\n";
      var id =
          java.util.UUID.nameUUIDFromBytes(
              example.path("name").asText().getBytes(StandardCharsets.UTF_8));
      var segment = new MeetingInput.Segment(id, text, "SYNTHETIC", Map.of(), null, true);
      var unit = new MeetingInput.Unit(0, "SYNTHETIC", "", List.of(segment), List.of());
      var source =
          new MeetingInput.Source(id, id, id, "", 0, id, "synthetic", List.of(), List.of(unit));
      var input =
          new MeetingInput(
              id,
              "synthetic",
              java.time.LocalDate.of(2026, 4, 3),
              example.path("name").asText(),
              List.of(source),
              List.of());
      var index = CitationIndex.build(input, first);
      out.append(render(index)).append("Reply:\n").append(example.path("reply")).append("\n\n");
      first += index.lineCount();
    }
    return out.toString();
  }

  private static String glossary() {
    var out = new StringBuilder();
    for (var kind : Wire.Kind.values())
      out.append(kind.name()).append(": ").append(kind.label).append('\n');
    for (var proposer : Wire.Proposer.values())
      out.append("Proposer ")
          .append(proposer.name())
          .append(": ")
          .append(proposer.label)
          .append('\n');
    for (var actor : Wire.Actor.values())
      out.append("Actor ").append(actor.name()).append(": ").append(actor.label).append('\n');
    for (var matter : Wire.MatterType.values())
      out.append("Matter ").append(matter.name()).append(": ").append(matter.label).append('\n');
    return out.toString();
  }

  private static String escape(String text) {
    var out = new StringBuilder();
    text.codePoints()
        .forEach(c -> {
          if (c == '\\') out.append("\\\\");
          else if (c == '<') out.append("\\u003c");
          else if (c < 0x20 && c != '\t')
            out.append(String.format(java.util.Locale.ROOT, "\\u%04x", c));
          else out.appendCodePoint(c);
        });
    return out.toString();
  }

  private static String resource(String name) {
    try (var stream = ModelContract.class.getResourceAsStream("/evaluation/" + name)) {
      if (stream == null) throw new IllegalStateException("Missing model contract resource");
      return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
    } catch (IOException exception) {
      throw new IllegalStateException("Cannot read model contract", exception);
    }
  }
}
