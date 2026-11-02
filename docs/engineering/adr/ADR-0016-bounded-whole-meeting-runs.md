# ADR-0016: Prepare count-bounded whole-meeting runs before analysis

- **Status:** Accepted
- **Date:** 2026-10-27
- **Recorded:** 2026-11-01, retrospectively
- **Deciders:** Kazim Yigit Ablay
- **Related requirements:** LIM-11, FR-13, FR-16, FR-18, FR-19, FR-20

## Context

Initial ingestion may include years of records, while a later run may contain only a few meetings. Treating the entire historical collection as one unbounded run would make progress and publication difficult to manage.

The selected initial analysis unit is a whole meeting. The project preferred trying that approach before adopting subdivisions that would add internal planning and checkpoint rules.

## Decision

The coordinator will form runs from consecutive whole meetings up to a configurable maximum meeting count. The selected inputs for the active run will be retrieved and prepared, including native extraction and required OCR, before meeting analysis begins. Analysis then proceeds meeting by meeting under ADR-0015.

Expected inputs follow the official published, in-scope records for each meeting. Processing does not require a fixed agenda/minutes/transcript bundle when some types are unpublished. An officially cancelled meeting can be accounted for after its applicable expected inputs are handled successfully.

Whole-meeting analysis is the initial unit, not a requirement for one model call or one database transaction. No numerical count, page limit or adaptive partitioning algorithm is selected.

## Alternatives considered

Preparing and analyzing each meeting before preparing the next was discussed. Preparing the active run first was selected instead, separating input readiness from sequential analysis.

Agenda-item or passage subdivisions were proposed, but deferred in favor of attempting whole meetings first. Smaller analysis units remain an option if later capacity evidence warrants reconsideration. They are not implemented implicitly through this record.

## Consequences

- **Positive:** Historical ingestion can progress in bounded groups while maintaining whole-meeting context and a clear readiness boundary.
- **Negative:** Preparation delays the start of analysis for that run. A meeting-count bound does not bound pages, model context, processing time or memory.
- **Follow-ups:** Evaluate whole-meeting capacity and define planning/recovery details. The chosen unit does not authorize silent truncation, skipping failed inputs or assuming every meeting fits the selected model.

## AI assistance and provenance

Codex helped compare run formation and analysis granularity, then checked this record against the confirmed preparation and availability decisions. Kazim selected whole meetings and count-based bounds. Capacity remains unmeasured.

## References

- SRS v1.0
- ADR-0014: Meeting identity
- ADR-0015: Chronological coordination
- ADR-0017: Run publication
