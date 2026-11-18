# ADR-0030: Install complete files before committing their metadata

- **Status:** Accepted
- **Date:** 2026-11-08
- **Recorded:** 2026-11-13
- **Deciders:** Kazim Yigit Ablay
- **Related requirements:** DR-02, DR-03, NFR-04

## Context

Retained originals live in file storage while SQL holds their references. Those systems do not share a transaction, so interruption can otherwise leave a database row referring to a missing or partial file.

## Decision

Records stages, hashes and flushes bytes before installing a complete file under a relative UUID-derived key. Metadata references commit only after installation. Source filenames remain metadata, while deployment configuration supplies the persistent storage directory so that retained references do not depend on one developer's machine.

An interrupted staging write is removed. If file installation succeeds and the database transaction fails, the complete unreferenced file can remain. Automatic orphan deletion is deferred until a retention and reconciliation policy is established. External success waits for the relevant database commit.

## Alternatives considered

Committing metadata first could expose a file that is not ready. Storing every original as a SQL binary would change the selected file-storage boundary. A distributed transaction would add complexity without removing the need to understand filesystem failures.

## Consequences

- **Positive:** Acknowledged references point to complete retained files, and relative keys support storage relocation.
- **Negative:** Failed metadata commits can leave orphan files that need later reconciliation.
- **Follow-up:** Test interrupted writes, rollback, relocation and restart retrieval. These checks do not establish protection against every machine power failure.

## AI assistance and provenance

Codex helped organize the file and transaction failure cases and verify the ordering. Kazim accepted staged installation before metadata commit and deferred automatic deletion.

## References

- SRS v2.0
- ADR-0006: File and SQL storage
- ADR-0029: Owner-managed migrations
