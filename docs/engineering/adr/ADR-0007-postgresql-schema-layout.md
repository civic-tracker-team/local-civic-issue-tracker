# ADR-0007: Use one local PostgreSQL database with service-owned schemas

- **Status:** Accepted
- **Date:** 2026-10-29
- **Recorded:** 2026-11-01, retrospectively
- **Deciders:** Kazim Yigit Ablay
- **Related requirements:** LIM-06; supporting ownership constraint LIM-09

## Context

Records, analysis and the coordinator need persistent application state, but operating a separate database server for every service would add work to local development. The two developers expect to work with their own local instances.

Physical database arrangement and logical ownership are separate decisions. Sharing a database does not require analysis to write records tables, or search to query every schema directly. OCR and the model runtime do not need application schemas under the chosen inference boundary.

## Decision

Each local development environment will begin with one PostgreSQL instance and one application database, divided into service-owned schemas. Records, analysis and the coordinator retain their respective ownership boundaries.

Consumers access another owner's domain through service APIs. A shared server does not grant unrestricted cross-schema access. Search-owned persistence remains undecided, so this record does not create a search schema or choose an index store.

The arrangement is the initial local baseline, not a requirement that every future deployment use one server.

## Alternatives considered

The storage discussion retained separate per-service databases or instances as an alternative. That would permit stronger physical separation but add local provisioning and data-transfer work. The shared instance/database arrangement was selected explicitly for simplicity.

Unrestricted shared tables would weaken the separate ownership decision and are not the selected interpretation of a shared database.

## Consequences

- **Positive:** Developers have fewer database instances to provision and a coordinated dataset can use one application-database export.
- **Negative:** Services share database resources and availability. Schema changes, permissions and restoration still need coordination.
- **Follow-ups:** Define actual schema names, grants and migrations. No migration tool or performance guarantee is selected here. Later physical separation can preserve owner APIs while changing deployment details.

## AI assistance and provenance

Codex helped compare physical layout and ownership, then checked this record against LIM-06 and LIM-09. Kazim selected the single-instance arrangement. Its operational trade-offs are expected consequences, not benchmark results.

## References

- SRS v1.0
- ADR-0008: Service data ownership
- ADR-0012: Dataset snapshots
