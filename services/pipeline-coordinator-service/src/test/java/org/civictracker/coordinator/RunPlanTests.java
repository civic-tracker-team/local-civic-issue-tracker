package org.civictracker.coordinator;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.civictracker.coordinator.owners.AnalysisClient;
import org.civictracker.coordinator.owners.DiscoveredMeeting;
import org.civictracker.coordinator.owners.Original;
import org.civictracker.coordinator.owners.Prepared;
import org.civictracker.coordinator.publication.Disposition;
import org.civictracker.coordinator.publication.PublishedMeeting;
import org.civictracker.coordinator.publication.SourceVersion;
import org.civictracker.coordinator.run.Attempts;
import org.civictracker.coordinator.run.DateRange;
import org.civictracker.coordinator.run.MeetingFacts;
import org.civictracker.coordinator.run.NoInputReason;
import org.civictracker.coordinator.run.Pin;
import org.civictracker.coordinator.run.PlanRejected;
import org.civictracker.coordinator.run.PlannedMeeting;
import org.civictracker.coordinator.run.Progress;
import org.civictracker.coordinator.run.PublicationBase;
import org.civictracker.coordinator.run.RetryHint;
import org.civictracker.coordinator.run.RunFacts;
import org.civictracker.coordinator.run.RunPlan;
import org.civictracker.coordinator.run.StartRun;
import org.civictracker.coordinator.run.Step;
import org.civictracker.coordinator.run.StepKind;
import org.junit.jupiter.api.Test;

class RunPlanTests {
  final UUID runId = id(1);
  final StartRun command = new StartRun(runId, "synthetic", date(1), date(31), List.of("council"));

  @Test
  void orderIsMeetingDateThenOfficialIdWithStableOrdinals() throws Exception {
    var a = found(10, "B", date(3), null);
    var b = found(11, "A", date(3), null);
    var c = found(12, "Z", date(1), "Cancelled");
    var planned = RunPlan.plan(command, 3, List.of(a, b, c));
    assertEquals(List.of("Z", "A", "B"), planned.stream().map(PlannedMeeting::officialId).toList());
    assertEquals(List.of(0, 1, 2), planned.stream().map(PlannedMeeting::ordinal).toList());
    assertEquals(planned, RunPlan.plan(command, 3, List.of(c, a, b)));
    assertTrue(planned.getFirst().cancellationEvidence());
    assertFalse(planned.getLast().cancellationEvidence());
    assertThrows(UnsupportedOperationException.class, () -> planned.clear());
  }

  @Test
  void discoveryRejectsUndatedOutOfRangeDuplicateAndExcessMeetingsWithoutTruncating() {
    rejected("UNDATED_MEETING", List.of(found(10, "A", null, null)), 2);
    rejected("MEETING_OUT_OF_RANGE", List.of(found(10, "A", date(1).minusDays(1), null)), 2);
    rejected("MEETING_OUT_OF_RANGE", List.of(found(10, "A", date(31).plusDays(1), null)), 2);
    var a = found(10, "A", date(1), null);
    rejected("MEETING_DUPLICATE", List.of(a, a), 2);
    rejected("RANGE_EXCEEDS_LIMIT", List.of(a, found(11, "B", date(2), null)), 1);
  }

  @Test
  void exactLimitAndInclusiveRangeBoundariesAreAccepted() throws Exception {
    var result =
        RunPlan.plan(
            command, 2, List.of(found(10, "A", date(1), null), found(11, "B", date(31), null)));
    assertEquals(2, result.size());
    assertTrue(RunPlan.plan(command, 1, List.of()).isEmpty());
  }

