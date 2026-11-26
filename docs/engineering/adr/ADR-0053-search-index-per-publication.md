# ADR-0053: Search a PostgreSQL full-text index built per publication

- **Status:** Proposed
- **Date:** 2026-11-25
- **Decider:** Michael Avila
- **Related requirements:** FR-02, FR-36, FR-37, FR-43, FR-44, FR-45; NFR-13; V-02, V-23, V-29
- **Related decisions:** ADR-0007, ADR-0008, ADR-0010, ADR-0017, ADR-0029, ADR-0051

## Context

T09 provides search over issues, records and meetings, with any combination of the three selectable. The Release 1 plan of November 18, 2026 limits it to basic keyword search with a type filter. Detailed matching and ranking are Michael-led (SRS v2.0 §6.1).

Search must answer from published results while a run is in progress (NFR-13). Under ADR-0051, records and analysis do not check publication membership, so public readers pin the coordinator's current manifest and filter against it themselves. Analysis also holds revisions that were never published. Kazim asked whether plain PostgreSQL full-text search is acceptable for search storage and left the choice to Michael.

## Decision

- **Storage.** Search owns a `search` schema with migration and runtime roles (ADR-0007, ADR-0029). PostgreSQL full-text search uses the `english` configuration. A stored weighted `tsvector` per row ranks labels above context and context above body text.
- **One index per publication.** A scheduled indexer polls the coordinator's current publication. For a new publication it reads only the meetings, exact analysis revisions and document versions in that manifest, then writes the whole index and moves the current pointer in one transaction. Older indexes are deleted. A failed or inconsistent read writes nothing, and the previous index keeps serving.
- **What is indexed.**
  - Issues are named by their latest mention among the published revisions.
  - Each prepared page or agenda section of a record is its own row, and results are grouped back into one hit per record.
  - A record is searched by its title alone when the preparation to index is ambiguous, and responses report the count.
- **Slim results.** The public route `GET /api/v1/search` returns kind, ID, label, date, location, plain-text snippet fragments and a few kind-specific details per hit. It also returns the publication ID, coverage, record types and per-type totals. The T10 routes load full pages through the owners.
- **Record titles.** The records version read now returns the portal title it already kept, so search and the T10 routes can label records.

## Alternatives considered

- **An in-memory index rebuilt from the manifest.** No schema is needed, but it rebuilds from every owner after each restart, and stemming, ranking and snippets would be hand-written.
- **Querying owner APIs at request time.** Owners don't offer text search, and membership filtering would repeat on every request.
- **A dedicated search engine.** OpenSearch or Lucene would add a runtime and operations work that the Release 1 capacity can't absorb, for a collection of about one month of meetings.
- **Returning full issue, record and meeting views.** That would duplicate T10's page assembly inside search and couple search to presentation.

## Consequences

- **Positive:**
  - Searches always see one complete publication and never wait for a run (NFR-13).
  - Membership is enforced once, at build time.
  - Restarts keep the index.
- **Negative:**
  - The search schema and roles add provisioning work.
  - Each new publication is reindexed in full, which suits Release 1 sizes but not a large archive.
  - Snippets come from PostgreSQL headlines, which can drop tag-like text.
  - Ranking uses `ts_rank` only, with no tuning against reviewed queries yet.
- **Follow-ups:**
  - Put the preparation ID in the manifest's source entries so record text is never ambiguous.
  - Agree the V-02 reference queries with known relevant records.
  - Run the T14 integration against published data.
  - Consider owner-side membership checks.

## AI assistance and provenance

Claude assessed the T09 scope from the SPMP, SRS v2.0, ADR-0051 and the open T08 and T11 pull requests, and proposed these choices. On November 25, 2026, Michael chose PostgreSQL full-text search per publication, slim results, exposing the record title, and building on `main` against the documented owner contracts with test doubles. Claude implemented them and drafted this record. Its status remains Proposed, and no integration with the live coordinator, records or analysis services is claimed here.
