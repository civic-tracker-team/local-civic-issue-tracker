# ADR-0028: Replay corrected history from the earliest affected meeting

- **Status:** Accepted
- **Date:** 2026-11-08
- **Recorded:** 2026-11-13
- **Deciders:** Kazim Yigit Ablay
- **Related requirements:** FR-14, FR-15, FR-25, FR-34, NFR-12

## Context

Meeting analysis can use the results of earlier meetings. Correcting an earlier source may therefore change later associations and summaries, even when those later source files have not changed.

## Decision

Historical reanalysis begins at the earliest affected meeting. It restores a verified analysis state preceding that boundary or, if none is available, rebuilds from the start of the selected retained collection. Meetings then complete in chronological order while earlier source versions and superseded evidence associations remain retained.

The coordinator uses durable database-controlled admission so active or unresolved work prevents another run from starting. Maintainers resolve failures through inspection, correction and explicit resume. Automatic skipping is not selected.

ADR-0017 governs publication during replay. The prior view remains available until the affected later history is coherent, including replay that spans several bounded runs. This coordinator behavior is planned and has not been established by records-service recovery tests.

## Alternatives considered

Updating only the changed meeting would ignore dependencies in later analysis. Selective dependency-based replay could reduce work but requires a dependency model not yet implemented. Rebuilding every historical input is the fallback when a trustworthy preceding state is unavailable.

## Consequences

- **Positive:** Later history is recomputed against the corrected earlier context.
- **Negative:** A correction can require substantial replay and delay publication.
- **Follow-up:** Verify checkpoint restoration, run exclusion and coherent publication before relying on the coordinator implementation.

## AI assistance and provenance

Codex helped organize the replay alternatives and distinguish recovery from publication. Kazim selected chronological replay with a verified preceding state or a rebuild.

## References

- SRS v2.0
- ADR-0015: Chronological coordination
- ADR-0017: Run publication
- ADR-0025: Source versions and observations
