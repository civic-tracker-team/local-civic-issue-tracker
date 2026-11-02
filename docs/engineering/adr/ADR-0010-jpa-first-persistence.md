# ADR-0010: Use JPA-first persistence for records and analysis

- **Status:** Accepted
- **Date:** 2026-10-29
- **Recorded:** 2026-11-01, retrospectively
- **Deciders:** Kazim Yigit Ablay
- **Related requirements:** LIM-08, LIM-09

## Context

Records and analysis will use Java/Spring Boot and need to persist associated domain data. Kazim preferred JPA, while asking whether that approach remained practical when analysis needs records data and search needs information from both owners.

The access review separated two questions: how an owner persists its domain, and how another service obtains that domain's data. JPA inside a service does not require consumers to share its entities or query its tables.

## Decision

Records and analysis will use Spring Data JPA as their primary domain-persistence approach. Specialized SQL remains available within an owner's boundary where useful. JPA-first does not mean that every operation must be expressed through object loading or repository methods.

Cross-service reads will follow owner APIs as recorded in ADR-0008. A service's JPA entities and persistence context are internal implementation details, not the serialized contract shared with consumers.

This record does not prescribe JPA for the coordinator's execution metadata, select a migration tool or establish search persistence.

## Alternatives considered

The review considered JPA alongside targeted SQL and evaluated the separate API-versus-SQL access question. The selected approach keeps JPA for ordinary domain persistence without rejecting lower-level SQL when a concrete operation warrants it.

The retained history does not establish a benchmark-based rejection of JDBC or a conclusion that JPA is universally preferable. No such claim is made.

## Consequences

- **Positive:** Ordinary persistence follows the preferred Java domain approach while specialized operations remain possible.
- **Negative:** Entity loading, transaction boundaries and query behavior still need attention. Repository abstractions do not remove bulk-processing or query-design work.
- **Follow-ups:** Design mappings and transactions against actual access patterns. Verify the selected framework/database combination during implementation. A JPA entity must not become a substitute for an explicit inter-service contract.

## AI assistance and provenance

Codex helped evaluate the JPA-first approach and its cross-service implications, then checked the record against LIM-08 and LIM-09. Kazim selected JPA and API-based access. No persistence implementation or performance result is asserted.

## References

- SRS v1.0
- ADR-0008: Service data ownership
- ADR-0011: Service communication
