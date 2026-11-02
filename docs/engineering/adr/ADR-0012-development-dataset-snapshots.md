# ADR-0012: Share coordinated datasets between local development instances

- **Status:** Accepted
- **Date:** 2026-10-29
- **Recorded:** 2026-11-01, retrospectively
- **Deciders:** Kazim Yigit Ablay
- **Related requirements:** DR-17; supporting storage constraints LIM-06, LIM-07

## Context

Both developers need to use retained documents and processed outputs, including analysis already completed on another machine. Repeating extraction and inference merely to obtain the same development dataset would add work.

The storage design separates files from SQL references and queryable results. A database export without its referenced files, or a file archive without the matching database state, is therefore not a complete development copy.

## Decision

We will develop using separate local instances and share coordinated dataset snapshots. A shared dataset contains a database snapshot and the matching retained original files and artifacts referenced by it. The recipient can restore saved records and analysis results locally rather than rerun the whole processing pipeline.

The data must remain usable with each developer's configured storage location. Exchanging a dataset does not merge two independently changed databases or establish live synchronization.

Exact packaging, transfer channel, snapshot producer, cadence, automation and treatment of active coordinator state remain to be designed. This record does not prescribe a transfer command or introduce a remote storage dependency.

## Alternatives considered

A shared remote database and persistent file server were discussed as one way to let both developers access the same data. Separate local copies with coordinated snapshots were accepted as the direction when that infrastructure is not used.

Rerunning inference is possible but is not necessary simply to use existing saved results. Automatic two-way merging was not selected.

## Consequences

- **Positive:** Developers can work locally with the same retained source and processed dataset without requiring a shared server.
- **Negative:** Copies can diverge, and snapshot creation/restoration must keep file references consistent. This is a transfer workflow rather than collaboration through one live database.
- **Follow-ups:** Verify a restore using the matching files and database. Define how snapshots are obtained without treating an incomplete or active state as a ready dataset. No tested backup or disaster-recovery procedure is claimed.

## AI assistance and provenance

Codex helped explain the local-copy workflow and check it against DR-17 and the storage decisions. Kazim accepted snapshot sharing. Packaging and restore validation remain outstanding.

## References

- SRS v1.0
- ADR-0006: File and SQL storage
- ADR-0007: PostgreSQL layout
