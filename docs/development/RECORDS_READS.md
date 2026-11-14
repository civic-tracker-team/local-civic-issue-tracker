# Internal records reads

The records API provides authenticated access to retained metadata, original files and prepared content. These routes expose processing data. They are separate from the planned public APIs, which will serve published results.

## Routes

| GET route | Response |
|---|---|
| `/internal/v1/documents/{id}` | Current version, source identifiers, contexts, URLs and retained version IDs |
| `/internal/v1/document-versions/{id}` | Exact document and artifact IDs, SHA-256, byte length, filename, source observations and sealed preparation IDs |
| `/internal/v1/artifacts/{id}/bytes` | Original bytes streamed as an attachment |
| `/internal/v1/document-versions/{id}/content` | Sealed prepared text and source locations |

IDs are internal UUIDs. The version list has a deterministic technical order, not a historical sequence. `firstObservedAt` means the earliest retained observation of those bytes. It does not substitute for an unknown official publication date. Historical versions retain their own URLs and artifact references.

A consumer selects a version and follows its artifact or preparation reference. Repeatedly following the document's current pointer could mix versions if an import occurs between requests. Prepared-content selectors and pagination are described in [record preparation](RECORDS_PREPARATION.md). Meeting reads are in [records acquisition](RECORDS_ACQUISITION.md).

## Authentication

Configure service identities in `records.security.callers`, `records.security.processors` or `records.security.maintainers`. Each value is an externally supplied password hash beginning with `{bcrypt}`. The client sends its username and original password through HTTP Basic authentication. Plaintext and `{noop}` configuration values are rejected.

An empty configuration grants no access. Read-only callers cannot submit processing or maintenance commands. Routes and methods without an explicit permission are denied. There is no browser login, and ordinary reads do not create session cookies. Processing commands retain CSRF protection.

Non-loopback requests require HTTPS as reported by the servlet connection. Forwarding headers alone do not satisfy that check. A reverse proxy therefore needs an explicit trusted deployment configuration, with internal routes kept out of public routing. `SERVER_ADDRESS` can bind local development to loopback.

## Consistent reads and downloads

Metadata is assembled in a read-only repeatable-read transaction. JPA entities and storage keys are not serialized. File streaming starts after the metadata transaction and does not hold a database connection throughout the transfer.

Before sending headers, records checks that the file exists and has the recorded length. Consumers can compare downloaded bytes with the version's SHA-256. The download route does not detect same-length corruption automatically. Downloads use a UUID filename, while the original source filename remains in metadata.

A transfer failure after headers have been sent is logged. It cannot be replaced with a JSON error response, so consumers must reject incomplete downloads.

## Errors and tests

Malformed IDs return 400, unknown objects return 404, invalid credentials return 401, denied requests return 403, and unavailable database or file reads return 503. Unexpected failures return a sanitized 500. Responses follow RFC 9457 and include a stable `code` and `correlationId`. They do not expose exception messages, credentials or physical locations.

`tools/verify-java.ps1` includes real HTTP tests for permissions, exact bytes, historical sources, missing and truncated files, concurrent reads, transport rules and error responses. Service classes keep HTTP handling, read transactions and file access separate so consumers do not depend on the persistence implementation.
