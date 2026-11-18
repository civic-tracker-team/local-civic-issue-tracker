package org.civictracker.analysis;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.*;
import java.time.LocalDate;
import java.util.*;
import org.civictracker.analysis.domain.*;
import org.civictracker.analysis.evaluation.*;
import org.civictracker.analysis.model.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import tools.jackson.databind.JsonNode;

class FindingsContractTests {
  @TempDir Path temporary;

  @Test
  void unusedIssueNoteStillLetsTheEvaluatorSucceed() throws Exception {
    var input = input(List.of("one\ntwo"));
    String raw =
        reply(FindingsRegressionTests.ACTION)
            .replace(
                "\"links\":[{\"issueId\":\"i1\",\"basis\":\"SOURCE_STATED\",\"evidence\":[{\"firstLine\":1,\"lastLine\":2}]}]",
                "\"links\":[]");
    var model =
        new ModelClient() {
          public Prepared preflight(List<Map<String, String>> messages, int limit) {
            return new Prepared("p", List.of(1), 100, limit, Map.of());
          }

          public Attempt generate(Prepared p, JsonNode schema, int seed, Sampling sampling) {
            return new Attempt(State.SUCCESS, raw, "eos", Map.of());
          }
        };
    var output = temporary.resolve("unused");
    assertEquals(
        ModelClient.State.SUCCESS,
        new EvaluationRunner(model)
            .evaluate(
                input, 10, 1, ModelClient.Sampling.baseline(), output, temporary.resolve("gate")));
    assertTrue(
        EvaluationJson.MAPPER
            .readTree(Files.readString(output.resolve("candidates.json")))
            .path("issues")
            .isEmpty());
    assertTrue(Files.readString(output.resolve("validation.json")).contains("UNUSED_ISSUE"));
    assertEquals(
        "NOT_REVIEWED",
        EvaluationJson.MAPPER
            .readTree(Files.readString(output.resolve("receipt.json")))
            .path("humanReview")
            .asText());
  }

