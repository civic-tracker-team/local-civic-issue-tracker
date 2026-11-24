# ADR-0048: Save meeting analysis revisions through a durable analyze operation

- **Status:** Proposed
- **Date:** 2026-11-22
- **Decider:** Kazim Yigit Ablay
- **Related requirements:** FR-03, FR-41, FR-46; DR-07 to DR-10; NFR-16, NFR-17
- **Related decisions:** ADR-0006, ADR-0008, ADR-0009, ADR-0017, ADR-0018, ADR-0027, ADR-0029, ADR-0031, ADR-0039, ADR-0040

## Context

The T06 evaluator writes validated findings to JSON files in a developer folder. Release 1 needs those findings saved by the analysis service, so later views (T08), the coordinator (T11) and publication (ADR-0017) can refer to exact results. ADR-0009 makes analysis the owner of its domain results. ADR-0017 requires owners to save revisioned outputs before the coordinator publishes them. The schedule leaves about two days for this work.

## Decision

Analysis gets its own `analysis` schema with separate migration and runtime roles, following ADR-0029. New entities use the existing `AssignedEntity` UUID and new-state pattern.

Each successful analysis of one meeting is saved as an immutable meeting analysis revision. The revision holds:
- the evidence spans
- the developments with their date claims
- the issue mentions
- the associations with their basis
- the hashes and contract revision that produced them

Run-local labels from the model reply are replaced by UUIDs. The runtime role can only insert and read findings. A new analysis creates a new revision and never edits an old one.

Analysis exposes one durable analyze-meeting operation over HTTP, following ADR-0027. The client supplies the operation ID. The same ID with the same command returns the existing operation, and the same ID with a different command is a conflict.

A single execution slot admits one analysis at a time. The worker runs the existing T06 evaluation path unchanged and stores its attempt files on disk. It saves the revision in one transaction only when validation succeeds. Invalid output, capacity rejection and runtime failures end the operation as failed. Uncertain execution keeps the slot until a maintainer confirms the runtime stopped and resumes the operation. There is no automatic retry or takeover.

The model profile is fixed in service configuration, and its fingerprint is saved on each revision. Internal read routes return service-local DTOs, never entities. Public visibility stays with the publication view.

A developer command imports an existing successful trial folder as a revision marked as imported, after checking its recorded hashes.

## Alternatives considered

- **Keep the CLI and copy JSON files between services.** This leaves no owner-enforced record for publication to refer to.
- **Let the coordinator write analysis tables.** This violates ADR-0008 service ownership.
- **Rewrite the evaluator to run in memory.** This would change frozen, tested code days before the first full run.
- **Store attempt files in the database.** This duplicates up to several megabytes per attempt for little benefit, since hashes already tie the database to the files.

## Consequences

- **Positive:**
  - Findings get stable UUIDs, exact source references and immutable revisions that publication can name.
  - The evaluation path the trials exercised is reused as it is.
  - Imported trials give T08 real data before the operation is finished.
- **Negative:**
  - The analysis module gains database, migration and security dependencies.
  - Uncertain model execution needs maintainer action.
  - Attempt files on disk must be kept with the database for full provenance.
  - A maintainer can resume a blocked operation but cannot abandon it. Resume returns it to `ACCEPTED` with its original acceptance time, so it runs again next. If an input repeatedly ends in uncertain execution, it blocks the queue until someone edits the database. An abandon route that marks it `FAILED` and frees the slot is planned if time allows.
- **Follow-ups:**
  - Coordinator run control (ADR-0051)
  - The publication manifest
  - Public read routes (ADR-0053)
  - Re-analysis after corrections, which is deferred with FR-25

## AI assistance and provenance

The no-abandon consequence comes from the PR #7 review on November 24, 2026.

Claude drafted this record and the T07 plan from the code, the existing ADRs and the agreed Release 1 re-plan. Kazim selects the decisions. No implementation or test result is claimed by this record.
