# Analysis operations

Analysis saves immutable meeting findings and runs a durable analyze-meeting operation over pinned Records inputs. The operation calls the frozen T06 evaluator. Model quality, cross-service scheduling and publication remain separate concerns.

## Configure database and storage

Provision an `analysis` schema with separate migration and runtime roles. `infra/postgres/analysis-bootstrap.sql` takes the psql variables `migration_role`, `migration_password`, `runtime_role` and `runtime_password`. It creates new roles; an existing installation needs equivalent grants rather than repeated role creation.

Flyway applies the analysis migrations using the migration role. Hibernate validates the schema at startup. The runtime role has full DML on operation tables and only SELECT and INSERT on findings tables. It cannot access Records tables, migration history or schema DDL.

| Environment setting | Purpose |
|---|---|
| `ANALYSIS_DB_URL` | PostgreSQL JDBC URL |
| `ANALYSIS_DB_USER`, `ANALYSIS_DB_PASSWORD` | Runtime credentials |
| `ANALYSIS_MIGRATION_USER`, `ANALYSIS_MIGRATION_PASSWORD` | Schema-owner credentials |
| `ANALYSIS_STORAGE_ROOT` | Private persistent attempt and import storage |
| `SERVER_PORT` | Optional fixed HTTP port |
| `ANALYSIS_WORKER_ENABLED` | `true` enables processing; defaults to `false` |

Database and storage configuration are required at startup. When the worker is disabled, commands are saved but do not execute automatically. `analysis.operations.poll-millis` defaults to 500. Keep the database and attempt storage together for provenance. A failed SQL save can leave unreferenced attempt files; automatic cleanup is not implemented.

## Configure Records and the model

Records credentials need access to operation receipts and prepared-content reads. Model and Records clients are created when processing requests them. Both use HTTPS, with explicit loopback HTTP allowed for local operation. They do not follow redirects or retry requests.

| Environment setting | Purpose |
|---|---|
| `ANALYSIS_RECORDS_BASE_URL` | Records origin, without a route prefix |
| `ANALYSIS_RECORDS_USER`, `ANALYSIS_RECORDS_PASSWORD` | Records processing identity |
| `ANALYSIS_RECORDS_TIMEOUT_SECONDS` | Positive request timeout |
| `ANALYSIS_MODEL_BASE_URL` | Model-runtime origin |
| `ANALYSIS_MODEL_PATH` | Model file available to analysis and matching the runtime's reported path |
| `ANALYSIS_MODEL_SHA256` | Pinned model-file SHA-256 |
| `ANALYSIS_MODEL_RUNTIME_BUILD` | Pinned runtime build |
| `ANALYSIS_MODEL_TOKEN` | Optional runtime bearer token |
| `ANALYSIS_MODEL_TIMEOUT_SECONDS` | Positive request timeout |
| `ANALYSIS_MODEL_OUTPUT_TOKENS` | Output reservation; defaults to 8192 |
| `ANALYSIS_MODEL_SEED` | Sampling seed; defaults to 1 |

`analysis.records.page-size` defaults to 100. Model preflight verifies the local file hash, runtime identity and available context before completion is sent. The [model evaluation guide](MODEL_EVALUATION.md) describes runtime setup and admission requirements.

The R1 sampling defaults are temperature 0.7, top-p 0.8, top-k 20, min-p 0, presence penalty 1.5 and repeat penalty 1.0. Their override settings are `ANALYSIS_MODEL_TEMPERATURE`, `ANALYSIS_MODEL_TOP_P`, `ANALYSIS_MODEL_TOP_K`, `ANALYSIS_MODEL_MIN_P`, `ANALYSIS_MODEL_PRESENCE_PENALTY` and `ANALYSIS_MODEL_REPEAT_PENALTY`. This profile is not a model-quality approval.

The importer and operation use one fingerprint function over seed, sampling, output limit, context, model hash, runtime build and reasoning mode. Machine paths are excluded. Moving an otherwise identical model does not change its fingerprint.

## Authenticate internal requests

Supply service identities through external Spring configuration. Each name belongs to one group and uses a `{bcrypt}` password hash:

| Configuration group | Permissions |
|---|---|
| `analysis.security.callers` | Read operation status, saved findings and issue history |
| `analysis.security.processors` | Read and submit analysis commands |
| `analysis.security.maintainers` | Read, submit and resume operations |

No identities are granted by default. Before each POST, fetch `GET /internal/v1/csrf` using a processing or maintenance identity. Retain the cookie and send the returned token under the returned header name. Fetch a fresh token for the next write. Remote callers require HTTPS; forwarding headers alone do not grant the loopback exception.

## Submit and read an operation

| Method and route | Input or result |
|---|---|
| POST `/internal/v1/analysis-operations` | `id`, `selection`, `priorRevisionIds` |
| GET `/internal/v1/analysis-operations/{id}` | State, attempt and worker IDs, blocked attempt, error code, attempt count and result revision |
| POST `/internal/v1/analysis-operations/{id}/resume` | Attempt and worker IDs with explicit stop confirmations |
| GET `/internal/v1/meeting-analyses/{revisionId}` | One immutable `SavedFindings` revision |
| GET `/internal/v1/issues/{id}` | The same `IssueHistory` returned by `FindingsStore.issueHistory` |
| GET `/internal/v1/csrf` | CSRF header name and token |

The command's `selection` contains `discoveryId`, `acquisitionId`, `preparationOperationId`, `meetingId`, `officialId`, `meetingDate`, `title` and `inputs`. Every input pin contains `acquisitionItemId`, `documentId`, `versionId`, `artifactId`, `sha256`, `byteLength`, `preparationId` and `profileFingerprint`. Take these values from successful [Records receipts](RECORDS_ACQUISITION.md) and [preparation results](RECORDS_PREPARATION.md). Analysis verifies complete pinned coverage before inference.

