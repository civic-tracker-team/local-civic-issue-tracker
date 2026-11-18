# ADR-0037: Keep OCR synchronous with caller-owned recovery

- **Status:** Accepted
- **Date:** 2026-11-13
- **Recorded:** 2026-11-13
- **Deciders:** Kazim Yigit Ablay
- **Related requirements:** FR-42, NFR-04, NFR-16, NFR-17

## Context

OCR is a compute service, while records owns durable preparation. A request can time out or disconnect while recognition continues, so losing the HTTP response cannot safely release the execution boundary and start replacement work.

## Decision

The FastAPI wrapper accepts bounded image bytes and metadata through a synchronous per-image request. It validates the input identity, uses a service-only bearer credential and permits one active inference per configured instance. It has no application SQL connection, message broker or separate durable job queue.

Records saves an execution reference before calling OCR. An ambiguous response retains its running operation and slot until a maintainer confirms that both the earlier records execution and referenced inference stopped. Completed units remain available for resume, and attempt checks reject stale saves.

Within one wrapper instance, repeated execution IDs do not repeat completed inference, and conflicting inputs are rejected. Cancelling an HTTP handler leaves admission occupied while recognition continues, and the instance-local cache remains separate from records' durable execution state.

## Alternatives considered

Releasing the slot on every timeout could overlap unfinished inference. A broker and another OCR database would duplicate durable ownership for this initial sequential workflow. Automatic process termination or remote-status recovery would need additional mechanisms and verification.

## Consequences

- **Positive:** OCR remains a small compute service while records controls persistence and uncertain execution.
- **Negative:** Ambiguous failures require explicit stop confirmation and can delay later work.
- **Follow-up:** A new listener or instance ID is not proof of termination. Automatic cancellation and cross-service coordinator recovery remain separate work.

## AI assistance and provenance

Codex assisted with the HTTP boundary, cancellation and crash-recovery checks. Kazim accepted the synchronous wrapper and manual reconciliation of both executions.

## References

- SRS v2.0
- ADR-0009: Inference persistence boundary
- ADR-0027: Durable service operations
- ADR-0036: Prepared-content revisions
