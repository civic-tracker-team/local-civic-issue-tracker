package org.civictracker.analysis;

import static org.junit.jupiter.api.Assertions.*;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.civictracker.analysis.domain.*;
import org.civictracker.analysis.model.*;
import org.junit.jupiter.api.Test;

class FindingsRegressionTests {
  @Test
  void globalCitationsRejectOldUnitsAndCrossSectionRanges() {
    var input = input();
    var index = CitationIndex.build(input);
    var valid = reply(ACTION, "[]", "[]");
    assertTrue(
        assertInstanceOf(
                Interpretation.Interpreted.class, ModelContract.interpret(valid, index, input))
            .accepted());
    assertInstanceOf(
        Interpretation.Malformed.class,
        ModelContract.interpret(
            valid.replace("\"firstLine\":4", "\"unit\":\"U99\",\"firstLine\":4"), index, input));
    var cross =
        assertInstanceOf(
            Interpretation.Unresolved.class,
            ModelContract.interpret(
                valid.replace("\"firstLine\":4", "\"firstLine\":3"), index, input));
    assertEquals(
        List.of(new Problem(Problem.Code.RANGE_CROSSES_UNITS, "developments[0].evidence[0]")),
        cross.problems());
    var outside =
        assertInstanceOf(
            Interpretation.Unresolved.class,
            ModelContract.interpret(
                valid
                    .replace("\"firstLine\":4", "\"firstLine\":6")
                    .replace("\"lastLine\":5", "\"lastLine\":6"),
                index,
                input));
    assertEquals(Problem.Code.LINE_OUT_OF_RANGE, outside.problems().getFirst().code());
  }

  static MeetingInput input() {
    var units =
        List.of(
                "Council names\nCover",
                "Agenda",
                "It is recommended that Council receive the report.\nRESULT: direct a study [6-1]")
            .stream()
            .map(text ->
                new MeetingInput.Unit(
                    0,
                    "OCR",
                    "",
                    List.of(
                        new MeetingInput.Segment(
                            UUID.randomUUID(), text, "OCR", Map.of(), null, true)),
                    List.of()))
            .toList();
    var source =
        new MeetingInput.Source(
            UUID.randomUUID(),
            UUID.randomUUID(),
            UUID.randomUUID(),
            "a".repeat(64),
            1,
            UUID.randomUUID(),
            "profile",
            List.of(),
            units);
    return new MeetingInput(
        UUID.randomUUID(),
        "synthetic",
        LocalDate.of(2026, 4, 3),
        "Riverton",
        List.of(source),
        List.of());
  }

  static String reply(String finding, String issues, String links) {
    return """
    {"issues":%s,"developments":[{"evidence":[{"firstLine":4,"lastLine":5}],
    "finding":%s,"does":"receive the report and provide direction","links":%s}]}
    """
        .formatted(issues, finding, links);
  }

  static final String PROPOSAL =
      "{\"kind\":\"PROPOSAL\",\"proposedBy\":\"NOT_STATED\",\"planned\":[]}";
  static final String ACTION = "{\"kind\":\"ACTION\",\"actedBy\":\"CITY_COUNCIL\",\"planned\":[]}";

  private Interpretation.Interpreted interpreted(String raw) {
    var input = input();
    return assertInstanceOf(
        Interpretation.Interpreted.class,
        ModelContract.interpret(raw, CitationIndex.build(input), input));
  }

  @Test
  void proposalHasHostRenderedSubjectAndNoOccurrence() {
    var result = interpreted(reply(PROPOSAL, "[]", "[]"));
    assertTrue(result.accepted());
    var dev = result.candidates().developments().getFirst();
    assertEquals(
        "Proposed for decision: receive the report and provide direction", dev.description());
    assertEquals(Candidates.Status.PROPOSED, dev.status());
    assertEquals(
        List.of(
            new Candidates.DateClaim(
                Candidates.Role.MEETING, Candidates.Precision.DAY, "2026-04-03", null, false)),
        dev.dates());
  }