`priorRevisionIds` explicitly selects earlier saved revisions. It is an empty list for the first meeting. Unknown, duplicate, same-day and later revisions return 400 without creating an operation. Analysis builds the model-facing prior labels itself; the command does not supply `priorIssues`.

An accepted command returns 202, a status Location and `Cache-Control: no-store`. The same UUID and canonical command return the existing operation. Changed input under that UUID returns 409. One durable slot admits one operation at a time.

Saved findings contain ordered developments, issue mentions and associations. Each development retains its reply kind (`PROPOSAL`, `ACTION`, `EARLIER_ACTION` or `UNCLEAR`), description, status, dates and exact evidence. Kind is checked against the accepted reply rather than inferred from status. Evidence retains source identities, code-point offsets, `unitOrdinal`, `segmentOrdinal` and locator. `segmentOrdinal` is the zero-based position among the unit's selected segments in input order. Unselected extraction alternatives do not create gaps, and evidence cannot cite them. Two spans are adjacent lines when they share a version and unit ordinal and their segment ordinals differ by exactly one. T08 can derive development order from its list position and group associations by development ID.

Prior links reuse saved issue UUIDs. Unlinked mentions create new issues. Equal-basis links resolving to the same development and issue merge with ordered unique evidence. Conflicting bases reject the whole save. A revision lists each issue once using its first mention's name; issue history retains every mention and uses the latest name across its ordered revisions.

## Handle failure and resume

| Outcome | Durable result |
|---|---|
| Validated success | Findings and `SUCCEEDED` commit together; the slot is released |
| `INVALID_OUTPUT`, `CAPACITY_REJECTED`, `RUNTIME_FAILED` | `FAILED`, no findings and the slot released |
| Deterministic save rejection | `FAILED` with its fixed reason, such as `CONFLICTING_LINK_BASIS`, `EVIDENCE_SOURCE_MISMATCH`, `ATTEMPT_INPUT_CHANGED`, `ARTIFACT_HASH_MISMATCH` or `FINDING_KIND_MAPPING_MISMATCH` |
| Other post-success save failure | `FAILED` with `SAVE_FAILED` |
| Records input or client configuration cannot be read | `FAILED` with `INPUT_READ_FAILED` |
| Uncertain or unreconciled execution | `RUNNING`, a blocked attempt and the slot retained |

There is no automatic retry or takeover. Restarting the service does not prove that earlier work stopped. A foreign worker's running attempt remains blocked.

If the database is unavailable while the worker records an outcome, the operation stays `RUNNING` with no blocked attempt, and the log shows "Analysis worker cannot reconcile durable state". This looks like a long model call. After database access returns, restart the service to give the worker a new ID, then resume the operation with the stop confirmations below.

After confirming that the previous worker stopped, a maintainer sends its `attemptId`, `previousWorkerId` and `previousWorkerStopped=true` to the resume route. A running or uncertain attempt also requires `previousRuntimeStopped=true`, after the runtime has been checked. A failed, returned attempt does not require that runtime confirmation. A mismatched attempt, an active current worker or another occupied slot returns 409.

Resume returns the operation to `ACCEPTED`. Its next claim receives a new attempt ID and a new `operations/<id>/attempt-<n>/` directory. Earlier files remain unchanged. Status errors use fixed codes and correlation IDs, without exception details or rejected path text.

A maintainer can resume a blocked operation but cannot abandon it. Resume preserves the original acceptance time, so that operation runs again next. If an input repeatedly ends in uncertain execution, it blocks the queue until someone edits the database. An abandon route that marks the operation `FAILED` and frees the slot is planned if time allows.

## Import a saved trial

The developer command starts the database context without an HTTP server:

```sh
java -jar services/analysis-service/target/analysis-service-0.1.0-SNAPSHOT.jar import-trial TRIAL_DIR
```

It requires a `SUCCESS` receipt at `grounded-findings-3`. It checks input, model-input and candidate hashes, then reinterprets the raw reply and requires exact candidate equality. It retains `receipt.json`, `input.json`, `model-input.txt`, `candidates.json`, `attempt.json` and `preflight.json` under `imports/<input-sha256>-<candidates-sha256>/`. Only then does it save an `IMPORTED` revision and print its UUID. Repeating the same input and candidate artifacts returns that revision. The lookup, lock and storage key all use both hashes, keeping different meetings separate even when both have empty findings. The import command forces the worker off, including when service configuration enables it.

Standalone imports require empty prior context because a trial alone has no saved label-to-UUID mapping. Use the operation with `priorRevisionIds` for issue continuity. The existing `evaluate CONFIG OUTPUT ADMISSION` command remains a fallback for producing trials; its output directory must be new. Importing does not start inference or publish anything.

The receipt does not cover `preflight.json`, so an imported revision's profile fingerprint comes from an unverified file. Import only trial folders you produced and have not edited. The analyze operation is not affected: it writes its own preflight and checks the runtime identity against its configuration.

Reimporting the same input with identical findings returns the existing revision even if the second trial used a different seed or profile. The second trial's files and fingerprint are not stored.

## Verify

`tools/verify-java.ps1` provisions isolated PostgreSQL roles and storage and runs the Java checks. The tests cover permissions, transactional findings, all four kinds, prior context, operation outcomes, explicit recovery, HTTP permissions, CSRF and safe errors. Operation tests use loopback Records fixtures and a fake model.

The gated `t07.importReplayDir` test imports saved development trials when that property is supplied. It is skipped when the artifacts are unavailable. Automated validation does not establish semantic support. Prior-issue linking by the model still needs the separate development check before the full run; T08 adaptation and coordinator publication are separate work.
