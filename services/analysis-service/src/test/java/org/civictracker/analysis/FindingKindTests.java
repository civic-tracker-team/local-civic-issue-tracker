package org.civictracker.analysis;

import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayList;
import java.util.List;
import org.civictracker.analysis.application.DevelopmentKinds;
import org.civictracker.analysis.application.FindingsStore;
import org.civictracker.analysis.application.PriorContext;
import org.civictracker.analysis.application.SaveRejected;
import org.civictracker.analysis.domain.Candidates;
import org.civictracker.analysis.domain.CitationIndex;
import org.civictracker.analysis.domain.FindingKind;
import org.civictracker.analysis.domain.MeetingInput;
import org.civictracker.analysis.evaluation.EvaluationJson;
import org.civictracker.analysis.model.Interpretation;
import org.civictracker.analysis.model.ModelContract;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

@SpringBootTest
class FindingKindTests {
  @Autowired FindingsStore store;
  @Autowired JdbcTemplate jdbc;

  record Fixture(MeetingInput input, Interpretation.Interpreted interpreted) {}

  Fixture fixture() throws Exception {
    var original =
        EvaluationJson.MAPPER.readValue(
            OperationFixture.resource("input.json"), MeetingInput.class);
    var source = original.sources().getFirst();
    var segment = source.units().getFirst().segments().getFirst();
    String text =
        "Staff proposes the Elm bridge repair.\n"
            + "The Council approved the Elm bridge repair today.\n"
            + "Staff repaired the Elm bridge on December 1, 2025.\n"
            + "The next step for the Elm bridge repair is unclear.";
    var input =
        new MeetingInput(
            original.meetingId(),
            original.officialId(),
            original.meetingDate(),
            original.title(),
            List.of(
                new MeetingInput.Source(
                    source.documentId(),
                    source.versionId(),
                    source.artifactId(),
                    source.sha256(),
                    source.byteLength(),
                    source.preparationId(),
                    source.profileFingerprint(),
                    source.sourceUrls(),
                    List.of(
                        new MeetingInput.Unit(
                            0,
                            "NATIVE",
                            text,
                            List.of(
                                new MeetingInput.Segment(
                                    segment.id(), text, "NATIVE", segment.locator(), null, true)),
                            List.of())))),
            List.of());
    var kinds =
        List.of(
            "{\"kind\":\"PROPOSAL\",\"proposedBy\":\"STAFF\",\"planned\":[]}",
            "{\"kind\":\"ACTION\",\"actedBy\":\"CITY_COUNCIL\",\"planned\":[]}",
            "{\"kind\":\"EARLIER_ACTION\",\"actedBy\":\"STAFF\",\"occurred\":{\"precision\":\"DAY\",\"value\":\"2025-12-01\",\"approximate\":false},\"planned\":[]}",
            "{\"kind\":\"UNCLEAR\",\"planned\":[]}");
    var developments = new ArrayList<String>();
    for (int n = 0; n < kinds.size(); n++) {
      String evidence = "[{\"firstLine\":" + (n + 1) + ",\"lastLine\":" + (n + 1) + "}]";
      developments.add(
          "{\"finding\":"
              + kinds.get(n)
              + ",\"does\":\"repair the Elm bridge\",\"evidence\":"
              + evidence
              + ",\"links\":[{\"issueId\":\"i1\",\"basis\":\"SOURCE_STATED\",\"evidence\":"
              + evidence
              + "}]}");
    }
    String raw =
        "{\"issues\":[{\"id\":\"i1\",\"matterType\":\"PROJECT\",\"name\":\"Elm bridge"
            + " repair\",\"priorIssueId\":null}],\"developments\":["
            + String.join(",", developments)
            + "]}";
    var result =
        assertInstanceOf(
            Interpretation.Interpreted.class,
            ModelContract.interpret(raw, CitationIndex.build(input), input));
    assertTrue(result.accepted(), result.problems().toString());
    return new Fixture(input, result);
  }

  @Test
  void retainsAllFourKindsFromTheAcceptedReplyInReadsAndHistory() throws Exception {
    var f = fixture();
    var kinds = DevelopmentKinds.from(f.interpreted());
    var id =
        store.save(
            f.input(),
            f.interpreted().candidates(),
            PriorContext.Snapshot.empty(),
            new FindingsStoreTests.Fixture().metadata,
            kinds);
    var saved = store.read(id);
    assertEquals(
        List.of(
            FindingKind.PROPOSAL,
            FindingKind.ACTION,
            FindingKind.EARLIER_ACTION,
            FindingKind.UNCLEAR),
        saved.developments().stream().map(d -> d.kind()).toList());
    assertEquals(
        saved.developments(),
        store
            .issueHistory(saved.issues().getFirst().issueId())
            .revisions()
            .getFirst()
            .developments());
    assertEquals(
        saved,
        EvaluationJson.MAPPER.readValue(
            EvaluationJson.MAPPER.writeValueAsString(saved),
            org.civictracker.analysis.domain.SavedFindings.class));
  }

  @Test
  void missingReorderedOrChangedMappingsSaveNothing() throws Exception {
    var f = fixture();
    var kinds = DevelopmentKinds.from(f.interpreted());
    var reversed = new ArrayList<>(kinds.entries());
    java.util.Collections.reverse(reversed);
    var changed = new ArrayList<>(kinds.entries());
    var first = changed.getFirst().development();
    changed.set(
        0,
        new DevelopmentKinds.Entry(
            new Candidates.Development(
                first.id(), "changed", first.status(), first.dates(), first.evidenceIds()),
            FindingKind.PROPOSAL));
    var before = counts();
    for (var bad :
        List.of(
            new DevelopmentKinds(List.of()),
            new DevelopmentKinds(reversed),
            new DevelopmentKinds(changed))) {
      var error =
          assertThrows(
              SaveRejected.class,
              () ->
                  store.save(
                      f.input(),
                      f.interpreted().candidates(),
                      PriorContext.Snapshot.empty(),
                      new FindingsStoreTests.Fixture().metadata,
                      bad));
      assertEquals(SaveRejected.Code.FINDING_KIND_MAPPING_MISMATCH, error.code());
      assertEquals(before, counts());
    }
    var shortReply = f.interpreted().reply().deepCopy();
    ((tools.jackson.databind.node.ArrayNode) shortReply.path("developments")).remove(0);
    assertThrows(
        SaveRejected.class,
        () ->
            DevelopmentKinds.from(
                new Interpretation.Interpreted(
                    shortReply, List.of(), f.interpreted().candidates(), List.of())));
  }

  @Test
  void databaseRequiresAKnownFindingKind() throws Exception {
    var f = fixture();
    var id =
        store.save(
            f.input(),
            f.interpreted().candidates(),
            PriorContext.Snapshot.empty(),
            new FindingsStoreTests.Fixture().metadata,
            DevelopmentKinds.from(f.interpreted()));
    var before = counts();
    for (String kind : new String[] {null, "UNSUPPORTED"}) {
      assertThrows(
          org.springframework.dao.DataIntegrityViolationException.class,
          () ->
              jdbc.update(
                  "insert into"
                      + " analysis.development(id,revision_id,local_label,ordinal,description,status,kind)"
                      + " values (?,?,'invalid-kind',99,'synthetic','RECORDED',?)",
                  java.util.UUID.randomUUID(),
                  id,
                  kind));
      assertEquals(before, counts());
    }
  }

  private List<Long> counts() {
    return AnalysisFoundationTests.FINDINGS.stream()
        .map(t -> jdbc.queryForObject("select count(*) from analysis." + t, Long.class))
        .toList();
  }
}
