# ADR-0002: Refine the service boundaries around records and analysis

- **Status:** Accepted
- **Date:** 2026-10-26
- **Recorded:** 2026-11-01, retrospectively
- **Deciders:** Kazim Yigit Ablay
- **Related requirements:** LIM-02, LIM-04, FR-13, FR-15, DR-13

## Context

The microservices and MVC direction predates SRS v1.0 and the service-boundary topic was introduced in ADR-0001. Later design work clarified which responsibilities should stay together and gave pipeline control an explicit owner. This record captures that refinement without changing ADR-0001's historical status.

Records, extraction, model inference and user-facing queries have different responsibilities. However, splitting every derived output into another service would add contracts before we have a concrete reason for them. Interactive browsing and search must also remain separate from the duration of processing.

## Decision

We will retain separate records, OCR, search, analysis and model-runtime responsibilities, a web frontend and API routing. A separate pipeline coordinator microservice will own scheduling, run admission, progression and recovery coordination.

Linking, summaries, issue timelines and meeting overviews will remain internal responsibilities of analysis for now. Records owns acquisition and prepared content. OCR and model serving perform requested inference. MVC separates presentation, request handling and domain/data responsibilities without making each MVC role a separately deployed service. API routing does not select a gateway product or an additional deployment.

Where the conditional category feature is included, assignment stays within existing analysis rather than adding a classifier service.

## Alternatives considered

The PDR comparison included two backend services, a more finely divided backend and a modular monolith. Two services offered fewer interfaces, but Kazim preferred separate OCR and search responsibilities. A service per feature added unnecessary interfaces for closely related analysis. A monolith would change the existing microservices constraint. Later discussion selected a separate coordinator rather than leaving orchestration as a records-service background responsibility.

## Consequences

- **Positive:** Responsibilities and ownership are explicit, and related analysis can evolve without another service boundary.
- **Negative:** Separate services require contracts, integration tests and failure handling. Linking and summaries remain coupled at deployment level.
- **Follow-ups:** Define deployment and API details. This split does not prove resource isolation, response time or scalability. Coordination and publication are developed in ADR-0015 and ADR-0017.

## AI assistance and provenance

Codex helped organize the service comparison and check this record against the SRS and confirmed design history. Kazim selected the boundaries. The record does not assert a separate review of every detail by Michael.

## References

- SRS v1.0
- ADR-0015: Chronological coordination
- ADR-0017: Run publication
