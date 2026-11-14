# ADR-0025: Retain byte versions separately from source observations

- **Status:** Accepted
- **Date:** 2026-11-08
- **Recorded:** 2026-11-13
- **Refined:** 2026-11-13, rescan behavior
- **Deciders:** Kazim Yigit Ablay
- **Related requirements:** DR-02, DR-09, DR-11, DR-12

## Context

An official document can change without changing its identifier. Its metadata can also change while its bytes remain equal, and an older portal reference can be encountered after a newer authoritative observation.

## Decision

After document identity has been established, records computes SHA-256 over the original bytes and creates another retained version when those bytes change. Returning to previously stored bytes reuses that version and records a new observation rather than erasing the intervening history.

Observations retain source context, URLs, supplied publication information and retrieval time separately. A strictly newer authoritative observation can advance current references. Historical, older or conflicting observations remain recorded without automatically demoting them. Missing metadata does not instruct records to erase an existing value.

Rescans retrieve selected originals even when portal metadata appears unchanged, although a byte change such as a different HTML token does not itself establish a civic development.

## Alternatives considered

Overwriting originals would lose evidence supporting earlier results. Storing a new copy for every observation would duplicate unchanged bytes. Relying only on a portal date or ID would miss changes that leave those fields untouched.

## Consequences

- **Positive:** Original history and source metadata remain available without duplicating every unchanged version.
- **Negative:** Byte changes can exceed meaningful content changes, so downstream analysis still needs its own input comparison.
- **Follow-up:** Source authenticity and semantic equivalence are not established by SHA-256.

## AI assistance and provenance

Codex assisted with the version and observation reasoning and checked it against retention behavior. Kazim selected preservation of prior versions and authoritative current references.

## References

- SRS v2.0
- ADR-0019: Current reconstructed history
- ADR-0023: Source identifier reconciliation
- Records identity and versions