  @Test
  void overlappingCoverageIsRejectedIncludingAnEqualEndpoint() throws Exception {
    for (DateRange range :
        List.of(
            new DateRange(date(1), date(31)),
            new DateRange(date(1).minusDays(1), date(1)),
            new DateRange(date(31), date(31).plusDays(1)),
            new DateRange(date(10), date(20)))) {
      var base = new PublicationBase(id(90), List.of(), List.of(range));
      assertEquals(
          "RANGE_ALREADY_PUBLISHED",
          assertThrows(PlanRejected.class, () -> RunPlan.rejectOverlap(command, base)).code);
    }
    RunPlan.rejectOverlap(
        command,
        new PublicationBase(
            id(90),
            List.of(),
            List.of(
                new DateRange(date(1).minusDays(2), date(1).minusDays(1)),
                new DateRange(date(31).plusDays(1), date(31).plusDays(2)))));
    RunPlan.rejectOverlap(command, PublicationBase.NONE);
  }

  @Test
  void ownerOperationIdsAreReproducibleAndSeparateKindsMeetingsAndAttempts() {
    var operation = RunPlan.operationId(runId, StepKind.ACQUIRE, id(10), 1);
    assertEquals(operation, RunPlan.operationId(runId, StepKind.ACQUIRE, id(10), 1));
    assertEquals(
        UUID.nameUUIDFromBytes(
            ("civic-tracker/t11/" + runId + "/ACQUIRE/" + id(10) + "/1")
                .getBytes(StandardCharsets.UTF_8)),
        operation);
    var variations =
        Set.of(
            operation,
            RunPlan.operationId(runId, StepKind.ACQUIRE, id(10), 2),
            RunPlan.operationId(runId, StepKind.PREPARE, id(10), 1),
            RunPlan.operationId(runId, StepKind.ACQUIRE, id(11), 1),
            RunPlan.operationId(id(2), StepKind.ACQUIRE, id(10), 1),
            RunPlan.operationId(runId, StepKind.DISCOVER, null, 1));
    assertEquals(6, variations.size());
    assertEquals(RunPlan.publicationId(runId), RunPlan.publicationId(runId));
    assertFalse(variations.contains(RunPlan.publicationId(runId)));
  }

  @Test
  void unknownMeetingsMeanDiscoveryAndKnownEmptyMeetingsMeanReadyCandidate() {
    var unknown =
        new RunFacts(runId, command, 2, "profile", PublicationBase.NONE, 3, Optional.empty());
    assertEquals(new Step.Discover(3), RunPlan.next(unknown));
    var terminal = assertInstanceOf(Step.Publish.class, RunPlan.next(facts(List.of())));
    assertTrue(terminal.manifest().meetings().isEmpty());
    assertEquals(List.of(new DateRange(date(1), date(31))), terminal.manifest().coverage());
  }

  @Test
  void everyMeetingMustFinishRecordsStepsBeforeAnyAnalysis() {
    var a = row(0, 1, new Progress.Prepared(List.of(pin(1))));
    var b = row(1, 2, new Progress.Discovered());
    var acquire = assertInstanceOf(Step.Acquire.class, RunPlan.next(facts(List.of(b, a))));
    assertEquals(b.meeting(), acquire.meeting());
    var acquired = new MeetingFacts(b.meeting(), new Progress.Acquired(), new Attempts(2, 3, 1));
    var prepare = assertInstanceOf(Step.Prepare.class, RunPlan.next(facts(List.of(acquired, a))));
    assertEquals(3, prepare.attempt());
    assertEquals(2, prepare.acquiredAttempt());
    var prepared =
        new MeetingFacts(b.meeting(), new Progress.Prepared(List.of(pin(2))), Attempts.FIRST);
    var analyze = assertInstanceOf(Step.Analyze.class, RunPlan.next(facts(List.of(prepared, a))));
    assertEquals(a.meeting(), analyze.meeting());
  }

