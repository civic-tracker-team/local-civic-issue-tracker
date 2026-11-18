package org.civictracker.analysis.model;

import static org.junit.jupiter.api.Assertions.*;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import java.time.LocalDate;
import java.util.*;
import org.civictracker.analysis.domain.*;
import org.civictracker.analysis.evaluation.EvaluationJson;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;

class SchemaSpecTests {
  private CitationIndex index(List<MeetingInput.PriorIssue> prior) {
    var id = UUID.randomUUID();
    var source =
        new MeetingInput.Source(
            id,
            id,
            id,
            "a".repeat(64),
            1,
            id,
            "profile",
            List.of(),
            List.of(
                new MeetingInput.Unit(
                    0,
                    "OCR",
                    "",
                    List.of(new MeetingInput.Segment(id, "one\ntwo", "OCR", Map.of(), null, true)),
                    List.of())));
    return CitationIndex.build(
        new MeetingInput(
            id, "synthetic", LocalDate.of(2026, 4, 3), "Riverton", List.of(source), prior));
  }

  private List<String> keys(JsonNode value) {
    return value.properties().stream().map(Map.Entry::getKey).toList();
  }

  private JsonNode field(JsonNode object, String field) {
    return object.path("properties").path(field);
  }

  private void record(JsonNode schema, Class<?> type, String discriminator, Set<Class<?>> checked) {
    var expected = new ArrayList<String>();
    if (discriminator != null) expected.add(discriminator);
    Arrays.stream(type.getRecordComponents()).forEach(c -> expected.add(c.getName()));
    assertEquals(expected, keys(schema.path("properties")), type.getName());
    assertEquals(
        expected,
        EvaluationJson.MAPPER.convertValue(
            schema.path("required"), new tools.jackson.core.type.TypeReference<List<String>>() {}));
    assertFalse(schema.path("additionalProperties").asBoolean(true));
    checked.add(type);
  }

  private void variants(
      JsonNode schema, Class<?> base, String discriminator, Set<Class<?>> checked) {
    var types = base.getAnnotation(JsonSubTypes.class).value();
    assertEquals(types.length, schema.path("anyOf").size());
    for (int i = 0; i < types.length; i++) {
      var branch = schema.path("anyOf").get(i);
      assertEquals(types[i].name(), field(branch, discriminator).path("const").asText());
      record(branch, types[i].value(), discriminator, checked);
    }
  }

  @Test
  void everyWireRecordHasTheSameOrderedRequiredPropertiesAsItsSchema() {
    var root = SchemaSpec.schema(index(List.of()));
    var checked = new HashSet<Class<?>>();
    record(root, Wire.Reply.class, null, checked);
    var issue = field(root, "issues").path("items");
    record(issue, Wire.IssueDraft.class, null, checked);
    var development = field(root, "developments").path("items");
    record(development, Wire.DevelopmentDraft.class, null, checked);
    var cite = field(development, "evidence").path("items");
    record(cite, Wire.Citation.class, null, checked);
    var link = field(development, "links").path("items");
    record(link, Wire.LinkDraft.class, null, checked);
    var finding = field(development, "finding");
    variants(finding, Wire.Finding.class, "kind", checked);
    var dates = field(finding.path("anyOf").get(0), "planned").path("items");
    variants(dates, Wire.DateValue.class, "precision", checked);
    assertEquals(
        new HashSet<>(
            Arrays.stream(Wire.class.getDeclaredClasses()).filter(Class::isRecord).toList()),
        checked);
    assertEquals(List.of("issues", "developments"), keys(root.path("properties")));
    assertEquals(
        List.of("evidence", "finding", "does", "links"), keys(development.path("properties")));
    assertFalse(cite.path("properties").has("unit"));
    assertEquals(1, field(cite, "firstLine").path("minimum").asInt());
    assertEquals(2, field(cite, "lastLine").path("maximum").asInt());
    assertEquals(1, field(development, "evidence").path("minItems").asInt());
  }

  @Test
  void enumsAndKindTagsComeFromTheWireTypes() {
    var root = SchemaSpec.schema(index(List.of()));
    var kinds = field(field(root, "developments").path("items"), "finding").path("anyOf");
    assertEquals(
        Arrays.stream(Wire.Proposer.values()).map(Enum::name).toList(),
        EvaluationJson.MAPPER.convertValue(
            field(kinds.get(0), "proposedBy").path("enum"), List.class));
    assertEquals(
        Arrays.stream(Wire.Actor.values()).map(Enum::name).toList(),
        EvaluationJson.MAPPER.convertValue(
            field(kinds.get(1), "actedBy").path("enum"), List.class));
    assertEquals(
        Arrays.stream(Wire.MatterType.values()).map(Enum::name).toList(),
        EvaluationJson.MAPPER.convertValue(
            field(field(root, "issues").path("items"), "matterType").path("enum"), List.class));
    var annotations = Wire.Finding.class.getAnnotation(JsonSubTypes.class).value();
    for (int i = 0; i < Wire.Kind.values().length; i++) {
      assertEquals(Wire.Kind.values()[i].name(), annotations[i].name());
      assertEquals(Wire.Kind.values()[i].type, annotations[i].value());
    }
    assertEquals(
        Wire.ISSUE_ID_PATTERN,
        field(field(root, "issues").path("items"), "id").path("pattern").asText());
  }

  @Test
  void priorIssueReferencesAreRestrictedToTheSuppliedContextOrNull() {
    var empty = SchemaSpec.schema(index(List.of()));
    assertEquals(
        "null", field(field(empty, "issues").path("items"), "priorIssueId").path("type").asText());
    var prior =
        new MeetingInput.PriorIssue(
            "known", "Known parcel", LocalDate.of(2026, 4, 2), "b".repeat(64));
    var schema = SchemaSpec.schema(index(List.of(prior)));
    var allowed = field(field(schema, "issues").path("items"), "priorIssueId").path("anyOf");
    assertEquals(
        List.of("known"),
        EvaluationJson.MAPPER.convertValue(allowed.get(0).path("enum"), List.class));
    assertEquals("null", allowed.get(1).path("type").asText());
  }
}
