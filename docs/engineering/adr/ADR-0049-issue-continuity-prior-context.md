# ADR-0049: Continue issues across meetings through prior-issue context, without merge or split

- **Status:** Proposed
- **Date:** 2026-11-22
- **Decider:** Kazim Yigit Ablay
- **Related requirements:** FR-09, FR-46; OQ-05
- **Related decisions:** ADR-0018, ADR-0028, ADR-0040, ADR-0048

## Context

An issue continues across meetings, but each model reply names issues with run-local labels. The frozen `grounded-findings-3` contract already accepts a list of earlier issues. It renders them into the model input and restricts `priorIssueId` to their IDs. No trial has used that list, so the model's ability to link a new mention to the right earlier issue is unmeasured. OQ-05, which covers merging and splitting issues, is deferred for Release 1.

## Decision

Each analyze command names the earlier meeting analysis revisions that form its context. The coordinator passes the accepted revisions of earlier meetings in the same run.

Analysis lists every issue mentioned in those revisions, in order of first appearance:
- Each issue gets a short label, p1 to pN.
- Its description is the name from its latest mention.
- Its reviewed-through date is that mention's meeting date, which must be before the current meeting.

The context and its hash are saved with the new revision.

When the model links an issue to a prior label, the new mention attaches to the existing issue and keeps its UUID. An unlinked issue creates a new issue. Two mentions linked to the same prior issue both attach to it. There's no automatic merge, split or alias handling. The contract stays frozen.

## Alternatives considered

- **Match issues automatically by name similarity.** This would link unrelated matters with similar names, which the T06 trials showed is a real failure, for example two projects merged into one issue.
- **Build a maintainer curation screen.** It doesn't fit the Release 1 schedule.
- **Revise the instructions and examples to teach prior-issue linking.** That needs new trials before the full run.
- **Let analysis pick the context itself.** This hides which meetings shaped a result and complicates later replay (ADR-0028).

## Consequences

- **Positive:**
  - Continuity is explicit and reproducible, because the context and its hash are saved.
  - Replay can rebuild the same context.
  - The frozen contract and its evidence stay valid.
- **Negative:**
  - If the model doesn't use a prior label, one real matter becomes two issues, and Release 1 has no way to merge them.
  - The prior-issue list grows with each meeting and uses model context.
- **Follow-ups:**
  - Check linking on two consecutive development meetings before the full run.
  - Measure duplicate and wrong links in T16.
  - Revisit OQ-05 after Release 1.

## AI assistance and provenance

Claude drafted this record from the contract code, the T06 trial records and the agreed Release 1 re-plan. It flagged that prior-issue linking has no trial evidence. Kazim selects the decision. No model behavior or test result is claimed by this record.
