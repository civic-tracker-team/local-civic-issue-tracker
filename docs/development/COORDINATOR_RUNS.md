# Coordinator runs

The coordinator admits one bounded date range, asks Records to discover and prepare its meetings, then analyzes them in date order. It stores progress in its own PostgreSQL schema. Original files and prepared text stay with Records; findings stay with analysis.

## Configuration

Provision `infra/postgres/coordinator-bootstrap.sql` with separate migration and runtime credentials. Supply its `migration_role`, `migration_password`, `runtime_role` and `runtime_password` parameters through psql. Flyway runs migrations as the migration role. JPA validates the schema, and application queries use the runtime role.

| Environment setting | Purpose |
|---|---|
| `COORDINATOR_DB_URL` | PostgreSQL JDBC URL |
| `COORDINATOR_DB_USER`, `COORDINATOR_DB_PASSWORD` | Runtime credentials |
| `COORDINATOR_MIGRATION_USER`, `COORDINATOR_MIGRATION_PASSWORD` | Flyway credentials |
| `SERVER_PORT` | HTTP port; the baseline otherwise selects an available port |
| `COORDINATOR_MAX_MEETINGS` | Required positive limit, with no default |
| `COORDINATOR_PREPARATION_PROFILE_ID` | Records preparation profile frozen at admission |
| `COORDINATOR_STALL_AFTER` | Positive ISO duration for the stalled indication, such as `PT1M` |
| `COORDINATOR_DRIVER_ENABLED` | Enable reconciliation; default `false` |
| `COORDINATOR_PUBLISH_AUTOMATIC` | Publish READY runs automatically; default `false` |
| `COORDINATOR_RECORDS_BASE_URL`, `COORDINATOR_ANALYSIS_BASE_URL` | Owner base URLs |
| `COORDINATOR_RECORDS_USERNAME`, `COORDINATOR_RECORDS_PASSWORD` | Records identity allowed to submit and read operations |
| `COORDINATOR_ANALYSIS_USERNAME`, `COORDINATOR_ANALYSIS_PASSWORD` | Analysis identity allowed to submit operations and read revisions |
| `COORDINATOR_RECORDS_TIMEOUT_SECONDS`, `COORDINATOR_ANALYSIS_TIMEOUT_SECONDS` | Required positive HTTP timeouts |

Configure incoming identities through `coordinator.security.maintainers.<name>` and `coordinator.security.publication-readers.<name>`. Values are bcrypt hashes with the `{bcrypt}` prefix. Names cannot contain a colon or belong to both groups. No default identity is enabled. Maintainers control runs and read publications. Publication readers can only read the two publication routes.

Use HTTPS except for loopback development. Forwarded headers do not make a remote HTTP caller local. Enable the driver on one coordinator process. Its delay is `coordinator.driver.delay`, default `PT5S`. This polls existing runs; it does not schedule new date ranges. Records and analysis must also have their workers enabled. Their setup is described in [Records acquisition](RECORDS_ACQUISITION.md), [preparation](RECORDS_PREPARATION.md) and [analysis operations](ANALYSIS_OPERATIONS.md).

## Start and watch

Authenticate with HTTP Basic. Before each POST, GET `/internal/v1/csrf`, keep its cookie, then send its `token` under the returned `headerName`. Fetch a fresh token for each write because authentication can clear the preceding cookie.

POST `/internal/v1/runs` with:

```json
{
  "runId": "<new UUID>",
  "sourceId": "<configured Records source>",
  "from": "<inclusive YYYY-MM-DD>",
  "to": "<inclusive YYYY-MM-DD>",
  "committeeIds": ["<official committee ID>"]
}
```

The response is 202 with a Location. Repeating equivalent input under that UUID returns the existing run. Changed input returns `RUN_ID_CONFLICT`. Another active, halted or READY run returns `RUN_ACTIVE` with its ID. Committee order is normalized before fingerprinting. A range overlapping published coverage is rejected. Discovery exceeding the configured limit halts rather than truncating the range. A discovery that finds no meetings halts with `EMPTY_DISCOVERY` instead of publishing coverage for the range. Check the source and committee IDs, then retry or abandon.

