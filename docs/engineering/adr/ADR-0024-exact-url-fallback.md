# ADR-0024: Use an exact-URL fallback when official IDs are unavailable

- **Status:** Accepted
- **Date:** 2026-11-08
- **Recorded:** 2026-11-13
- **Deciders:** Kazim Yigit Ablay
- **Related requirements:** DR-01, DR-05

## Context

Some supported source forms can provide a document link without a usable official identifier. Records needs an initial way to recognize repeated retrievals without assuming that unrelated links identify the same document.

## Decision

Within the same portal, meeting and agenda-item context where applicable, records uses the exact absolute source URL as the initial fallback identity. Query parameters are preserved, and an unchanged URL can reuse the internal document UUID while SHA-256 determines whether its bytes changed. Known official identifiers take precedence.

This acceptance is deliberately limited. Temporary or signed URLs, redirects, fragments, repeated links and later assignment of an official ID need further consideration. A changed URL does not automatically establish continuity.

## Alternatives considered

Removing query parameters could merge different source selections. Matching filenames or visible titles would also discard identifying information. Treating every retrieval as new would avoid those merges but duplicate repeated inputs even when their exact reference is unchanged.

## Consequences

- **Positive:** Supported missing-ID inputs have a conservative initial matching path.
- **Negative:** Changing temporary links can produce separate records for the same underlying document.
- **Follow-up:** Representative live missing-ID cases and the unresolved URL cases still need evaluation.

## AI assistance and provenance

Codex helped describe the limited fallback and its unresolved cases. Kazim accepted exact matching without speculative URL normalization.

## References

- SRS v2.0
- ADR-0023: Source identifier reconciliation
- Records identity and versions
