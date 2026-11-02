# ADR-0011: Use HTTP/JSON with typed Spring service clients

- **Status:** Accepted
- **Date:** 2026-10-29
- **Recorded:** 2026-11-01, retrospectively
- **Deciders:** Kazim Yigit Ablay
- **Related requirements:** LIM-04, LIM-09

## Context

The Java services need to exchange data while remaining separate runtimes. Kazim wanted familiar Java interfaces and ordinary data objects where possible, drawing on prior OSGi experience. That preference must be reconciled with calls crossing a process and network boundary.

A typed client can make the caller's code resemble an interface invocation without pretending that both services share the same object or persistence session.

## Decision

For ordinary Java inter-service calls, we will use HTTP APIs with JSON structured payloads and typed Spring HTTP service clients backed by `RestClient`. The caller invokes a local client proxy. Request values are serialized, and response values become new DTOs/POJOs in the calling service.

File content may use binary or streaming transfer rather than JSON. The Python OCR and model-runtime contracts must fit their service boundaries, but their framework and client details are not selected here.

This decision does not establish endpoint fields, a shared-interface package, versioning, authentication, retry rules or the completion semantics of long-running work.

## Alternatives considered

The discussion compared traditional local Java/OSGi-style expectations with communication between separately running services. A local service reference does not supply a cross-process invocation contract by itself. Typed HTTP clients were selected to retain convenient call sites while making that boundary explicit.

The retained history does not contain a measured comparison with gRPC or a selected message broker. Neither is described as rejected on performance grounds.

## Consequences

- **Positive:** Java callers can use typed operations while services expose explicit serialized contracts.
- **Negative:** Network failures, timeouts and contract mismatches remain possible. A local-looking method call must not hide remote execution and recovery concerns.
- **Follow-ups:** Define errors, request/response contracts and long-operation behavior. A timeout does not establish that an operation had no effect or that remote inference stopped. This choice alone does not require Spring Cloud or a particular discovery mechanism.

## AI assistance and provenance

Codex helped explain the Java/OSGi comparison and organize the selected client approach, then checked it against the service-access decision. Kazim selected HTTP/JSON and typed Spring clients. Exact contracts remain unimplemented.

## References

- SRS v1.0
- ADR-0008: Service data ownership
- ADR-0009: Inference persistence boundary
