# ADR-0017: Publish completed runs and retain the previous published view

- **Status:** Accepted
- **Date:** 2026-10-30
- **Recorded:** 2026-11-01, retrospectively
- **Deciders:** Kazim Yigit Ablay
- **Related requirements:** FR-33, NFR-10, NFR-11, NFR-13

## Context

A meeting's analysis can be completed and saved before the bounded run containing it finishes. Internal progress and user-visible results therefore need distinct boundaries.

Users should still be able to browse and search previously published information while import, analysis or recovery is in progress. Publishing partly updated results merely because one meeting finished would introduce another visibility policy to manage.

## Decision

We will make a bounded run's new results available to users after the run completes successfully. Completed internal meeting work within an unfinished or unsuccessful run will remain unpublished.

The last successfully published results will remain available during later processing and unresolved failure/recovery. Browsing and search will use published results without waiting for the entire new run to finish. A run is the selected bounded group of whole meetings, not the full historical archive.

This decision specifies visibility behavior. It does not choose a staging schema, publication pointer, search synchronization mechanism or one long transaction across services. Browser refresh and first-publication presentation remain separate details.

## Alternatives considered

Per-meeting publication was explicitly compared with publication after the whole bounded run. Kazim selected whole-run publication because it seemed easier to implement and manage.

This was a design preference, not an experimentally established claim that the implementation will be simpler under every storage arrangement. Keeping previous results available does not authorize serving partially updated new results.

## Consequences

- **Positive:** A run has one user-facing completion boundary, while prior usable results remain available.
- **Negative:** Users wait longer to see work already completed internally. Reading services need a coherent distinction between published and unpublished data.
- **Follow-ups:** Design and verify the publication mechanism, including failed runs and search consistency. Separate service processes alone do not guarantee responsiveness or prevent shared-resource contention.

## AI assistance and provenance

Codex helped compare publication options and check the visibility policy against FR-33 and NFR-10–NFR-13. Kazim selected whole-run publication and preservation of the prior view. No transaction or availability mechanism has been validated by this record.

## References

- SRS v1.0
- ADR-0015: Chronological coordination
- ADR-0016: Whole-meeting runs