  private MeetingInput input(List<String> text) {
    var segments =
        text.stream()
            .map(t -> new MeetingInput.Segment(UUID.randomUUID(), t, "OCR", Map.of(), null, true))
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
            List.of(new MeetingInput.Unit(0, "OCR", "", segments, List.of())));
    return new MeetingInput(
        UUID.randomUUID(),
        "synthetic",
        LocalDate.of(2026, 4, 3),
        "Riverton",
        List.of(source),
        List.of());
  }

  private String reply(String finding) {
    return FindingsRegressionTests.reply(
            finding,
            "[{\"id\":\"i1\",\"matterType\":\"PROPERTY\",\"name\":\"Parcel"
                + " Alpha\",\"priorIssueId\":null}]",
            "[{\"issueId\":\"i1\",\"basis\":\"SOURCE_STATED\",\"evidence\":[{\"firstLine\":4,\"lastLine\":5}]}]")
        .replace("\"firstLine\":4", "\"firstLine\":1")
        .replace("\"lastLine\":5", "\"lastLine\":2");
  }

  private Interpretation.Interpreted interpreted(String raw, MeetingInput input) {
    return assertInstanceOf(
        Interpretation.Interpreted.class,
        ModelContract.interpret(raw, CitationIndex.build(input), input));
  }

  @Test
  void identitiesAndSeparateOcrSegmentsRemainStableAcrossLinks() {
    var input = input(List.of("Council directed staff", "to study the parcel."));
    var result = interpreted(reply(FindingsRegressionTests.ACTION), input);
    assertTrue(result.accepted());
    assertEquals(
        List.of("e1", "e2"),
        result.candidates().evidence().stream().map(Candidates.Evidence::id).toList());
    assertEquals(
        input.selectedSegments().stream().map(MeetingInput.Segment::text).toList(),
        result.candidates().evidence().stream().map(e -> e.source().text()).toList());
    assertEquals(List.of("e1", "e2"), result.candidates().associations().getFirst().evidenceIds());
    assertEquals("d1", result.candidates().associations().getFirst().developmentId());
    assertEquals(2, result.traces().size());
  }

  @Test
  void repeatedTextRetainsExactUnicodePositions() {
    var input = input(List.of("😀 yes\nyes\nyes\n"));
    var result =
        interpreted(
            reply(FindingsRegressionTests.ACTION)
                .replace("\"firstLine\":1,\"lastLine\":2", "\"firstLine\":3,\"lastLine\":3"),
            input);
    assertTrue(result.accepted());
    var span = result.candidates().evidence().getFirst().source();
    assertEquals(10, span.start());
    assertEquals(14, span.end());
  }

  @Test
  void plannedDatesKeepEveryPrecisionAndApproximation() {
    var input = input(List.of("one\ntwo"));
    var values =
        List.of(
            "{\"precision\":\"DAY\",\"value\":\"2027-01-15\",\"approximate\":true}",
            "{\"precision\":\"MONTH\",\"value\":\"2027-01\",\"approximate\":true}",
            "{\"precision\":\"YEAR\",\"value\":\"2027\",\"approximate\":true}",
            "{\"precision\":\"RANGE\",\"start\":\"2027-01-15\",\"end\":\"2027-02-01\",\"approximate\":true}",
            "{\"precision\":\"UNKNOWN\",\"approximate\":true}");
    for (int n = 0; n < values.size(); n++) {
      var result =
          interpreted(
              reply(
                  "{\"kind\":\"PROPOSAL\",\"proposedBy\":\"STAFF\",\"planned\":["
                      + values.get(n)
                      + "]}"),
              input);
      assertTrue(result.accepted());
      var date = result.candidates().developments().getFirst().dates().getLast();
      assertEquals(Candidates.Role.PLANNED, date.role());
      assertEquals(Candidates.Precision.values()[n], date.precision());
      assertTrue(date.approximate());
      if (n != 3) assertNull(date.end());
    }
  }

  @Test
  void invalidCalendarAndReversedDatesStillFailHostValidation() {
    var input = input(List.of("one\ntwo"));
    var dates =
        Map.of(
            "{\"precision\":\"DAY\",\"value\":\"2026-02-30\",\"approximate\":false}",
            Problem.Code.INVALID_CALENDAR_DATE,
            "{\"precision\":\"RANGE\",\"start\":\"2026-02-01\",\"end\":\"2026-01-15\",\"approximate\":false}",
            Problem.Code.REVERSED_DATE_RANGE);
    dates.forEach((date, code) -> {
      var result =
          interpreted(
              reply(
                  "{\"kind\":\"EARLIER_ACTION\",\"actedBy\":\"STAFF\",\"occurred\":"
                      + date
                      + ",\"planned\":[]}"),
              input);
      assertFalse(result.accepted());
      assertEquals(code, result.problems().getFirst().code());
    });
  }

  @Test
  void oldRolesUnitsSubjectsAndCoercionsAreMalformed() {
    var input = input(List.of("one\ntwo"));
    var index = CitationIndex.build(input);
    String valid = reply(FindingsRegressionTests.ACTION);
    for (String bad :
        List.of(
            valid.replace("\"firstLine\":1", "\"unit\":\"U1\",\"firstLine\":1"),
            valid.replace("\"firstLine\":1", "\"firstLine\":1.5"),
            valid.replace("\"firstLine\":1", "\"firstLine\":\"1\""),
            valid.replace("\"firstLine\":1", "\"firstLine\":null"),
            valid.replace("\"actedBy\":\"CITY_COUNCIL\"", "\"actedBy\":null"),
            valid.replace("\"kind\":\"ACTION\"", "\"kind\":\"ACTION\",\"role\":\"OCCURRENCE\""),
            valid.replace("\"does\":", "\"status\":\"RECORDED\",\"does\":"),
            valid.replace("\"matterType\":\"PROPERTY\"", "\"matterType\":\"TOPIC\""),
            valid.replace("\"id\":\"i1\"", "\"id\":\"i0\""),
            reply(FindingsRegressionTests.PROPOSAL.replace("NOT_STATED", "CITY_COUNCIL"))))
      assertInstanceOf(
          Interpretation.Malformed.class, ModelContract.interpret(bad, index, input), bad);
    String planned =
        "{\"kind\":\"PROPOSAL\",\"proposedBy\":\"STAFF\",\"planned\":[{\"role\":\"PLANNED\",\"precision\":\"YEAR\",\"value\":\"2027\",\"approximate\":false}]}";
    assertInstanceOf(
        Interpretation.Malformed.class, ModelContract.interpret(reply(planned), index, input));
  }

  @Test
  void invalidRangeSavesNoPartialCandidatesAndReleasesAdmission() throws Exception {
    var input = input(List.of("one\ntwo"));
    String invalid =
        reply(FindingsRegressionTests.ACTION)
            .replace("\"firstLine\":1", "\"firstLine\":99")
            .replace("\"lastLine\":2", "\"lastLine\":99");
    var model =
        new ModelClient() {
          public Prepared preflight(List<Map<String, String>> messages, int outputLimit) {
            return new Prepared("p", List.of(1), 100, outputLimit, Map.of());
          }

          public Attempt generate(Prepared prepared, JsonNode schema, int seed, Sampling sampling) {
            return new Attempt(State.SUCCESS, invalid, "eos", Map.of());
          }
        };
    var output = temporary.resolve("invalid");
    var gate = temporary.resolve("gate");
    assertEquals(
        ModelClient.State.INVALID_OUTPUT,
        new EvaluationRunner(model)
            .evaluate(input, 10, 1, ModelClient.Sampling.baseline(), output, gate));
    assertFalse(Files.exists(gate));
    assertFalse(Files.exists(output.resolve("candidates.json")));
    assertTrue(Files.readString(output.resolve("validation.json")).contains("LINE_OUT_OF_RANGE"));
    assertTrue(Files.exists(output.resolve("resolution.json")));
  }

  @Test
  void unusedIssueDoesNotHideBadPriorReferencesOrDuplicateIds() {
    var input = input(List.of("one\ntwo"));
    var raw =
        FindingsRegressionTests.reply(
                FindingsRegressionTests.ACTION,
                "[{\"id\":\"i1\",\"matterType\":\"PROPERTY\",\"name\":\"Parcel\",\"priorIssueId\":\"unknown\"}]",
                "[]")
            .replace("\"firstLine\":4", "\"firstLine\":1")
            .replace("\"lastLine\":5", "\"lastLine\":2");
    var result = interpreted(raw, input);
    assertFalse(result.accepted());
    assertTrue(
        result.problems().stream().anyMatch(p -> p.code() == Problem.Code.UNKNOWN_PRIOR_ISSUE));
    assertTrue(result.candidates().issues().isEmpty());
  }

  @Test
  void whitespaceVerbPhraseCannotPassThroughTheTemplatePrefix() {
    var input = input(List.of("one\ntwo"));
    var result =
        interpreted(
            reply(FindingsRegressionTests.ACTION)
                .replace("receive the report and provide direction", " \u00a0 "),
            input);
    assertFalse(result.accepted());
    assertEquals(
        new Problem(Problem.Code.EMPTY_TEXT, "developments[0].does"), result.problems().getFirst());
  }
}
