package org.civictracker.analysis.model;

import static org.junit.jupiter.api.Assertions.*;

import java.lang.reflect.Modifier;
import java.time.LocalDate;
import java.util.List;
import org.civictracker.analysis.domain.Candidates;
import org.civictracker.analysis.evaluation.EvaluationJson;
import org.junit.jupiter.api.Test;

class WireTests {
  private Wire.Reply parse(String finding) {
    return EvaluationJson.MAPPER.readValue(
        """
        {"issues":[],"developments":[{"evidence":[{"firstLine":1,"lastLine":1}],
        "finding":%s,"does":"study the parcel","links":[]}]}
        """
            .formatted(finding),
        Wire.Reply.class);
  }

  @Test
  void packagePrivateRecordsParseBothNestedUnions() {
    assertFalse(Modifier.isPublic(Wire.class.getModifiers()));
    assertFalse(Modifier.isPublic(Wire.Reply.class.getModifiers()));
    for (String date :
        List.of(
            "{\"precision\":\"DAY\",\"value\":\"2025-03-03\",\"approximate\":false}",
            "{\"precision\":\"MONTH\",\"value\":\"2025-03\",\"approximate\":true}",
            "{\"precision\":\"YEAR\",\"value\":\"2025\",\"approximate\":false}",
            "{\"precision\":\"RANGE\",\"start\":\"2025-03-03\",\"end\":\"2025-04-03\",\"approximate\":true}",
            "{\"precision\":\"UNKNOWN\",\"approximate\":true}")) {
      var reply =
          parse(
              "{\"kind\":\"EARLIER_ACTION\",\"actedBy\":\"CITY_COUNCIL\",\"occurred\":"
                  + date
                  + ",\"planned\":["
                  + date
                  + "]}");
      var finding =
          assertInstanceOf(Wire.EarlierAction.class, reply.developments().getFirst().finding());
      assertEquals(Candidates.Status.RECORDED, finding.status());
      assertEquals(
          finding.occurred().claim(Candidates.Role.OCCURRENCE),
          finding.dates(LocalDate.of(2026, 4, 3)).getFirst());
      assertEquals(
          Candidates.Role.PLANNED, finding.dates(LocalDate.of(2026, 4, 3)).getLast().role());
    }
  }

  @Test
  void allKindsUseTheDeclaredHostPolicyAndTemplates() {
    var day = LocalDate.of(2026, 4, 3);
    var proposal = new Wire.Proposal(Wire.Proposer.STAFF, List.of());
    var action = new Wire.Action(Wire.Actor.CITY_COUNCIL, List.of());
    var earlier =
        new Wire.EarlierAction(Wire.Actor.CITY_COUNCIL, new Wire.UnknownDate(true), List.of());
    var unclear = new Wire.Unclear(List.of());
    assertEquals("Proposed by city staff for decision: study", Wire.render(proposal, "study"));
    assertEquals(
        "Proposed for decision: study",
        Wire.render(new Wire.Proposal(Wire.Proposer.NOT_STATED, List.of()), "study"));
    assertEquals("Recorded action by the City Council: study", Wire.render(action, "study"));
    assertEquals(
        "Earlier action by the City Council reported: study", Wire.render(earlier, "study"));
    assertEquals("Outcome not established: study", Wire.render(unclear, "study"));
    assertEquals(
        List.of(Candidates.Role.MEETING),
        proposal.dates(day).stream().map(Candidates.DateClaim::role).toList());
    assertEquals(
        List.of(Candidates.Role.OCCURRENCE, Candidates.Role.MEETING),
        action.dates(day).stream().map(Candidates.DateClaim::role).toList());
    assertNull(earlier.dates(day).getFirst().start());
    assertEquals(Candidates.Status.UNCERTAIN, unclear.status());
    assertInstanceOf(
        Wire.Proposal.class,
        parse("{\"kind\":\"PROPOSAL\",\"proposedBy\":\"STAFF\",\"planned\":[]}")
            .developments()
            .getFirst()
            .finding());
    assertInstanceOf(
        Wire.Action.class,
        parse("{\"kind\":\"ACTION\",\"actedBy\":\"CITY_COUNCIL\",\"planned\":[]}")
            .developments()
            .getFirst()
            .finding());
    assertInstanceOf(
        Wire.Unclear.class,
        parse("{\"kind\":\"UNCLEAR\",\"planned\":[]}").developments().getFirst().finding());
  }

  @Test
  void proposerCannotBeCouncilAndTheParserKeepsItsStrictSettings() {
    for (String bad :
        List.of(
            "{\"kind\":\"PROPOSAL\",\"proposedBy\":\"CITY_COUNCIL\",\"planned\":[]}",
            "{\"kind\":\"PROPOSAL\",\"proposedBy\":\"STAFF\",\"planned\":[],\"role\":\"OCCURRENCE\"}",
            "{\"kind\":\"ACTION\",\"actedBy\":null,\"planned\":[]}",
            "{\"kind\":\"ACTION\",\"actedBy\":\"STAFF\"}",
            "{\"kind\":\"EARLIER_ACTION\",\"actedBy\":\"STAFF\",\"occurred\":{\"precision\":\"DAY\",\"value\":\"2025-03-03\",\"approximate\":false,\"role\":\"OCCURRENCE\"},\"planned\":[]}"))
      assertThrows(RuntimeException.class, () -> parse(bad));
    assertFalse(
        List.of(Wire.Proposer.values()).stream().anyMatch(p -> p.name().equals("CITY_COUNCIL")));
  }
}
