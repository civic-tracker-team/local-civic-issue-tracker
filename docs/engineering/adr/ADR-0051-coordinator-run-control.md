# ADR-0051: Drive bounded runs with a reconciling coordinator

- **Status:** Proposed
- **Date:** 2026-11-24
- **Decider:** Kazim Yigit Ablay
- **Related requirements:** FR-13 to FR-16, FR-20, FR-33 to FR-35; NFR-10 to NFR-12; OQ-04
- **Related decisions:** ADR-0008, ADR-0015, ADR-0016, ADR-0017, ADR-0027, ADR-0028, ADR-0029, ADR-0031, ADR-0048, ADR-0049

## Context

Release 1 runs one bounded range of consecutive meetings through Records and analysis, then publishes. Both owners already provide durable, idempotent operations with client-supplied IDs (ADR-0027), but they don't order work across meetings or publish anything. The coordinator is the only component allowed to do that (ADR-0015, ADR-0017). It has to survive restarts in the middle of a run, without taking over owner work and without a workflow engine.

## Decision

The coordinator keeps, in its own schema, only facts it learned once:
- a run's frozen meeting list, ordered by meeting date and then official ID
- per-meeting progress
- the pinned inputs and the frozen analysis request
- revision IDs and attempt counters

A pure plan function picks the next step from those facts. Records preparation of every meeting finishes before analysis starts. Analysis then goes meeting by meeting in date order, and the run halts on the first failed owner operation.

Each owner operation ID is a name-based UUID computed from the run, the step kind, the meeting and the attempt number. A scheduled driver checks the owner's status for that ID, and only submits it when the owner doesn't know it yet. A retry increments the attempt.

The coordinator's own actions are repeatable owner commands or local transactions, so a coordinator restart continues without a maintainer confirming that the earlier driver stopped. Uncertain owner execution is still resolved through each owner's own resume (ADR-0027). The coordinator waits for it and never proxies it.

**Admission.** A single slot row admits one run at a time. A run holds the slot while it's ACTIVE, HALTED or READY, and releases it when it's PUBLISHED or ABANDONED (ADR-0028).

**Resolving a halted run.** Kazim answered OQ-04 on November 24, 2026. A maintainer may retry, abandon, or skip a halted meeting with a written reason. The skip records the maintainer, appears as SKIPPED in the manifest and supplies no analysis revision to later priors. Automatic skipping remains excluded.

**Empty discovery.** A discovery that finds no meetings halts instead of publishing coverage for the range, because a published range cannot be processed again until re-analysis exists.

**Meetings with no retained originals** are accounted for without preparation or analysis, either as cancelled or as having no retained originals. Analysis rejects empty input.

**Earlier context.** Prior revisions include analyzed meetings from the base publication and this run with strictly earlier dates. Same-day meetings do not share context.

**Publication.** When every meeting is terminal, the run becomes READY. A configuration toggle, which defaults to off, decides who publishes it. With the toggle off, the run waits for a maintainer's publish command. With it on, the driver publishes the run by itself. Both use the same publish step and checks. One coordinator transaction inserts a manifest and compare-and-sets the current-publication pointer on the previous publication. The manifest carries the previous publication forward, plus this run's meetings, each with its disposition, exact analysis revision and source versions.

**Release 1 membership exception.** Public readers read the current manifest from the coordinator and enforce membership themselves. Owners don't check publication IDs yet.

**Spring Batch is not used.**

## Alternatives considered

- **A maintainer script calling the owners in order.** It keeps no durable record of what was sent. Every caller would repeat the ordering and pin rules, and the publication pointer would still need a durable home. It stays as the documented fallback: the same tables and core, with a manual `advance` command in place of the scheduled driver.
- **An outbox with stored in-flight step states.** It has to reconcile "submitted but unknown" rows after a crash, which is exactly the window the computed IDs remove.
- **An event-sourced run journal.** It adds projections and replay for little gain, since owners keep each attempt's history.
- **Proxying owner resume through the coordinator.** That would duplicate two owners' resume contracts.
- **Spring Batch or an external scheduler.** Excluded by the re-plan, and its restart model conflicts with the rule of no automatic takeover.

## Consequences

- **Positive:**
  - Crash recovery is the normal code path.
  - Only a complete manifest becomes public, including explicit skipped or empty meetings.
  - Run views expose the stage, halt and owner waiting information.
  - Publications are immutable and can be cached by ID.
- **Negative:**
  - The design depends on owners deduplicating resent commands.
  - Only one owner operation is in flight per run.
  - A new range can't overlap published coverage until re-analysis exists (FR-25).
  - Manual skips leave gaps in findings and can interrupt issue continuity.
  - Issues first raised at two meetings on the same day get separate identities.
- **Follow-ups:**
  - owner-side membership checks
  - scheduled runs (FR-10, FR-17)
  - re-analysis after corrections (FR-25)
  - an analysis abandon route

## AI assistance and provenance

Claude ran a design arena: three independent candidate designs and a cross-judge, with the synthesis recorded in the T11 planning notes. It drafted this record. Kazim accepted the decisions on November 24, 2026, including manual skip and the default-off automatic-publish toggle. Codex implemented the accepted plan and updated this record. Its status remains Proposed; automated verification is recorded separately. During review of PR #8, Michael Avila asked for the empty-discovery halt and for refusing a discovery retry once meetings are saved. Claude implemented both.
