package org.civictracker.analysis;

import static org.junit.jupiter.api.Assertions.*;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.civictracker.analysis.domain.*;
import org.civictracker.analysis.model.Interpretation;
import org.civictracker.analysis.model.ModelContract;
import org.junit.jupiter.api.Test;

class SemanticRegressionTests {
  private MeetingInput input() {
    var segment =
        new MeetingInput.Segment(
            UUID.randomUUID(), "Staff recommended a study.", "OCR", Map.of(), null, true);
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
            List.of(new MeetingInput.Unit(0, "OCR", "", List.of(segment), List.of())));
    return new MeetingInput(
        UUID.randomUUID(),
        "synthetic",
        LocalDate.of(2026, 1, 15),
        "Council",
        List.of(source),
        List.of());
  }

  private Candidates values(MeetingInput input, Candidates.Role role, String date) {
    var span =
        assertInstanceOf(CitationIndex.Found.class, CitationIndex.build(input).resolveLines(1, 1))
            .spans()
            .getFirst();
    return new Candidates(
        List.of(new Candidates.Evidence("e1", span)),
        List.of(
            new Candidates.Development(
                "d1",
                "A study is proposed",
                Candidates.Status.PROPOSED,
                List.of(
                    new Candidates.DateClaim(role, Candidates.Precision.DAY, date, null, false)),
                List.of("e1"))),
        List.of(),
        List.of());
  }

  @Test
  void proposalCannotClaimAnOccurrence() {
    var input = input();
    assertFalse(
        new CandidateValidator()
            .validate(values(input, Candidates.Role.OCCURRENCE, "2026-01-15"), input)
            .valid());
  }

  @Test
  void meetingDateMustEqualTheFrozenInput() {
    var input = input();
    assertFalse(
        new CandidateValidator()
            .validate(values(input, Candidates.Role.MEETING, "2026-01-16"), input)
            .valid());
  }

  @Test
  void collectsAllProblemsNotFirst() {
    String raw =
        """
        {"developments":[{"finding":{"kind":"PROPOSAL","proposedBy":"NOT_STATED","planned":[]},"does":"study",
        "evidence":[{"firstLine":99,"lastLine":99},{"firstLine":2,"lastLine":1}],
        "links":[]}],"issues":[]}
        """;
    var input = input();
    var outcome =
        assertDoesNotThrow(() -> ModelContract.interpret(raw, CitationIndex.build(input), input));
    var unresolved = assertInstanceOf(Interpretation.Unresolved.class, outcome);
    assertTrue(
        unresolved
            .problems()
            .contains(new Problem(Problem.Code.LINE_OUT_OF_RANGE, "developments[0].evidence[0]")));
    assertTrue(
        unresolved
            .problems()
            .contains(new Problem(Problem.Code.REVERSED_RANGE, "developments[0].evidence[1]")));
    assertEquals(2, unresolved.traces().size());
  }

  @Test
  void malformedJsonIsMalformedReply() {
    var input = input();
    var outcome =
        assertDoesNotThrow(
            () -> ModelContract.interpret("not JSON", CitationIndex.build(input), input));
    assertEquals(
        List.of(new Problem(Problem.Code.MALFORMED_REPLY, "$")),
        assertInstanceOf(Interpretation.Malformed.class, outcome).problems());
  }

  @Test
  void oneInvalidCitationReportsOnlyItsRootCause() {
    var input = input();
    String raw =
        """
        {"developments":[{"finding":{"kind":"PROPOSAL","proposedBy":"NOT_STATED","planned":[]},"does":"study",
        "evidence":[{"firstLine":99,"lastLine":99}],"links":[]}],"issues":[]}
        """;
    var outcome =
        assertInstanceOf(
            Interpretation.Unresolved.class,
            ModelContract.interpret(raw, CitationIndex.build(input), input));
    assertEquals(
        List.of(new Problem(Problem.Code.LINE_OUT_OF_RANGE, "developments[0].evidence[0]")),
        outcome.problems());
  }
}
