# Search service

Java package: `org.civictracker.search`.

Provides keyword search over the current published issues, individual records and meetings. A scheduled indexer reads the coordinator's current manifest and only the records and analysis data it names. It writes a PostgreSQL full-text index for that publication in its own `search` schema. `GET /api/v1/search` serves results with selectable types and the collection's coverage.

Setup, indexing rules and the response contract are in the [search guide](../../docs/development/SEARCH.md). The design decision is [ADR-0053](../../docs/engineering/adr/ADR-0053-search-index-per-publication.md). Ranking beyond PostgreSQL `ts_rank` is not tuned yet.
