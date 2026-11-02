# ADR-0020: Preserve temporal meaning and uncertainty

- **Status:** Accepted
- **Date:** 2026-10-29
- **Recorded:** 2026-11-01, retrospectively
- **Deciders:** Kazim Yigit Ablay
- **Related requirements:** FR-29, FR-30, FR-31, FR-32, DR-09, DR-10, NFR-07, NFR-08

## Context

An issue timeline represents the evolution of a civic matter, not merely a chronological feed of documents. A meeting can report an earlier event or discuss a future plan. Using its date as the occurrence date in every case would change the meaning of the source.

Records may also supply only a month, year or range. Turning that into an exact day or an exact sequence would create precision the evidence does not provide.

## Decision

We will distinguish development occurrence dates, source meeting dates and available publication dates in retained data. The timeline will use a source-supported occurrence date when available. Otherwise, it will show the source meeting date as a reported or discussed date rather than claim that the event occurred then.

Approximate dates, ranges and unknown values will retain their meaning. Coarse or overlapping dates will not be presented as a source-established exact sequence. Source-supported future dates remain planned or expected until later official evidence establishes a change; the passage of time alone does not establish completion, failure or cancellation.

Processing order remains primarily meeting-date based. That order does not override the meaning of a development's displayed date or its actual source attribution.

## Alternatives considered

The date discussion contrasted using source meeting dates with using supported occurrence dates and a labeled fallback. Kazim selected the latter after reviewing the temporal extraction approach.

The model investigation supported possible implementation methods. It did not select a production model, prove extraction accuracy or justify automatic exact-date imputation.

## Consequences

- **Positive:** Timeline dates explain when something happened, when it was reported and what remains uncertain without conflating those meanings.
- **Negative:** Data contracts and presentation must carry precision and date roles rather than one timestamp. A visual layout may still need placement rules that do not imply factual certainty.
- **Follow-ups:** Define date representation, renderer behavior and representative evaluation cases. Keep any technical tie-breaking distinct from evidence-supported chronology. No automatic date-resolution algorithm is selected here.

## AI assistance and provenance

Codex supported temporal-grounding analysis and checked this record against the adopted occurrence-date/fallback policy and SRS. Kazim selected that policy. Model capacity and temporal extraction quality remain to be evaluated.

## References

- SRS v1.0
- ADR-0015: Processing order
- ADR-0019: Reconstructed history