  @Test
  void analysisFinishesMeetingsInOrdinalOrderAndUsesExactOwnerIdsAndPins() {
    var a = row(0, 1, new Progress.Analyzed(List.of(pin(1)), id(91)));
    var b =
        new MeetingFacts(
            row(1, 2, new Progress.Prepared(List.of(pin(2)))).meeting(),
            new Progress.Prepared(List.of(pin(2))),
            new Attempts(2, 3, 4));
    var step = assertInstanceOf(Step.Analyze.class, RunPlan.next(facts(List.of(b, a))));
    assertEquals(b.meeting(), step.meeting());
    assertEquals(4, step.attempt());
    assertEquals(List.of(id(91)), step.priorRevisionIds());
    assertEquals(
        RunPlan.operationId(runId, StepKind.DISCOVER, null, 1), step.selection().discoveryId());
    assertEquals(
        RunPlan.operationId(runId, StepKind.ACQUIRE, b.meeting().meetingId(), 2),
        step.selection().acquisitionId());
    assertEquals(
        RunPlan.operationId(runId, StepKind.PREPARE, b.meeting().meetingId(), 3),
        step.selection().preparationOperationId());
    assertEquals(List.of(pin(2)), step.selection().inputs());
    assertEquals(b.meeting().date(), step.selection().meetingDate());
    assertEquals(b.meeting().officialId(), step.selection().officialId());
  }

  @Test
  void cancelledAndEmptyMeetingsNeedNeitherPreparationNorAnalysis() {
    var cancelled = new PlannedMeeting(0, id(10), "A", date(1), "Synthetic", true);
    var empty = new PlannedMeeting(1, id(11), "B", date(2), "Synthetic", false);
    var c = RunPlan.afterAcquisition(cancelled, List.of());
    var e = RunPlan.afterAcquisition(empty, List.of());
    assertEquals(new Progress.NoInputs(NoInputReason.CANCELLED), c);
    assertEquals(new Progress.NoInputs(NoInputReason.NO_RETAINED_ORIGINALS), e);
    assertInstanceOf(
        Progress.Acquired.class, RunPlan.afterAcquisition(cancelled, List.of(original(1))));
    var ready =
        RunPlan.next(
            facts(
                List.of(
                    new MeetingFacts(cancelled, c, Attempts.FIRST),
                    new MeetingFacts(empty, e, Attempts.FIRST))));
    assertInstanceOf(Step.Publish.class, ready);
  }

  @Test
  void pinJoinKeepsRecordsDocumentOrderWhenItemIdsAreNotAscending() throws Exception {
    var originals = List.of(original(4), original(1), original(3));
    var preparations = List.of(prepared(1), prepared(3), prepared(4));
    assertEquals(List.of(pin(4), pin(1), pin(3)), RunPlan.pins(originals, preparations));
  }

  @Test
  void pinJoinIsABijectionPreservingOriginalBytesAndPreparedTextReferences() throws Exception {
    var originals = List.of(original(2), original(1));
    var prepared = List.of(prepared(1), prepared(2));
    var result = RunPlan.pins(originals, prepared);
    assertEquals(List.of(pin(2), pin(1)), result);
    assertThrows(UnsupportedOperationException.class, () -> result.clear());
  }

  @Test
  void pinJoinRejectsEveryMissingDuplicateAndConflictingMembershipCase() {
    pinRejected(List.of(), List.of());
    pinRejected(List.of(original(1)), List.of());
    pinRejected(List.of(original(1)), List.of(prepared(2)));
    pinRejected(List.of(original(1), original(2)), List.of(prepared(1), prepared(1)));
    pinRejected(List.of(original(1), original(1)), List.of(prepared(1), prepared(2)));
    var p = prepared(1);
    pinRejected(
        List.of(original(1)),
        List.of(
            new Prepared(
                p.acquisitionItemId(),
                id(999),
                p.versionId(),
                p.preparationId(),
                p.profileFingerprint())));
    pinRejected(
        List.of(original(1)),
        List.of(
            new Prepared(
                p.acquisitionItemId(),
                p.documentId(),
                id(999),
                p.preparationId(),
                p.profileFingerprint())));
    pinRejected(List.of(original(1)), List.of(prepared(1), prepared(2)));
  }

