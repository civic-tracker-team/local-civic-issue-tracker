# ADR-0044: Read screen data through a typed API-client layer, starting with sample data

- **Status:** Accepted
- **Date:** 2026-11-15
- **Recorded:** 2026-11-18
- **Deciders:** Michael Avila
- **Related requirements:** LIM-03, IF-02, NFR-02

## Context

Only the records service has read endpoints so far. The screens can be built and reviewed now from the sample content in the signed-off designs, but they must not hard-wire that content, or each service's arrival would mean rewriting screens.

## Decision

Screens read data only through async functions in `frontend/src/api/`, such as `getIssue`, `getRecord` and `search`. Typed sample data sits behind those functions, and no screen imports it directly. Each function can be replaced by a real endpoint call when its service is ready. Types are named after the SRS and the ADRs: evidence, basis, issue, development, meeting, record, version and temporal dates, following ADR-0018 and ADR-0020.

Every screen shows a "Sample data" badge while it uses sample data. The sample contains no real records, no city branding and no made-up official links. Source passages render as plain text nodes, never as HTML.

## Alternatives considered

Waiting for each service's API before building its screens would hold the frontend back for most of the term. Importing sample data straight into screens would be faster at first but would have to be undone screen by screen.

## Consequences

- **Positive:** The screens can be built, reviewed and tested now, and real endpoints can replace sample functions one at a time.
- **Negative:** The browser types are presentation contracts, not the services' wire contracts. A records `Document` response, for example, still has to be mapped to the browser's `CivicRecord`. Sample totals such as "41 records" describe the designs, not stored data.
- **Follow-up:** Each service endpoint needs a mapping function and a check that the screen still shows the basis and date precision correctly.

## AI assistance and provenance

The options were prepared with AI assistance as open question 8. Michael chose the API-client layer with sample data on 2026-11-15. Codex (GPT-6.1 Sol) delegates built the types, sample data and client, coordinated by Claude Code.

## References

- SRS v2.0
- ADR-0018: Evidence, development and issue model
- ADR-0020: Temporal data semantics
- ADR-0021: Untrusted content boundary
- ADR-0026: Records read contracts
