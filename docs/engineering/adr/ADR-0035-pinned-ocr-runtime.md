# ADR-0035: Use a pinned local PP-OCRv6 medium runtime

- **Status:** Accepted
- **Date:** 2026-11-13
- **Recorded:** 2026-11-13
- **Deciders:** Kazim Yigit Ablay
- **Related requirements:** LIM-10, FR-12, DR-06, NFR-16

## Context

The extraction prototype compared local recognition approaches for map labels and scanned content. Model weights, package versions and orientation handling affected the results, so a model name alone would not identify the tested behavior.

## Decision

The first OCR service uses PP-OCRv6 medium through PaddleOCR in a pinned Python 3.12 Linux or WSL environment. Model artifacts, package versions, orientation policy and relevant processing settings form a runtime fingerprint.

The selected orientation policy classifies suitable vertical text regions and preserves per-item alignment through a version-specific adapter. The adapter keeps its changes outside installed packages, and the wrapper specifies the input colour conversion needed by Paddle. A changed runtime identity requires a corresponding preparation profile rather than silently reusing earlier prepared output.

MapReader, vision-language alternatives, automatic enlargement and tiling are outside the initial service. Their prototype results do not become production dependencies.

## Alternatives considered

Using unpinned packages would make results difficult to reproduce. Disabling orientation remains a useful comparison, but it performed worse on the reviewed development labels. Larger recognition systems would require separate qualification and capacity checks.

## Consequences

- **Positive:** Recognition results can be associated with the actual weights and processing configuration used.
- **Negative:** The adapter is tied to particular library behavior, and upgrades need renewed qualification.
- **Follow-up:** Focused map-label results do not establish broad document accuracy or complete label detection.

## AI assistance and provenance

Codex assisted with runtime qualification, comparison setup and documentation. Kazim selected the local PP-OCRv6 starting approach with recorded model and package identities.

## References

- SRS v2.0
- ADR-0013: Native-first extraction
- ADR-0034: Content preparation
- ADR-0036: Prepared-content revisions
