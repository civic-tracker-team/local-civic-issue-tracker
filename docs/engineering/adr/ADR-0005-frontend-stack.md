# ADR-0005: Use React, TypeScript and Vite for the frontend

- **Status:** Accepted
- **Date:** 2026-10-29
- **Recorded:** 2026-11-01, retrospectively
- **Deciders:** Kazim Yigit Ablay
- **Related requirements:** LIM-03

## Context

The web frontend will present issues, meetings, individual records and source-supported results. It needs interactive views and a clear connection to the backend without exposing persistence internals to the browser.

Before selecting the frontend core, the project reviewed React/TypeScript/Vite examples and their development and API-integration implications. The examples helped establish the intended interface direction. They were not evidence of equal project size, effort or backend architecture.

## Decision

We will implement the web frontend using React, TypeScript and Vite. React provides the component basis, TypeScript expresses frontend data and component types, and Vite provides frontend development/build tooling.

The browser remains a service consumer rather than a direct database client. TypeScript types do not replace server-side validation or make serialized data inherently trustworthy. The frontend stack does not relocate backend domain or processing responsibilities into the browser.

This choice establishes the core only. It does not select a UI kit, routing library, cache library, timeline renderer, animation package or hosting platform. Detailed fetching and caching were deferred.

## Alternatives considered

The retained discussion supports selection of this core after reviewing concrete examples. It does not preserve a formal rejection of every alternative framework, so this record does not claim one.

A retrospective comparison with a primarily server-rendered interface would concern where view state and interactions reside. That comparison was not the recorded reason for selecting React and is not treated as an earlier rejected design.

## Consequences

- **Positive:** The chosen frontend has a defined component and tooling basis, separate from the Java services.
- **Negative:** Browser state, serialized API data and build tooling need their own checks. A typed frontend does not automatically stay synchronized with Java contracts.
- **Follow-ups:** Define view composition and API contracts as needed. The issue timeline represents a matter's evolution, not a social-media feed. Specific layout and component choices remain separate work.

## AI assistance and provenance

Codex supported example inspection and the development/integration explanation, then checked this record against LIM-03 and the confirmed scope. Kazim selected the core stack. No project-specific productivity result is asserted.

## References

- SRS v1.0
- ADR-0002: Service boundaries
- ADR-0020: Temporal data semantics
