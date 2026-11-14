# Records discovery and acquisition

Discovery captures a selection of PrimeGov meetings and listed documents. Acquisition retrieves the available originals for one selected meeting, expands supported agenda attachments and saves exact version references. Preparation and publication are separate steps.

## Configure the source

Set up the [records database and storage](RECORDS_PERSISTENCE.md), then enable the worker with `records.operations.worker-enabled=true`. When the worker is disabled, commands are still saved but do not execute automatically.

Supply source profiles through external Spring configuration. For example:

```json
{
  "records.operations.worker-enabled": true,
  "records.portal.sources.sanbernardino": {
    "base-url": "https://sanbernardino.primegov.com",
    "allowed-hosts": ["sanbernardino.primegov.com", "pgwest.blob.core.windows.net"],
    "revision": "1",
    "request-interval-millis": 500,
    "connect-timeout-millis": 10000,
    "transfer-timeout-millis": 45000,
    "max-bytes": 134217728,
    "max-redirects": 5,
    "allow-loopback": false
  }
}
```

These values control network access and resource use. They are not performance guarantees. The delivery host in the example is used by inspected official generated-PDF redirects. Host approval is explicit and includes every redirect destination. Public sources use HTTPS on the standard port. Local fixture servers require the loopback option and their configured port.

Every request and redirect consumes a rate reservation. A valid `Retry-After` value from a 429 response delays later requests, while the current operation fails rather than entering an automatic retry loop. Identity encoding keeps byte lengths and checksums tied to the retained original.

`records.operations.poll-millis` defaults to 500, `max-page-size` to 100 and `max-discovery-years` to 20. Discovery saves its resolved source profile with its inputs. Changing that configuration requires a new discovery operation.

## Authenticate commands

Read-only identities use `records.security.callers`. Processing identities use `records.security.processors`, while `records.security.maintainers` also permits recovery commands. Each name belongs to one group and has an externally supplied `{bcrypt}` password hash.

Before a POST, an authenticated processing client requests `/internal/v1/csrf`. It retains the returned cookie and sends the token under the returned header name. Refresh the token after authentication changes or a CSRF rejection. An uncertain submission must retain its operation UUID when retried, so it cannot accidentally create duplicate work.

## API

| Method and route | Input or result |
|---|---|
| POST `/internal/v1/discovery-operations` | `operationId`, `sourceId`, `from`, `to`, `committeeIds`, optional `replacesOperationId` |
| POST `/internal/v1/acquisition-operations` | `operationId`, `discoveryId`, `meetingId`, optional `runId` and `replacesOperationId` |
| GET `/internal/v1/operations/{id}` | State, attempt and worker references, error code, notes and result URL |
| GET `/internal/v1/operations/{id}/meetings` | Frozen discovered meetings with `page` and `size` |
| GET `/internal/v1/operations/{id}/originals` | Exact acquisition receipts with `page` and `size` |
| POST `/internal/v1/operations/{id}/resume` | Previous attempt and worker references with stop confirmation |
| GET `/internal/v1/meetings?discoveryId={id}` | Meeting summaries from a successful discovery |
| GET `/internal/v1/meetings/{id}` | Retained meeting metadata, agenda items and document relationships |

Discovery dates are inclusive source-local calendar dates. Unknown or conflicting current dates cause a reported failure. Collection requests use a nonnegative `page` and a positive bounded `size`.

A saved command returns 202 and a Location header after its acceptance commits. Repeating an equivalent command returns its existing operation. Reusing the UUID with different inputs returns 409. Result collections become available after success. An empty successful discovery is distinct from a source failure.

Acquisition receipts contain the exact `versionId`, its `artifactId`, `observationId`, SHA-256 and byte length, as well as the document's selected `currentVersionId`. The imported version can differ from the current one. Source publication dates and retrieval observations remain separate.

## Changes and coverage

Rescans retrieve selected originals and compare bytes even when portal metadata appears unchanged. `CURRENT_ORIGINAL_CHANGED` reports a current-version transition. `SOURCE_METADATA_OR_MEMBERSHIP_CHANGED` reports changes in the selected meeting metadata or relationships. Neither note starts analysis automatically.

HTML verification tokens can change while meeting content stays the same. Records retains those byte versions without interpreting every byte change as a civic development.

The adapter follows supported official attachment-history links rather than arbitrary hyperlinks. Unsupported formats or hosts are reported. Missing unpublished document types do not fail acquisition of available inputs, but a selected published link that cannot be retrieved does fail. Missing HTML means item discovery is unavailable; malformed published HTML is a failure. Transcript discovery outside the supported official reference forms remains unverified.

## Recovery

A durable database slot permits one records operation to execute at a time. Downloads run outside SQL transactions. Retention, source observations, packet expansion and each completed item receipt commit in short transactions that check the execution attempt.

Resume reuses completed items and restarts incomplete transfers. A maintainer supplies `attemptId`, `previousWorkerId` and `previousWorkerStopped=true` only after confirming that the earlier execution stopped. Restarting the service does not establish that fact. Changed inputs require a linked replacement operation. Acquisition replacements keep the same meeting and run association.

A running attempt owned by the current instance cannot be resumed. A failed operation whose work has returned can be resumed in that instance. Preparation involving unresolved OCR additionally requires the confirmations in [record preparation](RECORDS_PREPARATION.md).

Tests cover source failures, duplicate commands, source changes, SQL rollback, interrupted transfers, packet reuse and separate-process recovery. Scheduling and cross-service run admission belong to the coordinator and are not implemented by this records worker.
