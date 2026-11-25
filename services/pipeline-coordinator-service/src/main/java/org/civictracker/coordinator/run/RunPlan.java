package org.civictracker.coordinator.run;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.civictracker.coordinator.owners.DiscoveredMeeting;
import org.civictracker.coordinator.owners.Original;
import org.civictracker.coordinator.owners.Prepared;
import org.civictracker.coordinator.publication.Disposition;
import org.civictracker.coordinator.publication.Manifest;
import org.civictracker.coordinator.publication.PublishedMeeting;
import org.civictracker.coordinator.publication.SourceVersion;

/** Chooses the next step from saved facts, without I/O or mutable state. */
public final class RunPlan {
  private RunPlan() {}

  public static UUID operationId(UUID runId, StepKind kind, UUID meetingIdOrNull, int attempt) {
    var name = "civic-tracker/t11/" + runId + "/" + kind + "/" + meetingIdOrNull + "/" + attempt;
    return UUID.nameUUIDFromBytes(name.getBytes(StandardCharsets.UTF_8));
  }

  public static UUID publicationId(UUID runId) {
    return UUID.nameUUIDFromBytes(
        ("civic-tracker/t11/" + runId + "/publication").getBytes(StandardCharsets.UTF_8));
  }

  public static Step next(RunFacts facts) {
    if (facts.meetings().isEmpty()) {
      return new Step.Discover(facts.discoveryAttempt());
    }
    var meetings = ordered(facts);
    for (var m : meetings) {
      if (m.progress() instanceof Progress.Discovered) {
        return new Step.Acquire(m.meeting(), m.attempts().acquire());
      }
      if (m.progress() instanceof Progress.Acquired) {
        return new Step.Prepare(m.meeting(), m.attempts().prepare(), m.attempts().acquire());
      }
    }
    for (var m : meetings) {
      if (m.progress() instanceof Progress.Prepared prepared) {
        UUID operation =
            operationId(
                facts.runId(), StepKind.ANALYZE, m.meeting().meetingId(), m.attempts().analyze());
        if (m.frozenAnalysis() != null) {
          var frozen = m.frozenAnalysis();
          if (!operation.equals(frozen.operationId())) {
            throw new IllegalStateException("Frozen analysis belongs to a different attempt");
          }
          return new Step.Analyze(
              m.meeting(),
              m.attempts().analyze(),
              frozen.selection(),
              frozen.priorRevisionIds(),
              frozen);
        }
        var selection =
            new AnalysisSelection(
                operationId(facts.runId(), StepKind.DISCOVER, null, facts.discoveryAttempt()),
                operationId(
                    facts.runId(),
                    StepKind.ACQUIRE,
                    m.meeting().meetingId(),
                    m.attempts().acquire()),
                operationId(
                    facts.runId(),
                    StepKind.PREPARE,
                    m.meeting().meetingId(),
                    m.attempts().prepare()),
                m.meeting().meetingId(),
                m.meeting().officialId(),
                m.meeting().date(),
                m.meeting().title(),
                prepared.pins());
        return new Step.Analyze(
            m.meeting(), m.attempts().analyze(), selection, priorRevisionIds(facts, m.meeting()));
      }
    }
    return new Step.Publish(manifest(facts));
  }

  public static List<UUID> priorRevisionIds(RunFacts facts, PlannedMeeting target) {
    var revisions = new ArrayList<Prior>();
    for (var m : facts.base().meetings()) {
      if (m.disposition() == Disposition.ANALYZED && m.date().isBefore(target.date())) {
        revisions.add(new Prior(m.date(), m.revisionId()));
      }
    }
    for (var m : facts.meetings().orElse(List.of())) {
      if (m.progress() instanceof Progress.Analyzed analyzed
          && m.meeting().date().isBefore(target.date())) {
        revisions.add(new Prior(m.meeting().date(), analyzed.revisionId()));
      }
    }
    return revisions.stream()
        .sorted(Comparator.comparing(Prior::date).thenComparing(Prior::revisionId))
        .map(Prior::revisionId)
        .distinct()
        .toList();
  }

  public static List<PlannedMeeting> plan(
      StartRun command, int maxMeetings, List<DiscoveredMeeting> found) throws PlanRejected {
    if (found.size() > maxMeetings) {
      throw new PlanRejected("RANGE_EXCEEDS_LIMIT");
    }
    var ids = new HashSet<UUID>();
    for (var m : found) {
      if (m.localDate() == null) {
        throw new PlanRejected("UNDATED_MEETING");
      }
      if (m.localDate().isBefore(command.from()) || m.localDate().isAfter(command.to())) {
        throw new PlanRejected("MEETING_OUT_OF_RANGE");
      }
      if (!ids.add(m.id())) {
        throw new PlanRejected("MEETING_DUPLICATE");
      }
    }
    var sorted =
        found.stream()
            .sorted(
                Comparator.comparing(DiscoveredMeeting::localDate)
                    .thenComparing(DiscoveredMeeting::officialId))
            .toList();
    var planned = new ArrayList<PlannedMeeting>();
    for (int i = 0; i < sorted.size(); i++) {
      var m = sorted.get(i);
      planned.add(
          new PlannedMeeting(
              i,
              m.id(),
              m.officialId(),
              m.localDate(),
              m.title(),
              m.cancellationEvidence() != null && !m.cancellationEvidence().isBlank()));
    }
    return List.copyOf(planned);
  }

  /** Called at admission, before any owner command is submitted. */
  public static void rejectOverlap(StartRun command, PublicationBase base) throws PlanRejected {
    var requested = new DateRange(command.from(), command.to());
    if (base.coverage().stream().anyMatch(requested::overlaps)) {
      throw new PlanRejected("RANGE_ALREADY_PUBLISHED");
    }
  }

