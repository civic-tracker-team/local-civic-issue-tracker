package org.civictracker.analysis.model;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.civictracker.analysis.domain.Candidates;
import org.civictracker.analysis.domain.CitationIndex;
import org.civictracker.analysis.domain.MeetingInput;
import org.civictracker.analysis.evaluation.EvaluationJson;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ObjectNode;

/** Builds the grammar schema in wire-record order. */
final class SchemaSpec {
  private SchemaSpec() {}

  static JsonNode schema(CitationIndex index) {
    if (index.lineCount() == 0)
      throw new IllegalArgumentException("A meeting needs prepared source lines");
    var line = node("integer").put("minimum", 1).put("maximum", index.lineCount());
    var citation = object(Wire.Citation.class, Map.of("firstLine", line, "lastLine", line));
    var evidence = array(citation).put("minItems", 1);
    var flag = node("boolean");
    var dates = new LinkedHashMap<Class<?>, Map<String, JsonNode>>();
    dates.put(Wire.Day.class, Map.of("value", string().put("format", "date"), "approximate", flag));
    dates.put(
        Wire.Month.class,
        Map.of(
            "value", string().put("pattern", "^[0-9]{4}-(0[1-9]|1[0-2])$"), "approximate", flag));
    dates.put(
        Wire.Year.class,
        Map.of("value", string().put("pattern", "^[0-9]{4}$"), "approximate", flag));
    dates.put(
        Wire.DateRange.class,
        Map.of(
            "start",
            string().put("format", "date"),
            "end",
            string().put("format", "date"),
            "approximate",
            flag));
    dates.put(Wire.UnknownDate.class, Map.of("approximate", flag));
    var date = union(Wire.DateValue.class, "precision", dates);
    var planned = array(date);
    var kinds = new LinkedHashMap<Class<?>, Map<String, JsonNode>>();
    kinds.put(
        Wire.Proposal.class,
        Map.of("proposedBy", enumeration(Wire.Proposer.class), "planned", planned));
    kinds.put(
        Wire.Action.class, Map.of("actedBy", enumeration(Wire.Actor.class), "planned", planned));
    kinds.put(
        Wire.EarlierAction.class,
        Map.of("actedBy", enumeration(Wire.Actor.class), "occurred", date, "planned", planned));
    kinds.put(Wire.Unclear.class, Map.of("planned", planned));
    var finding = union(Wire.Finding.class, "kind", kinds);
    var id = string().put("pattern", Wire.ISSUE_ID_PATTERN);
    var link =
        object(
            Wire.LinkDraft.class,
            Map.of(
                "issueId", id, "basis", enumeration(Candidates.Basis.class), "evidence", evidence));
    var development =
        object(
            Wire.DevelopmentDraft.class,
            Map.of(
                "evidence", evidence, "finding", finding, "does", string(), "links", array(link)));
    var issue =
        object(
            Wire.IssueDraft.class,
            Map.of(
                "id",
                id,
                "matterType",
                enumeration(Wire.MatterType.class),
                "name",
                string(),
                "priorIssueId",
                priorIssues(index)));
    return object(
        Wire.Reply.class, Map.of("issues", array(issue), "developments", array(development)));
  }

  private static ObjectNode priorIssues(CitationIndex index) {
    var ids = index.view().priorIssues().stream().map(MeetingInput.PriorIssue::id).toList();
    if (ids.isEmpty()) return node("null");
    var allowed = node("string");
    allowed.set("enum", EvaluationJson.MAPPER.valueToTree(ids));
    var result = EvaluationJson.MAPPER.createObjectNode();
    result.set("anyOf", EvaluationJson.MAPPER.valueToTree(List.of(allowed, node("null"))));
    return result;
  }

  private static ObjectNode union(
      Class<?> type, String discriminator, Map<Class<?>, Map<String, JsonNode>> variants) {
    var result = EvaluationJson.MAPPER.createObjectNode();
    var branches = result.putArray("anyOf");
    for (var variant : type.getAnnotation(JsonSubTypes.class).value()) {
      var fields = variants.get(variant.value());
      if (fields == null) throw new IllegalStateException("Missing wire variant");
      branches.add(object(variant.value(), fields, discriminator, variant.name()));
    }
    return result;
  }

  private static ObjectNode object(Class<?> type, Map<String, JsonNode> fields) {
    return object(type, fields, null, null);
  }

  private static ObjectNode object(
      Class<?> type, Map<String, JsonNode> fields, String discriminator, String tag) {
    var components = type.getRecordComponents();
    if (components.length != fields.size())
      throw new IllegalStateException("Wire schema fields differ");
    var result = node("object").put("additionalProperties", false);
    var properties = result.putObject("properties");
    var required = result.putArray("required");
    if (discriminator != null) {
      properties.set(discriminator, node("string").put("const", tag));
      required.add(discriminator);
    }
    for (var component : components) {
      var value = fields.get(component.getName());
      if (value == null) throw new IllegalStateException("Missing wire schema field");
      properties.set(component.getName(), value);
      required.add(component.getName());
    }
    return result;
  }

  private static ObjectNode enumeration(Class<? extends Enum<?>> type) {
    var result = node("string");
    result.set(
        "enum",
        EvaluationJson.MAPPER.valueToTree(
            Arrays.stream(type.getEnumConstants()).map(Enum::name).toList()));
    return result;
  }

  private static ObjectNode array(JsonNode item) {
    var result = node("array");
    result.set("items", item);
    return result;
  }

  private static ObjectNode string() {
    return node("string").put("minLength", 1);
  }

  private static ObjectNode node(String type) {
    return EvaluationJson.MAPPER.createObjectNode().put("type", type);
  }
}