  @Test
  void priorsIncludeBaseAndRunRevisionsStrictlyBeforeTheMeetingSortedByDateAndId() {
    var earlier = published(80, date(1).minusDays(1), Disposition.ANALYZED, id(98));
    var ignored = published(81, date(1).minusDays(1), Disposition.CANCELLED, null);
    var base =
        new PublicationBase(
            id(90),
            List.of(earlier, ignored, published(82, date(4), Disposition.ANALYZED, id(99))),
            List.of());
    var a = row(0, 1, new Progress.Analyzed(List.of(pin(1)), id(94)));
    var b =
        new MeetingFacts(
            new PlannedMeeting(1, id(11), "B", date(1), "Synthetic", false),
            new Progress.Analyzed(List.of(pin(2)), id(93)),
            Attempts.FIRST);
    var future = row(2, 5, new Progress.Analyzed(List.of(pin(3)), id(92)));
    var facts = facts(base, List.of(future, a, b));
    assertEquals(
        List.of(id(98), id(93), id(94)),
        RunPlan.priorRevisionIds(facts, row(3, 2, new Progress.Discovered()).meeting()));
    assertFalse(RunPlan.priorRevisionIds(facts, a.meeting()).contains(id(93)));
  }

  @Test
  void sameDayMeetingsGetIdenticalPriorsEvenAfterTheirPeerFinishes() {
    var earlier = row(0, 1, new Progress.Analyzed(List.of(pin(1)), id(91)));
    var a = row(1, 2, new Progress.Prepared(List.of(pin(2))));
    var b =
        new MeetingFacts(
            new PlannedMeeting(2, id(12), "C", date(2), "Synthetic", false),
            new Progress.Prepared(List.of(pin(3))),
            Attempts.FIRST);
    var before = facts(List.of(earlier, a, b));
    var first = RunPlan.priorRevisionIds(before, a.meeting());
    var after =
        facts(
            List.of(
                earlier,
                new MeetingFacts(
                    a.meeting(), new Progress.Analyzed(List.of(pin(2)), id(92)), Attempts.FIRST),
                b));
    assertEquals(List.of(id(91)), first);
    assertEquals(first, RunPlan.priorRevisionIds(after, b.meeting()));
  }

  @Test
  void frozenRequestSurvivesChangedFactsWithoutRebuildingItsSelectionPriorsOrBytes() {
    var a = row(0, 2, new Progress.Prepared(List.of(pin(1))));
    var step = assertInstanceOf(Step.Analyze.class, RunPlan.next(facts(List.of(a))));
    var operation = RunPlan.operationId(runId, StepKind.ANALYZE, a.meeting().meetingId(), 1);
    var frozen = AnalysisClient.freeze(operation, step.selection(), step.priorRevisionIds());
    var restored = AnalysisClient.readFrozen(frozen.json(), frozen.sha256());
    assertEquals(frozen, restored);
    var base =
        new PublicationBase(
            id(90), List.of(published(80, date(1), Disposition.ANALYZED, id(91))), List.of());
    var changed =
        new MeetingFacts(
            a.meeting(), new Progress.Prepared(List.of(pin(2))), Attempts.FIRST, restored);
    var next = assertInstanceOf(Step.Analyze.class, RunPlan.next(facts(base, List.of(changed))));
    assertEquals(frozen.selection(), next.selection());
    assertEquals(frozen.priorRevisionIds(), next.priorRevisionIds());
    assertEquals(frozen.json(), next.frozenRequest().json());
    assertEquals(frozen.sha256(), next.frozenRequest().sha256());
    assertEquals(List.of(pin(1)), next.selection().inputs());
    assertThrows(
        IllegalArgumentException.class,
        () -> AnalysisClient.readFrozen(frozen.json() + " ", frozen.sha256()));
  }

