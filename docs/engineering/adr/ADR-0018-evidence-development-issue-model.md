# ADR-0018: Separate source evidence, developments and ongoing issues

- **Status:** Accepted
- **Date:** 2026-10-26
- **Recorded:** 2026-11-01, retrospectively
- **Deciders:** Kazim Yigit Ablay
- **Related requirements:** FR-21, FR-22, FR-23, FR-24, FR-41, DR-07, DR-08; supporting record access FR-37

## Context

An issue timeline must describe a civic matter's evolution across records. A source passage, the development it supports and the ongoing matter are related, but they are not interchangeable. A summary alone would not preserve enough information to explain an association later.

An issue is a specific project, proposal, policy change or other civic matter. Shared broad subject matter does not establish the same issue, and one record may concern several issues.

## Decision

Analysis will represent source evidence, developments and ongoing issues as distinct linked concepts. Each evidence item retains an identifiable supporting source passage. Developments express supported proposals, decisions or changes, while issues connect the relevant developments over time.

Source-supported inferred associations will be included, with a visible distinction from source-stated relationships and access to supporting passages for either kind. When successfully processed sources provide insufficient support, the association remains unset. Published individual records remain accessible, and later evidence may establish a connection.

This conceptual separation does not select tables, Java classes, a graph database, a numeric confidence threshold or a merge/split workflow. It does not create a new issue for every unmatched record.

## Alternatives considered

The discussion distinguished source evidence from derived accounts rather than treating summaries as the only retained account of what the sources establish. It also confirmed that inferred links are necessary instead of limiting the system to relationships explicitly stated in every record.

Forcing an association in inconclusive cases was not selected. Abstention preserves the difference between missing support and a processing failure.

## Consequences

- **Positive:** Associations and timelines can expose their source basis, including whether a relationship is stated or inferred.
- **Negative:** Evidence associations and issue identity require explicit modeling and evaluation. Model confidence alone cannot establish source support.
- **Follow-ups:** Define mappings and identity judgments. Linking evaluation must use reviewed references and the agreed evidence checks; no acceptance percentage is introduced here.

## AI assistance and provenance

Codex helped organize the conceptual distinction and uncertainty behavior and checked this record against the SRS and accepted linking decisions. Kazim selected the evidence-based approach. No production linking model or accuracy result is asserted.

## References

- SRS v1.0
- ADR-0019: Reconstructed history
- ADR-0020: Temporal semantics
