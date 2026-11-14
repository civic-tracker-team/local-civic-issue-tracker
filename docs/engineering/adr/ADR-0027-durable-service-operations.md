# ADR-0027: Save processing operations before executing their work

- **Status:** Accepted
- **Date:** 2026-11-08
- **Recorded:** 2026-11-13
- **Refined:** 2026-11-13, records worker and checkpoint behavior
- **Deciders:** Kazim Yigit Ablay
- **Related requirements:** FR-42, NFR-04, DR-13

## Context

Acquisition and preparation can outlast an HTTP request or stop after partial progress. A client timeout does not show whether a command was accepted, and an in-memory task cannot preserve that distinction after restart.

## Decision

Processing services persist an operation UUID and fixed input before returning HTTP 202 with a status URL. Equivalent submissions return the existing operation, whereas reusing that UUID with different input produces a conflict. Operation state distinguishes accepted, running, succeeded and failed work, with success recorded only after validation and durable storage.

Records uses one persisted execution slot and checks the attempt identity when committing checkpoints. Completed inputs can be reused on resume. Running work is not reclaimed automatically after a timeout or restart. Maintainer recovery confirms that the previous execution stopped, and changed inputs use linked replacement operations.

Errors use RFC 9457 with stable application codes and correlation references. Records' local slot does not replace the coordinator's cross-service run admission.

## Alternatives considered

Synchronous request-only execution would leave clients uncertain after disconnection. An in-memory future would lose ownership on failure. A broker is not required by the selected sequential workflow, and timed takeover could overlap an execution that is still running.

## Consequences

- **Positive:** Repeated command delivery and interrupted work have explicit identities and recoverable checkpoints.
- **Negative:** Maintainers must reconcile uncertain execution instead of relying on automatic takeover.
- **Follow-up:** Coordinator admission and analysis operations need their own implementation and integration tests.

## AI assistance and provenance

Codex assisted with the operation contract, recovery comparison and implementation checks. Kazim accepted persisted operations and explicit reconciliation.

## References

- SRS v2.0
- ADR-0015: Chronological coordination
- ADR-0032: PrimeGov acquisition
- ADR-0037: OCR recovery