  @Test
  void retryNeedsANewFrozenRequestAndCannotReuseThePreviousAttemptsBytes() {
    var a = row(0, 2, new Progress.Prepared(List.of(pin(1))));
    var step = assertInstanceOf(Step.Analyze.class, RunPlan.next(facts(List.of(a))));
    var frozen =
        AnalysisClient.freeze(
            RunPlan.operationId(runId, StepKind.ANALYZE, a.meeting().meetingId(), 1),
            step.selection(),
            step.priorRevisionIds());
    var invalid = new MeetingFacts(a.meeting(), a.progress(), new Attempts(1, 1, 2), frozen);
    assertThrows(IllegalStateException.class, () -> RunPlan.next(facts(List.of(invalid))));
    var retry = new MeetingFacts(a.meeting(), a.progress(), new Attempts(1, 1, 2));
    assertEquals(
        2, assertInstanceOf(Step.Analyze.class, RunPlan.next(facts(List.of(retry)))).attempt());
  }

  @Test
  void manifestCarriesForwardEveryMeetingAndRangeWithExactDispositionsAndSources() {
    var carried = published(80, date(1).minusDays(1), Disposition.ANALYZED, id(90));
    var previousRange = new DateRange(date(1).minusDays(5), date(1).minusDays(1));
    var base = new PublicationBase(id(89), List.of(carried), List.of(previousRange));
    var analyzed = row(0, 1, new Progress.Analyzed(List.of(pin(1), pin(1)), id(91)));
    var cancelled = row(1, 2, new Progress.NoInputs(NoInputReason.CANCELLED));
    var empty = row(2, 3, new Progress.NoInputs(NoInputReason.NO_RETAINED_ORIGINALS));
    var skipped =
        row(
            3,
            4,
            new Progress.Skipped("Synthetic reason", "maintainer", Instant.EPOCH, List.of(pin(2))));
    var manifest = RunPlan.manifest(facts(base, List.of(skipped, empty, cancelled, analyzed)));
    assertEquals(RunPlan.publicationId(runId), manifest.publicationId());
    assertEquals(runId, manifest.runId());
    assertEquals(base.publicationId(), manifest.previousPublicationId());
    assertEquals(List.of(previousRange, new DateRange(date(1), date(31))), manifest.coverage());
    assertEquals(5, manifest.meetings().size());
    assertEquals(
        5, manifest.meetings().stream().map(PublishedMeeting::meetingId).distinct().count());
    assertEquals(carried, manifest.meetings().getFirst());
    assertEquals(
        List.of(
            Disposition.ANALYZED,
            Disposition.ANALYZED,
            Disposition.CANCELLED,
            Disposition.NO_RETAINED_ORIGINALS,
            Disposition.SKIPPED),
        manifest.meetings().stream().map(PublishedMeeting::disposition).toList());
    assertEquals(
        List.of(new SourceVersion(pin(1).documentId(), pin(1).versionId())),
        manifest.meetings().get(1).sources());
    assertEquals(id(91), manifest.meetings().get(1).revisionId());
    assertNull(manifest.meetings().get(2).revisionId());
    assertTrue(manifest.meetings().get(2).sources().isEmpty());
    assertEquals("Synthetic reason", manifest.meetings().getLast().skipReason());
    assertEquals(
        List.of(new SourceVersion(pin(2).documentId(), pin(2).versionId())),
        manifest.meetings().getLast().sources());
  }

  @Test
  void manifestCannotContainIncompleteOrDuplicateMeetings() {
    var m = row(0, 1, new Progress.NoInputs(NoInputReason.CANCELLED));
    var duplicate = published(10, date(1), Disposition.CANCELLED, null);
    var base = new PublicationBase(id(90), List.of(duplicate), List.of());
    assertThrows(IllegalStateException.class, () -> RunPlan.manifest(facts(base, List.of(m))));
    assertThrows(
        IllegalStateException.class,
        () -> RunPlan.manifest(facts(List.of(row(0, 1, new Progress.Acquired())))));
    assertThrows(
        IllegalStateException.class,
        () ->
            RunPlan.manifest(
                new RunFacts(
                    runId, command, 2, "profile", PublicationBase.NONE, 1, Optional.empty())));
  }

