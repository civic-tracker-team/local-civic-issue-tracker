# ADR-0019: Maintain current reconstructed history with source versions

- **Status:** Accepted
- **Date:** 2026-10-26
- **Recorded:** 2026-11-01, retrospectively
- **Deciders:** Kazim Yigit Ablay
- **Related requirements:** FR-09, FR-25, FR-26, FR-27, FR-28, DR-11, DR-12

## Context

Official records can arrive late, describe earlier developments or correct previously published information. An issue's currently supported history may therefore change after its initial analysis.

The project aims to reconstruct that history using the evidence available now. It is not a simulator of exactly what could have been known on each historical date. Retaining source attribution remains necessary even when later evidence changes the account.

## Decision

We will maintain current reconstructed history. New or corrected official records will update affected issue histories, timelines, summaries and meeting overviews where the evidence warrants it. Earlier imported source versions and the source-version associations behind derived results will remain retained.

Newer published official sources are generally preferred over older publications. An explicit correction updates the current account while preserving access to the earlier source basis. If comparable claims conflict without an explicit correction, the newer publication becomes the latest reported account, with the unresolved disagreement identified and both sources accessible.

Publication date is distinct from retrieval time and occurrence date. Newer sources do not erase legitimate earlier developments, and differing values at different stages are not automatically contradictions. A later report may establish an earlier event, but an earlier proposal does not establish that a later approval already occurred.

## Alternatives considered

Strict historical as-known-then replay was discussed and not selected. Preserving older source versions does not require that replay model.

Discarding earlier versions or silently inventing reconciliation would conflict with the selected provenance and contradiction behavior. Neither is part of this decision.

## Consequences

- **Positive:** The current account can improve as evidence arrives while retaining an explanation of its source basis.
- **Negative:** Updates may affect results beyond the newly imported meeting. Dependencies and revised publication need deliberate handling.
- **Follow-ups:** Define reanalysis scope and source-version relationships. Tied or absent publication dates, conflict detection and exact presentation remain open. Meeting-date processing order still applies and is distinct from displayed development dates.

## AI assistance and provenance

Codex helped organize late-evidence and correction reasoning and checked this record against the confirmed recency preference and SRS. Kazim selected current reconstruction and newer-publication preference. No reconciliation algorithm or tested replay procedure is claimed.

## References

- SRS v1.0
- ADR-0015: Chronological coordination
- ADR-0018: Evidence model
- ADR-0020: Temporal semantics
