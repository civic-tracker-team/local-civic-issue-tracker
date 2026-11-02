# ADR-0014: Preserve portal-scoped meeting identity and record relationships

- **Status:** Accepted
- **Date:** 2026-10-27
- **Recorded:** 2026-11-01, retrospectively
- **Deciders:** Kazim Yigit Ablay
- **Related requirements:** DR-04, DR-05, DR-16

## Context

The pipeline processes meetings, but downloads produce individual documents and attachments. A meeting date is useful for ordering, while a file's location is useful for retrieval. Neither alone identifies the scheduled meeting and its official relationships.

A document can also describe an earlier meeting or development. Its subject must not replace the actual portal association from which it was obtained.

## Decision

We will use the official portal meeting identifier within its source-portal context as the basic source meeting identity. Records will preserve official relationships among meetings, documents, agenda items and attachments through retrieval and preparation.

Meeting date remains an ordering value rather than an identity key. The model will retain a cancelled meeting's identity and official cancellation evidence rather than discard the scheduled entry or infer cancellation from missing minutes.

This source identity decision does not require every internal primary key to equal a portal identifier. Internal identifiers, duplicate reconciliation, language-entry handling and detailed tables remain separate design work.

## Alternatives considered

The design explicitly selected official IDs and retained relationships when deciding how downloaded files constitute a meeting.

As a retrospective comparison, dates alone can identify neither distinct same-day meetings nor the correct document associations. Treating every downloaded file as an independent record without its relationships would lose source context. These observations explain the selected identity boundary, not a separate historical identifier benchmark.

## Consequences

- **Positive:** Processing and provenance can refer to a stable source meeting while retaining the context of its attachments and agenda items.
- **Negative:** Import must carry source relationships forward rather than keep only file bytes or display titles. Duplicate and corrected entries still require careful handling.
- **Follow-ups:** Define internal identifiers and relationship contracts. Preserve cancellation as a supported meeting state without inventing held-meeting outcomes or cancellation of underlying civic issues.

## AI assistance and provenance

Codex helped clarify meeting identity and check this record against the source-identity and cancellation decisions. Kazim selected portal-scoped official IDs. The record does not select a full database schema or merge policy.

## References

- SRS v1.0
- ADR-0015: Chronological coordination
- ADR-0016: Whole-meeting runs
