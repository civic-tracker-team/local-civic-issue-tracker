# ADR-0029: Use owner-managed Flyway migrations and restricted runtime roles

- **Status:** Accepted
- **Date:** 2026-11-08
- **Recorded:** 2026-11-13
- **Deciders:** Kazim Yigit Ablay
- **Related requirements:** LIM-06, LIM-08, LIM-09

## Context

Services share one PostgreSQL database during local development but retain separate ownership. Schema changes need a reproducible history without giving ordinary service execution authority to alter any schema it can reach.

## Decision

Each owner keeps versioned Flyway SQL migrations in its module and its migration history in its own schema. JPA validates the migrated schema at startup, so schema changes remain explicit in the migration history rather than occurring through automatic entity mapping updates.

Database provisioning creates separate migration and runtime roles. The migrator owns the schema and history. Runtime grants cover application data but exclude schema alteration, migration-history changes and another service's tables. Records, analysis and coordinator retain separate schema boundaries. OCR and model serving have no application schema or SQL credentials.

## Alternatives considered

Automatic JPA schema updates would hide important changes from an explicit migration history. A single unrestricted application role would simplify setup but weaken ownership. A separate database per service would provide stronger physical separation at additional local setup cost.

## Consequences

- **Positive:** Schema changes are repeatable and runtime permissions reflect service ownership.
- **Negative:** Provisioning and migrations need separate credentials and coordinated deployment.
- **Follow-up:** Each owner's migrations and permissions need verification against the real PostgreSQL setup.

## AI assistance and provenance

Codex assisted with the migration and role comparison and checked the records implementation. Kazim accepted Flyway, owner schemas and restricted runtime permissions.

## References

- SRS v2.0
- ADR-0007: PostgreSQL schema layout
- ADR-0008: Service data ownership
- ADR-0010: JPA-first persistence
