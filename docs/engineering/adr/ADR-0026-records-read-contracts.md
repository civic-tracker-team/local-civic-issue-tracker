# ADR-0026: Separate metadata, prepared text and original downloads

- **Status:** Accepted
- **Date:** 2026-11-08
- **Recorded:** 2026-11-13
- **Deciders:** Kazim Yigit Ablay
- **Related requirements:** DR-03, DR-06, DR-08, LIM-09

## Context

Consumers need to inspect meetings and relationships without downloading every source file. Analysis also needs exact inputs, since following a mutable current document reference between requests can mix versions.

## Decision

Records exposes service-local DTOs for metadata and relationships, with prepared content and original bytes fetched separately for a selected version. Responses carry application UUIDs and source information without serializing JPA entities or physical storage keys.

Consumers keep exact version and preparation references while processing. Collections use bounded page/size requests against a fixed selection. PDF and HTML locations retain their own source forms, while text offsets use zero-based Unicode code points with an exclusive end against the retained segment.

Internal reads inspect retained processing data. Public reads will additionally enforce the publication view described in ADR-0017. Metadata reads do not imply authority to inspect unpublished data through public routes.

## Alternatives considered

Embedding all text and binaries in metadata would make ordinary inspection expensive. Shared JPA entities would expose persistence details across services. Repeated latest-version reads would be convenient but could change inputs during one operation.

## Consequences

- **Positive:** Consumers can request only the data they need and preserve exact evidence references.
- **Negative:** Clients make separate requests and must retain the references returned by metadata.
- **Follow-up:** Production consumer clients and public publication enforcement remain separate implementation work.

## AI assistance and provenance

Codex helped organize the API boundaries and check the code-point convention. Kazim selected separate metadata, text and original-file responses.

## References

- SRS v2.0
- ADR-0008: Service data ownership
- ADR-0011: HTTP service communication
- ADR-0017: Run publication
- ADR-0036: Prepared-content revisions
