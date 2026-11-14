# ADR-0022: Assign internal UUIDv4 identifiers

- **Status:** Accepted
- **Date:** 2026-11-08
- **Recorded:** 2026-11-13
- **Deciders:** Kazim Yigit Ablay
- **Related requirements:** DR-04, DR-05

## Context

Portal identifiers describe source objects, but the application also needs stable references for retained versions, artifacts and relationships. Reusing a portal identifier as every table key would tie those references to the portal's replacement behavior.

## Decision

Owning services assign UUIDv4 identifiers before persisting genuinely new domain objects, using `UUID.randomUUID()` in Java while retaining the stored identifiers of matched entities. Official source IDs remain separate, with their portal and relationship context.

Source matching and database constraints determine whether an object already exists, while UUID generation supplies an identifier without establishing chronology or reconciling independently imported databases.

## Alternatives considered

Portal IDs as application keys would combine source provenance with internal persistence identity. Database-generated numeric keys would require obtaining the key before connecting newly created objects. Deterministic IDs derived from URLs or hashes would depend on continuity rules that those values cannot establish alone.

## Consequences

- **Positive:** New objects can refer to one another before insertion, and portal changes do not require changing application references.
- **Negative:** Independent imports can assign different UUIDs to the same source object.
- **Follow-up:** Shared development snapshots preserve existing IDs. Database and source-key constraints remain necessary.

## AI assistance and provenance

Codex helped examine the identifier options and check this record against the selected approach. Kazim selected application-generated UUIDv4.

## References

- SRS v2.0
- ADR-0014: Portal meeting identity
- Records identity and versions