GET `/internal/v1/runs/{id}` or `/internal/v1/runs/active`. The view includes phase, meetings, current owner operation and status URL, halt details and waiting information. There is no active run before admission or after publication or abandon.

All Records work finishes before analysis starts. Input order follows Records' originals, and exact analysis request bytes are saved before submission. Priors include analyzed revisions from this run and the base publication with strictly earlier dates. Same-day meetings do not share priors. A cancelled meeting without originals is accounted for as CANCELLED. Other empty acquisitions become NO_RETAINED_ORIGINALS. A cancellation with retained originals is processed normally.

## Failure and recovery

| Waiting value | Meaning and response |
|---|---|
| `OWNER_BLOCKED` | Follow the owner status URL and its resume procedure. Resolve uncertain worker, OCR or model execution at the owner. |
| `STALLED` | The observed state and blocked ID have not changed for the configured interval. Check worker enablement and owner progress. This is advisory. |
| `OWNER_UNREACHABLE` | The owner could not supply a usable response. Restore connectivity or service availability; reconciliation tries again. |

Waiting never automatically skips a meeting or creates a replacement attempt. A coordinator restart reloads saved facts without a takeover confirmation. Owner uncertainty still requires the owner's stop confirmations. The coordinator has no owner-resume proxy.

A failed owner operation or invalid result halts the run. Inspect `halt.code`, `halt.operationId` and its advisory retry hint. A maintainer can use:

| POST route under `/internal/v1` | Body and effect |
|---|---|
| `/runs/{id}/retry` | `{"haltId": <current halt ID>}` creates the next attempt. Records receives the preceding operation as `replacesOperationId`. |
| `/runs/{id}/skip` | `{"haltId": <current halt ID>, "reason": "<explanation>"}` records the maintainer and reason, marks that meeting SKIPPED and continues. |
| `/runs/{id}/abandon` | `{"reason": "<explanation>"}` ends the run unpublished and frees admission. It does not cancel owner work. |

Retry and skip are guarded by the halt ID, so repeating an already-resolved command does not apply it twice. Skip requires a halted meeting; discovery and publication halts cannot be skipped. A discovery halt can be retried only before its meetings are saved. After that, retry returns `RUN_NOT_RETRYABLE` because saved meetings already name the discovery operation, so abandon the run and start a new one. Available pinned sources remain in a skipped entry, but it has no analysis revision and supplies no prior context. Skipping can leave gaps in later issue continuity. It is never automatic.

Some deterministic failures need correction or abandon. Records may reject a replacement of a successful preparation after a coordinator pin-join failure. A blocked analysis operation has no owner abandon route; follow its guide before starting work that needs its queue.

## Publish and read

When every meeting is accounted for, the run reaches READY and keeps the slot. POST `/internal/v1/runs/{id}/publish` to publish manually. With automatic publishing enabled, the driver calls the same method. Repeating publication returns the same publication ID.

Before the pointer transaction, the coordinator checks each newly analyzed revision's meeting ID, meeting date and evidence versions against the saved pins. A mismatch halts with `PUBLISH_CHECK_FAILED`. An unavailable owner returns 503 and leaves the run READY. The transaction writes the cumulative manifest and advances the current pointer only if the frozen base is still current. A pointer conflict rolls back the new rows and halts with `POINTER_MOVED`. Failed writes leave the earlier collection current.

T10 and T09 read GET `/internal/v1/publications/current`, which returns 404 `NO_PUBLICATION` before the first publication. Pin the returned ID across related reads using GET `/internal/v1/publications/{id}`. Manifests contain coverage ranges, meeting dispositions, exact analysis revisions and document/version pairs. Release 1 consumers enforce membership from these references. Published manifests are immutable; overlapping re-analysis and historical rebuilding remain deferred.

## Verification and contingency

Run `tools/verify-java.ps1` against its isolated database. Coordinator tests use loopback owners; they do not call a portal, OCR engine or model runtime. They cover operation recovery, publication validation, transactional rollback, permissions and the scheduled driver.

The planned time-box contingency replaces scheduling with a maintainer-triggered `advance` call. It was not activated. This implementation has no `/advance` route; disabling the driver only pauses reconciliation. The contingency requires the agreed implementation change before use.
