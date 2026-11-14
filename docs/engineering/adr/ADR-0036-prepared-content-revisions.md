# ADR-0036: Retain immutable prepared text with exact source locations

- **Status:** Accepted
- **Date:** 2026-11-13
- **Recorded:** 2026-11-13
- **Deciders:** Kazim Yigit Ablay
- **Related requirements:** DR-06, DR-08, DR-11, DR-12

## Context

Analysis evidence can cite extracted text that later changes after a parser correction or model update. Overwriting that text would leave the same citation referring to different words or positions.

## Decision

Records retains immutable prepared revisions associated with the exact document version, artifact and preparation profile. Segments retain stable IDs, literal text, extraction methods and source locations within their revision, and reads expose them only after that revision is sealed.

PDF locations retain physical pages, optional printed labels and crop/rotation-aware coordinates. OCR polygons preserve their input-image identity and transform into the displayed page frame. HTML locations retain item and section IDs with version-local structure. Text offsets count Unicode code points against the exact sealed segment.

Reuse requires the same version, artifact and full profile. Changed settings or an explicit corrective run create another revision without overwriting the earlier one. This does not automatically replay published analysis.

## Alternatives considered

One mutable text column would simplify storage but invalidate existing evidence references. Reusing another document's prepared identity because its bytes match would lose source context. Image-only results would omit the queryable text and location references needed by analysis.

## Consequences

- **Positive:** Evidence can continue to identify the text and source location actually used.
- **Negative:** Repeated preparation retains additional revisions and requires explicit selection during reads.
- **Follow-up:** Parser or routing changes must change the profile or pipeline revision. Semantic correctness still needs evidence review.

## AI assistance and provenance

Codex helped organize the revision and locator design and verify code-point handling. Kazim selected immutable prepared content and preservation of earlier revisions.

## References

- SRS v2.0
- ADR-0026: Records read contracts
- ADR-0034: Content preparation
- ADR-0035: Pinned OCR runtime
