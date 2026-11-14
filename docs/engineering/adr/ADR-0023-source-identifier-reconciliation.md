# ADR-0023: Match portal identifiers within their source context

- **Status:** Accepted
- **Date:** 2026-11-08
- **Recorded:** 2026-11-13
- **Deciders:** Kazim Yigit Ablay
- **Related requirements:** DR-01, DR-04, DR-05, DR-11

## Context

Portal identifiers normally remain stable, but inspected PrimeGov examples also showed replacement identifiers for unchanged content. Equal bytes alone cannot establish that documents from different agenda items have the same identity.

## Decision

Records first matches known official identifiers within their portal, meeting and identifier family, including historical aliases that continue to resolve to the same internal document.

For a new agenda-item identifier, automatic reuse requires exactly one equal-SHA-256 document in the same portal, meeting and agenda-item context. A generated meeting-level document additionally matches template ID, output type and language, with all required fields present. Missing, unmatched or ambiguous cases remain separate.

When continuity is established, the newer authoritative portal ID becomes primary while earlier IDs remain historical aliases. Neither continuity nor currentness is inferred from numeric ordering, filenames or titles.

## Alternatives considered

Treating every new portal ID as a new document would duplicate known unchanged records. Matching by title, position, file size or checksum across contexts would risk merging unrelated records. The narrower checksum fallback handles the observed replacement case while preserving uncertain inputs.

## Consequences

- **Positive:** Known replacement identifiers can preserve internal references without discarding source history.
- **Negative:** Ambiguous inputs remain separate and may need later reconciliation.
- **Follow-up:** Broader similarity matching and reconciliation interfaces remain outside this decision.

## AI assistance and provenance

Codex helped organize the portal comparison findings and checksum scope. Kazim selected the matching and conservative-retention rules.

## References

- SRS v2.0
- ADR-0022: Internal identifiers
- ADR-0025: Source versions and observations
- Records identity and versions
