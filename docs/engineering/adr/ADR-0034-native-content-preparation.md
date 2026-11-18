# ADR-0034: Check content coverage before accepting native extraction

- **Status:** Accepted
- **Date:** 2026-11-13
- **Recorded:** 2026-11-13
- **Deciders:** Kazim Yigit Ablay
- **Related requirements:** LIM-10, FR-12, FR-42, DR-06, NFR-16

## Context

A PDF can contain a native heading or footer while its body is scanned or drawn as outlines. Agenda HTML can also contain nested items whose source identities would be lost if a parent were flattened into one text block.

## Decision

Records uses PDFBox for native PDF extraction and rendering, and jsoup for retained agenda HTML. Where native text adequately recovers the content, it remains preferred, and useful native text stays distinguishable from any additional OCR contribution.

Routing considers page and region content. Known embedded-image placement can preserve source resolution, while composite or transformed content uses rendering. Mixed native/vector pages and uncertain native/image overlap require recorded selections. Without useful native body text, vector content uses a full render. Nested HTML item and section boundaries preserve their own locators and source order.

Every selected input needs usable saved content or an explicit justified blank, non-text or no-input disposition. Unsupported, unresolved or oversized content fails rather than becoming empty or truncated success.

## Alternatives considered

Accepting any nonempty text layer would miss image or outlined body content. OCR on every page would replace useful native text unnecessarily. An automatic decoration classifier would introduce another inference problem, so the initial approach records ambiguous selections explicitly.

## Consequences

- **Positive:** Preparation accounts for content that a native-text presence check would miss.
- **Negative:** Conservative graphics checks can require review of table rules, underlines and decoration.
- **Follow-up:** Source comparisons and regression tests cover known cases. Archive-wide coverage and transcription quality remain unmeasured.

## AI assistance and provenance

Codex assisted with parser comparisons, implementation and review. Review exposed omitted vector content and nested HTML identities, which were corrected with regression tests. Kazim accepted native-first preparation and explicit ambiguous selections.

## References

- SRS v2.0
- ADR-0013: Native-first extraction
- ADR-0035: Pinned OCR runtime
- ADR-0036: Prepared-content revisions
