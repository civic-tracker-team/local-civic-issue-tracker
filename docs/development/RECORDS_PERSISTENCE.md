# Records persistence

`RecordsFoundation` saves supplied meeting metadata and original document bytes. It retains agenda-item relationships, official source identifiers, observations, document versions and file references. Portal retrieval is handled separately by the acquisition service code.

## Database configuration

Records uses PostgreSQL with Spring Data JPA. Flyway applies versioned SQL migrations, and Hibernate validates the resulting schema. Runtime startup does not use JPA to create or alter tables.

Provision the records schema with separate migration and runtime roles. `infra/postgres/records-bootstrap.sql` takes the psql variables `migration_role`, `migration_password`, `runtime_role` and `runtime_password`. It creates new roles, so an existing installation needs equivalent grants rather than repeated role creation.

The migration role owns the schema and migration history. The runtime role can read and change records application data but cannot alter the schema, modify Flyway history or access another service's schema. OCR and model-serving processes receive no application database credentials.

| Setting | Purpose |
|---|---|
| `RECORDS_DB_URL` | PostgreSQL JDBC URL |
| `RECORDS_DB_USER` | Records runtime role |
| `RECORDS_DB_PASSWORD` | Runtime password |
| `RECORDS_MIGRATION_USER` | Schema owner used by Flyway |
| `RECORDS_MIGRATION_PASSWORD` | Migration password |
| `RECORDS_STORAGE_ROOT` | Persistent storage directory |
| `SERVER_PORT` | Optional fixed HTTP port |

Supply credentials and locations through external configuration. Storage must be private to records and support atomic moves within the configured directory. SQL stores relative, UUID-derived file keys. Original filenames remain metadata and never determine a physical destination.

## Application methods

`retain(SourceInput, InputStream)` saves an observation and returns the document's selected state. It commits before returning unless it participates in a caller's transaction. In that case, the caller must wait for its own commit before acknowledging success. The caller also closes the supplied stream.

`inspect` reads current metadata, while `inspectVersion` reads a particular retained version. `openOriginal` and `openVersion` return streams that the caller closes. These methods belong to records and are not cross-service Java interfaces. Consumers use the [HTTP read API](RECORDS_READS.md).

## Identity and versions

Official identifiers are matched within their portal, meeting and identifier family. A new identifier can reuse a document only under the scoped, unique SHA-256 rules in the [identity design](../engineering/design/RECORDS_IDENTITY.md). Ambiguous matches remain separate. The exact-URL fallback preserves query parameters and still has unresolved temporary-link cases.

Records computes SHA-256 while streaming the original bytes. Different bytes under a known identity create another retained version. If a later observation returns to previously retained bytes, records reuses that version and records the new observation. Equal bytes can still accompany changed metadata or relationships.

A newer authoritative source observation can advance current references. Older, equal-time or historical observations remain recorded without automatically replacing them. Observation times use PostgreSQL microsecond precision. Supplied meeting dates remain distinct from observation and publication times, and unknown timezones are not guessed.

A transaction-scoped advisory lock serializes matching within a portal meeting. Database constraints reject conflicting source keys, relationships and versions. Mutable metadata and current references also use optimistic concurrency checks.

## File and transaction failures

Files are staged, hashed, flushed and installed before SQL references commit. Interrupted staging is removed. If installation succeeds but the database transaction fails, a complete unreferenced file can remain. Automatic orphan deletion is not implemented.

This ordering prevents a successful metadata result from pointing to an incomplete file. It does not make SQL and the filesystem one transaction or guarantee survival of every machine power failure. Relocating the configured storage directory preserves references because SQL contains relative keys.

## Verification

`tools/verify-java.ps1` tests against an isolated PostgreSQL container, currently PostgreSQL 18.6. It provisions the roles and restricted schemas needed by permission tests, runs `mvn clean verify`, and removes its own container.

The tests cover identity matching, source history, version reversion, concurrent imports, permissions, rollback, interrupted writes, path escapes, relocation and separate-process retrieval. Manual runs need the same isolated database and storage setup. The tests write synthetic observations and intentional orphan fixtures, so they are unsuitable for an existing project dataset.
