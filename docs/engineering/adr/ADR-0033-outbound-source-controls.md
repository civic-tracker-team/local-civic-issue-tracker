# ADR-0033: Restrict and bound outbound portal requests

- **Status:** Accepted
- **Date:** 2026-11-13
- **Recorded:** 2026-11-13
- **Deciders:** Kazim Yigit Ablay
- **Related requirements:** LIM-12, NFR-04, NFR-15

## Context

Source links and redirects come from external data. Fetching them without destination and resource checks could reach unintended systems or let one transfer occupy records indefinitely.

## Decision

Records uses configured HTTPS source and delivery hosts, validates redirect destinations and rejects unapproved or private destinations. The explicit loopback option is for local fixture endpoints. Internal service credentials are not forwarded to sources.

A persisted request reservation controls pacing for each source, including redirects. Connect and transfer deadlines, byte limits and redirect limits bound retrieval. A valid Retry-After response delays later access without starting an unlimited automatic retry loop. Partial or stalled transfers cannot be acknowledged as completed originals.

On November 16, 2026, Kazim confirmed that the source's existing transfer timeout also bounds server-requested delays and pacing waits. Excessive Retry-After values fail explicitly without being persisted. An excessive saved gate fails immediately without being cleared or extended. This avoids a separate numerical default and prevents a persisted cooldown from occupying the worker for years.

Profile settings are fixed when discovery is accepted. Their numerical values are deployment controls rather than promised throughput or product acceptance targets.

## Alternatives considered

Unrestricted redirects would weaken the configured source boundary. In-memory pacing would be lost on restart and could be bypassed by another service instance. Assuming that a nonempty body is complete would retain truncated records as successful inputs.

## Consequences

- **Positive:** Source retrieval has explicit destination, pacing and completion boundaries.
- **Negative:** New official delivery hosts need configuration review, and limits can reject unusually large legitimate documents.
- **Follow-up:** Review host and limit changes against observed official sources without allowing wildcards to bypass validation.

## AI assistance and provenance

Codex helped identify redirect and request-budget cases and check them against the adapter. Kazim accepted the constrained outbound access approach.

Codex reproduced the excessive-delay defect and implemented regression checks for the November 16 refinement. Kazim selected reuse of the existing transfer timeout as the bound.

## References

- SRS v2.0
- ADR-0021: Imported-content boundary
- ADR-0032: PrimeGov acquisition
