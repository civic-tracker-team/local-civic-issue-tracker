# ADR-0009: Keep inference database-free and persist through the owner

- **Status:** Accepted
- **Date:** 2026-10-29
- **Recorded:** 2026-11-01, retrospectively
- **Deciders:** Kazim Yigit Ablay
- **Related requirements:** FR-42, NFR-16, NFR-17

## Context

OCR and language-model serving perform compute work, while records and analysis are responsible for the application data produced from that work. Giving inference wrappers database access would add another writer without making ownership clearer.

The coordinator also needs a meaningful completion boundary. An OCR response alone does not establish that validated readable content has been durably retained.

## Decision

OCR and the model runtime will have no application SQL access. They will return inference results to the requesting owner. Records will validate and durably save usable prepared content before acknowledging preparation completion. Analysis will supply model context, validate returned output and persist its domain results.

The coordinator requests and tracks owner-controlled work. It does not turn the inference wrappers into domain writers. Database-free inference may still use model files, configuration and temporary processing state.

This record does not choose synchronous versus asynchronous completion, callbacks, operation identifiers or an inference API framework. Those contracts must preserve the ownership and completion boundary.

## Alternatives considered

The design discussion explicitly questioned whether OCR and model serving needed application databases. Allowing those wrappers to modify domain tables was not selected. Their callers already own validation and persistence, so returning results keeps those responsibilities together.

The general possibility that a future design could change this boundary does not authorize SQL access now.

## Consequences

- **Positive:** Application writes stay with their owners, and compute implementations can change without receiving domain-table access.
- **Negative:** Owners must handle malformed, incomplete and unusable responses. Result transfer and recovery require contracts beyond a simple successful HTTP status.
- **Follow-ups:** Verify owner-side validation and durable completion. A timeout is not proof that remote work stopped, and a structurally valid result is not proof of factual support. No exactly-once or cancellation guarantee follows from this design.

## AI assistance and provenance

Codex helped assess inference persistence and check the selected workflow against FR-42 and NFR-16–NFR-17. Kazim selected owner-mediated persistence. The record does not claim that recovery or access controls have been implemented.

## References

- SRS v1.0
- ADR-0008: Service data ownership
- ADR-0015: Chronological coordination
- ADR-0021: Untrusted content