  public static Progress afterAcquisition(PlannedMeeting meeting, List<Original> originals) {
    if (!originals.isEmpty()) {
      return new Progress.Acquired();
    }
    return new Progress.NoInputs(
        meeting.cancellationEvidence()
            ? NoInputReason.CANCELLED
            : NoInputReason.NO_RETAINED_ORIGINALS);
  }

  public static List<Pin> pins(List<Original> originals, List<Prepared> prepared)
      throws PlanRejected {
    if (originals.isEmpty() || originals.size() != prepared.size()) {
      throw new PlanRejected("PIN_JOIN_MISMATCH");
    }
    var results = new HashMap<UUID, Prepared>();
    for (var p : prepared) {
      if (results.putIfAbsent(p.acquisitionItemId(), p) != null) {
        throw new PlanRejected("PIN_JOIN_MISMATCH");
      }
    }
    var seen = new HashSet<UUID>();
    var pins = new ArrayList<Pin>();
    for (var original : originals) {
      var p = results.get(original.acquisitionItemId());
      if (!seen.add(original.acquisitionItemId())
          || p == null
          || !original.documentId().equals(p.documentId())
          || !original.versionId().equals(p.versionId())) {
        throw new PlanRejected("PIN_JOIN_MISMATCH");
      }
      pins.add(
          new Pin(
              original.acquisitionItemId(),
              original.documentId(),
              original.versionId(),
              original.artifactId(),
              original.sha256(),
              original.byteLength(),
              p.preparationId(),
              p.profileFingerprint()));
    }
    return List.copyOf(pins);
  }

  public static Manifest manifest(RunFacts facts) {
    if (facts.meetings().isEmpty()) {
      throw new IllegalStateException("Discovery is incomplete");
    }
    var combined = new LinkedHashMap<UUID, PublishedMeeting>();
    for (var m : facts.base().meetings()) {
      add(combined, m);
    }
    for (var f : ordered(facts)) {
      var m = f.meeting();
      var entry =
          switch (f.progress()) {
            case Progress.Analyzed a ->
                new PublishedMeeting(
                    m.meetingId(),
                    m.officialId(),
                    m.date(),
                    m.title(),
                    Disposition.ANALYZED,
                    a.revisionId(),
                    sources(a.pins()),
                    null);
            case Progress.NoInputs n ->
                new PublishedMeeting(
                    m.meetingId(),
                    m.officialId(),
                    m.date(),
                    m.title(),
                    n.reason() == NoInputReason.CANCELLED
                        ? Disposition.CANCELLED
                        : Disposition.NO_RETAINED_ORIGINALS,
                    null,
                    List.of(),
                    null);
            case Progress.Skipped s ->
                new PublishedMeeting(
                    m.meetingId(),
                    m.officialId(),
                    m.date(),
                    m.title(),
                    Disposition.SKIPPED,
                    null,
                    sources(s.pins()),
                    s.reason());
            default -> throw new IllegalStateException("Meeting is incomplete");
          };
      add(combined, entry);
    }
    var coverage = new ArrayList<>(facts.base().coverage());
    coverage.add(new DateRange(facts.command().from(), facts.command().to()));
    return new Manifest(
        publicationId(facts.runId()),
        facts.runId(),
        facts.base().publicationId(),
        coverage.stream()
            .sorted(Comparator.comparing(DateRange::from).thenComparing(DateRange::to))
            .toList(),
        combined.values().stream()
            .sorted(
                Comparator.comparing(PublishedMeeting::date)
                    .thenComparing(PublishedMeeting::officialId))
            .toList());
  }

  public static RetryHint hint(StepKind kind, String code) {
    if (code == null) {
      return RetryHint.UNKNOWN;
    }
    if (List.of("CAPACITY_REJECTED", "RUNTIME_FAILED", "INPUT_READ_FAILED", "SAVE_FAILED")
            .contains(code)
        || code.startsWith("OWNER_")) {
      return RetryHint.LIKELY_TRANSIENT;
    }
    if (code.equals("INVALID_OUTPUT")) {
      return RetryHint.MAY_REPEAT;
    }
    if (List.of(
                "CONFLICTING_LINK_BASIS",
                "EVIDENCE_SOURCE_MISMATCH",
                "PIN_JOIN_MISMATCH",
                "UNDATED_MEETING")
            .contains(code)
        || code.startsWith("SUBMIT_REJECTED:")
        || code.startsWith("RANGE_")
        || code.startsWith("MEETING_")) {
      return RetryHint.DETERMINISTIC;
    }
    return RetryHint.UNKNOWN;
  }

  private static List<MeetingFacts> ordered(RunFacts facts) {
    return facts.meetings().orElseThrow().stream()
        .sorted(Comparator.comparingInt(m -> m.meeting().ordinal()))
        .toList();
  }

  private static List<SourceVersion> sources(List<Pin> pins) {
    return pins.stream()
        .map(p -> new SourceVersion(p.documentId(), p.versionId()))
        .distinct()
        .sorted(
            Comparator.comparing(SourceVersion::documentId).thenComparing(SourceVersion::versionId))
        .toList();
  }

  private static void add(Map<UUID, PublishedMeeting> meetings, PublishedMeeting meeting) {
    if (meetings.putIfAbsent(meeting.meetingId(), meeting) != null) {
      throw new IllegalStateException("Duplicate meeting in publication");
    }
  }

  private record Prior(LocalDate date, UUID revisionId) {}
}