  @Test
  void hintsDescribeFailureWithoutChoosingRetryOrSkip() {
    assertEquals(RetryHint.LIKELY_TRANSIENT, RunPlan.hint(StepKind.ANALYZE, "RUNTIME_FAILED"));
    assertEquals(RetryHint.LIKELY_TRANSIENT, RunPlan.hint(StepKind.ACQUIRE, "OWNER_UNREACHABLE"));
    assertEquals(RetryHint.MAY_REPEAT, RunPlan.hint(StepKind.ANALYZE, "INVALID_OUTPUT"));
    for (String code :
        List.of(
            "UNDATED_MEETING",
            "PIN_JOIN_MISMATCH",
            "RANGE_EXCEEDS_LIMIT",
            "MEETING_OUT_OF_RANGE",
            "SUBMIT_REJECTED:INVALID_REQUEST",
            "CONFLICTING_LINK_BASIS")) {
      assertEquals(RetryHint.DETERMINISTIC, RunPlan.hint(StepKind.ANALYZE, code));
    }
    assertEquals(RetryHint.UNKNOWN, RunPlan.hint(StepKind.DISCOVER, "OTHER"));
  }

  void rejected(String code, List<DiscoveredMeeting> meetings, int max) {
    assertEquals(
        code, assertThrows(PlanRejected.class, () -> RunPlan.plan(command, max, meetings)).code);
  }

  void pinRejected(List<Original> originals, List<Prepared> prepared) {
    assertEquals(
        "PIN_JOIN_MISMATCH",
        assertThrows(PlanRejected.class, () -> RunPlan.pins(originals, prepared)).code);
  }

  RunFacts facts(List<MeetingFacts> meetings) {
    return facts(PublicationBase.NONE, meetings);
  }

  RunFacts facts(PublicationBase base, List<MeetingFacts> meetings) {
    return new RunFacts(runId, command, 10, "profile", base, 1, Optional.of(meetings));
  }

  MeetingFacts row(int ordinal, int day, Progress progress) {
    return new MeetingFacts(
        new PlannedMeeting(ordinal, id(10 + ordinal), "M" + ordinal, date(day), "Synthetic", false),
        progress,
        Attempts.FIRST);
  }

  static UUID id(long n) {
    return new UUID(0, n);
  }

  static LocalDate date(int day) {
    return LocalDate.of(2026, 1, day);
  }

  static DiscoveredMeeting found(int id, String official, LocalDate date, String cancelled) {
    return new DiscoveredMeeting(id(id), official, date, "Synthetic", cancelled);
  }

  static Pin pin(int n) {
    return new Pin(
        id(n),
        id(100 + n),
        id(200 + n),
        id(300 + n),
        "a".repeat(64),
        n,
        id(400 + n),
        "b".repeat(64));
  }

  static Original original(int n) {
    var p = pin(n);
    return new Original(
        p.acquisitionItemId(),
        p.documentId(),
        p.versionId(),
        p.artifactId(),
        p.sha256(),
        p.byteLength());
  }

  static Prepared prepared(int n) {
    var p = pin(n);
    return new Prepared(
        p.acquisitionItemId(),
        p.documentId(),
        p.versionId(),
        p.preparationId(),
        p.profileFingerprint());
  }

  static PublishedMeeting published(int n, LocalDate date, Disposition disposition, UUID revision) {
    return new PublishedMeeting(
        id(n),
        "M" + n,
        date,
        "Synthetic",
        disposition,
        revision,
        List.of(new SourceVersion(id(500 + n), id(600 + n))),
        null);
  }
}
