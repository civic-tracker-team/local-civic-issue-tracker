# ADR-0008: Keep domain ownership behind service APIs

- **Status:** Accepted
- **Date:** 2026-10-29
- **Recorded:** 2026-11-01, retrospectively
- **Deciders:** Kazim Yigit Ablay
- **Related requirements:** LIM-09, DR-06, DR-13

## Context

Analysis needs prepared records, and search is likely to need information owned by both records and analysis. A shared PostgreSQL database makes direct queries technically possible, but that does not resolve who controls each domain or how consumers remain independent of its persistence model.

Ownership should follow the services responsible for accepting and maintaining the data. Cross-service access should not silently become shared JPA entities or direct writes into another schema.

## Decision

Records will own official-source relationships, file references and metadata, and prepared readable content. Analysis will own evidence, developments, issues, associations and derived summaries, overviews and timelines. The coordinator will own durable run, progression and recovery information.

Analysis will obtain records data through records APIs. Search will obtain records and analysis data through their owning APIs. The coordinator controls progression through owner interfaces rather than writing their domain tables.

This establishes access direction without selecting endpoint shapes, pagination, synchronization or a search read model. Search-owned persistence remains open.

## Alternatives considered

The access review compared owner APIs with direct SQL, including explicitly granted SQL views. Direct reads could simplify some queries, but would make consumers depend on another owner's database representation. Owner APIs were selected as the planned approach.

Direct cross-service SQL remains a possible later change with an explicit rationale and ADR. It is not part of the current baseline simply because the services share one database.

## Consequences

- **Positive:** Each owner controls how its data is accepted and exposed. Consumers depend on contracts rather than foreign tables or persistence entities.
- **Negative:** APIs introduce transfer, query-composition and failure-handling work. Suitable bulk reads may be needed rather than many small calls.
- **Follow-ups:** Define contracts and access permissions. Do not infer that APIs alone solve consistency or that every search request must synchronously fan out to every owner.

## AI assistance and provenance

Codex helped evaluate JPA and cross-service access implications and checked the recorded ownership against the SRS. Kazim selected owner APIs. No direct-SQL exception or detailed search implementation was accepted.

## References

- SRS v1.0
- ADR-0007: PostgreSQL layout
- ADR-0010: JPA-first persistence
- ADR-0011: Service communication