  @Test
  void actionGetsOccurrenceAndMeetingFromInput() {
    var result = interpreted(reply(ACTION, "[]", "[]"));
    assertTrue(result.accepted());
    var dev = result.candidates().developments().getFirst();
    assertEquals(Candidates.Status.RECORDED, dev.status());
    assertTrue(dev.description().startsWith("Recorded action by the City Council: "));
    assertEquals(
        List.of(Candidates.Role.OCCURRENCE, Candidates.Role.MEETING),
        dev.dates().stream().map(Candidates.DateClaim::role).toList());
    assertTrue(
        dev.dates().stream().allMatch(d -> d.start().equals("2026-04-03") && !d.approximate()));
  }

  @Test
  void earlierActionKeepsUnknownOccurrenceAndPlannedPrecision() {
    var result =
        interpreted(
            reply(
                """
                {"kind":"EARLIER_ACTION","actedBy":"STAFF","occurred":{"precision":"UNKNOWN","approximate":true},
                "planned":[{"precision":"MONTH","value":"2027-02","approximate":true}]}
                """,
                "[]",
                "[]"));
    assertTrue(result.accepted());
    var dates = result.candidates().developments().getFirst().dates();
    assertEquals(Candidates.Precision.UNKNOWN, dates.get(0).precision());
    assertNull(dates.get(0).start());
    assertTrue(dates.get(0).approximate());
    assertEquals(Candidates.Role.PLANNED, dates.get(2).role());
    assertEquals(Candidates.Precision.MONTH, dates.get(2).precision());
  }

  @Test
  void oneDevelopmentLinksTwoMattersAndDropsUnusedIssue() {
    String issues =
        """
        [{"id":"i1","matterType":"PROPERTY","name":"Parcel Alpha","priorIssueId":null},
        {"id":"i2","matterType":"PROJECT","name":"Elm shelter","priorIssueId":null},
        {"id":"i3","matterType":"PROGRAM","name":"Unused","priorIssueId":null}]
        """;
    String links =
        """
        [{"issueId":"i1","basis":"SOURCE_STATED","evidence":[{"firstLine":4,"lastLine":5}]},
        {"issueId":"i2","basis":"INFERRED","evidence":[{"firstLine":4,"lastLine":5}]}]
        """;
    var result = interpreted(reply(ACTION, issues, links));
    assertTrue(result.accepted());
    assertEquals(
        List.of("i1", "i2"),
        result.candidates().issues().stream().map(Candidates.Issue::id).toList());
    assertEquals(
        List.of("d1", "d1"),
        result.candidates().associations().stream()
            .map(Candidates.Association::developmentId)
            .toList());
    assertEquals(1, result.candidates().evidence().size());
    assertEquals(List.of(new Problem(Problem.Code.UNUSED_ISSUE, "issues[2]")), result.problems());
    assertEquals(3, result.reply().path("issues").size());
  }

  @Test
  void unknownIssueIsRejected() {
    var result =
        interpreted(
            reply(
                ACTION,
                "[]",
                "[{\"issueId\":\"i9\",\"basis\":\"INFERRED\",\"evidence\":[{\"firstLine\":4,\"lastLine\":5}]}]"));
    assertFalse(result.accepted());
    assertEquals(Problem.Code.UNKNOWN_ISSUE, result.problems().getFirst().code());
  }

  @Test
  void findingsSchemaHasEvidenceBeforeMeaningAndIssuesBeforeDevelopments() {
    var schema = ModelContract.request(CitationIndex.build(input())).schema();
    assertEquals(
        List.of("issues", "developments"),
        schema.path("properties").properties().stream().map(Map.Entry::getKey).toList());
    assertEquals(
        List.of("evidence", "finding", "does", "links"),
        schema
            .path("properties")
            .path("developments")
            .path("items")
            .path("properties")
            .properties()
            .stream()
            .map(Map.Entry::getKey)
            .toList());
  }
}
