# ADR-0015: Coordinate durable, non-overlapping chronological analysis

- **Status:** Accepted
- **Date:** 2026-10-26
- **Recorded:** 2026-11-01, retrospectively
- **Deciders:** Kazim Yigit Ablay
- **Related requirements:** FR-10, FR-11, FR-14, FR-15, FR-17, FR-34, FR-35, FR-42, DR-13, NFR-04, NFR-12

## Context

Later meeting analysis needs the established results of earlier meetings. Processing may also outlast a schedule interval or stop before completion. Starting another run merely because a scheduled time arrives could overlap work whose effects are unresolved.

The required control policy must hold independently of the eventual execution framework. Spring Batch remains conditional on demonstrating that policy, including exclusion across different job instances and recovery of unresolved remote work.

## Decision

The coordinator will own scheduled progression and durable execution information. Records will discover new or updated in-scope inputs and retrieve expected records. Analysis will use official meeting date as its primary order key, completing each meeting's analysis and saved result updates before beginning the next.

While a run is active or a processing failure remains unresolved, later meetings and runs stay pending. Available inputs and completed saved work are retained for recovery. A successful analysis may leave a date unknown or an association unsupported without becoming a failure. Inability to prepare required inputs or obtain usable validated analysis remains blocking.

If discovery finds no changed inputs and no pending work needs continuation, unchanged inputs do not trigger fresh analysis. Failed discovery is not evidence that there is no new work.

## Alternatives considered

The discussion considered queue-driven decomposition but selected a sequential workflow without requiring a broker. Pending work does not itself require a queue product. Allowing later analysis to proceed before earlier results are saved, or silently skipping failed meetings, does not satisfy the selected progression policy.

Spring Batch and a custom Java runner were compared separately. Neither implementation is accepted or verified by this framework-independent decision.

## Consequences

- **Positive:** Processing has an explicit owner, sequence and stopping boundary.
- **Negative:** One unresolved meeting can delay later work. Durable state and remote-operation reconciliation require implementation effort.
- **Follow-ups:** Verify exclusion, restart and failure scenarios. A timeout or failed framework execution does not prove remote work stopped. Tie-breakers, retries, scheduler cadence and recovery mechanics remain open. No exactly-once guarantee is asserted.

## AI assistance and provenance

Codex helped organize the workflow and framework comparison and checked the record against the confirmed execution policy and SRS. Kazim selected sequential progression and failure blocking. No concurrency or recovery test is reported as complete.

## References

- SRS v1.0
- ADR-0009: Inference completion boundary
- ADR-0016: Whole-meeting runs
- ADR-0017: Run publication
