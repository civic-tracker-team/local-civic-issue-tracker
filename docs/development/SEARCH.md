# Search

The search service finds issues, records and meetings in the current published collection. It keeps a PostgreSQL full-text index for each publication and serves one public route. Matching is keyword search with selectable result types. Detailed ranking remains open for later releases ([ADR-0052](../engineering/adr/ADR-0052-search-index-per-publication.md)).

## Configure database and owners

Provision a `search` schema with separate migration and runtime roles. `infra/postgres/search-bootstrap.sql` takes the psql variables `migration_role`, `migration_password`, `runtime_role` and `runtime_password`. Flyway grants the runtime role access to the index tables and nothing else.

| Environment setting | Purpose |
|---|---|
| `SEARCH_DB_URL`, `SEARCH_DB_USER`, `SEARCH_DB_PASSWORD` | Runtime database connection |
| `SEARCH_MIGRATION_USER`, `SEARCH_MIGRATION_PASSWORD` | Flyway migration role |
| `SEARCH_INDEXER_ENABLED` | `true` to poll the coordinator and build indexes; default `false` |
| `SEARCH_INDEXER_DELAY` | Delay between polls, default `PT30S` |
| `SEARCH_INDEXER_INITIAL_DELAY` | Delay before the first poll, default `PT0S` |
| `SEARCH_OWNER_TIMEOUT` | Timeout for each owner request, default `PT30S` |
| `SEARCH_COORDINATOR_BASE_URL`, `_USER`, `_PASSWORD` | A coordinator publication-reader identity |
| `SEARCH_RECORDS_BASE_URL`, `_USER`, `_PASSWORD` | A records read-only caller |
| `SEARCH_ANALYSIS_BASE_URL`, `_USER`, `_PASSWORD` | An analysis read-only caller |

Owner base URLs must use HTTPS, or plain HTTP to `127.0.0.1` or `[::1]`. They have no path, query or user information. Search does not follow redirects. Owner passwords are the plaintext halves of the bcrypt identities configured on each owner. With the indexer disabled, search starts without owner settings and serves whatever index it already has.

## Indexing

Each poll reads `GET /internal/v1/publications/current` from the coordinator. If the publication already has the current index, nothing happens. Otherwise search reads only what that manifest names:
- each meeting from Records `/internal/v1/meetings/{id}`
- each ANALYZED meeting's exact revision from analysis `/internal/v1/meeting-analyses/{revisionId}`
- each pinned document and version, and that version's prepared content

Records and analysis do not check publication membership in Release 1 (ADR-0051), so this restriction is the only membership boundary. Search never reads `/issues/{id}`, document current pointers or unpinned versions. A revision that does not match its manifest meeting fails the build, and so does one that cites a version outside the manifest.

A record's indexed text comes from the preparation that the publication's revisions cite for that version. If no revision cites the version and it has exactly one sealed preparation, that one is used. Otherwise the record is indexed by its title, agenda item and meeting only. Responses report how many records were indexed this way. A proposed follow-up is to put the preparation ID in the manifest's source entries.

The whole index and the current-pointer move commit in one transaction, and older indexes are then deleted. If an owner read fails or returns inconsistent data, nothing is written. Searches keep using the previous collection, and the next poll tries again. Failures are logged with a stable code such as `OWNER_UNAVAILABLE` or `PUBLICATION_MISMATCH`.

## What is searchable

| Type | Label | Also matched |
|---|---|---|
| Issues | The issue's name at its latest mention among the published revisions | Earlier names, and descriptions of developments linked to the issue |
| Records | The portal title of the pinned version | Agenda item, meeting title and each prepared page or agenda section |
| Meetings | The meeting title | Agenda item labels and the meeting's development descriptions |

Every published meeting is searchable, including cancelled, skipped and empty ones. A record without any issue link stays searchable (FR-37). Text uses PostgreSQL's `english` configuration, so word forms such as "approve" and "approved" match. Labels rank above context, and context ranks above body text.

## Search route

`GET /api/v1/search` is public and needs no identity. It accepts:

| Parameter | Meaning |
|---|---|
| `q` | Required, 1 to 200 characters. Words must all match. Quoted phrases, `or` and `-word` follow PostgreSQL `websearch_to_tsquery`. |
| `types` | `issues`, `records`, `meetings`, comma-separated or repeated. All three when omitted. |
| `limit` | Results per selected type, 1 to 50, default 10 |
| `offset` | Results to skip in each selected type, 0 to 1000, default 0 |

A response reads one index in a single snapshot:

```json
{
  "collection": {
    "publicationId": "…",
    "indexedAt": "2026-11-25T08:00:00Z",
    "coverage": [{ "from": "2026-10-01", "to": "2026-10-31" }],
    "recordTypes": ["Agenda", "Attachment", "Minutes"],
    "meetingCount": 3,
    "recordsSearchedByTitleOnly": 0
  },
  "query": { "q": "parking", "types": ["issues", "records"], "limit": 10, "offset": 0 },
  "totals": { "issues": 1, "records": 3 },
  "issues": [ … ],
  "records": [ … ]
}
```

Lists and totals appear only for the selected types (FR-36). `collection` lets the frontend state that results cover only the selected imported records and show the collection's date range and record types (FR-43, FR-44). Empty results include it as well. Coverage ranges carry dates, not committees, so they do not prove a committee had no meetings.

Each hit has `id`, `label`, `date`, `location`, `snippet` and `details`. `versionId` is set only for records. A hit's `snippet` is a list of `{text, match}` fragments of plain source text, and `match` marks matched words. Fragments are never markup, so render them as text. PostgreSQL's headline function can drop tag-like text such as `<b>` from a snippet. Snippets are previews: the record page shows the exact text.

| Type | `date` | `details` |
|---|---|---|
| Issues | Latest meeting mentioning it | `firstDate`, `latestDate`, `meetingCount`, `developmentCount`, `inferredOnly` |
| Records | Earliest meeting that includes it | `documentId`, `meetingId`, `meetingIds`, `meetingDate`, `meetingTitle`, `recordType`, `itemLabel`, `textSearched`, `issues` |
| Meetings | Meeting date | `officialId`, `committeeId`, `disposition`, `recordCount` |

A record's `issues` lists `{id, inferred}` for issues whose association evidence cites that version. `inferredOnly` marks an issue whose every published development link was inferred. The frontend should show the FR-45 caution for both. `location` is a record's best-matching page, such as "Page 2", or its agenda item.

Before the first index exists, the route returns 503 with `NO_PUBLISHED_INDEX`. Invalid requests return 400 with `INVALID_QUERY`, `INVALID_TYPE`, `INVALID_PAGE` or `INVALID_REQUEST`. Every other route and method returns 404. Responses use `Cache-Control: no-store` and RFC 9457 problems with `code` and `correlationId`. Search creates no sessions or cookies. Remote callers need HTTPS as reported by the servlet connection, so API routing needs a trusted deployment configuration.

## Verify

`tools/verify-java.ps1` provisions the search schema in its isolated database and runs the search tests. They build a real index from loopback stand-ins for the coordinator, records and analysis, using a synthetic publication. The tests cover:
- reference queries and every type combination (V-02, V-23)
- unlinked records, inferred links and cancelled meetings
- empty-result coverage (V-29)
- data outside the manifest, which must never be indexed
- failed rebuilds that keep the previous collection
- request validation and the runtime role's permissions
