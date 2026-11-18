# Model evaluation

The analysis JAR includes developer commands for T06. Normal startup still runs the Spring HTTP baseline. These commands read records and write private experiment files. They do not create civic issues or publish results.

## Prepare an evaluation

Use a dedicated local llama.cpp server with one slot, context shifting disabled and reasoning disabled for the initial configuration. Pin its binary, model artifact, quantization and launch arguments in the private experiment record. Finish and stop OCR before starting the model. An authenticated runtime is supported through a bearer-token environment variable.

Start inference without application database credentials or the records service's environment configuration. The runtime receives model inputs and its own service credential, while analysis retains records access.

Acquire and prepare the complete selected meeting through records first. Freeze its discovery, acquisition and preparation receipts. Keep source inputs, model files, configurations, references and outputs in ignored private storage. Preserve a separate final-evaluation selection before tuning development prompts.

The configuration has these fields:

| Field | Value |
|---|---|
| `recordsBase` | Records origin, using HTTPS or explicit loopback HTTP |
| `recordsUser`, `recordsPasswordEnv` | Internal read-capable user and name of its password environment variable |
| `recordsTimeoutSeconds`, `pageSize` | Request timeout and content page size within the records limit |
| `selection` | Frozen selection described below |
| `modelBase`, `modelTokenEnv` | Runtime origin and bearer-token environment variable, or null for a runtime without authentication |
| `modelTimeoutSeconds` | Bounded request timeout, including inference |
| `modelPath`, `modelSha256`, `runtimeBuild` | Local artifact, exact SHA-256 and `/props` build identity |
| `outputTokens`, `seed` | Output reservation and experiment seed |
| `sampling` | Required object with `temperature`, `topP`, `topK`, `minP`, `presencePenalty` and `repeatPenalty` |

`selection` contains `discoveryId`, `acquisitionId`, `preparationOperationId`, `meetingId`, `officialId`, `meetingDate`, `title`, `inputs` and `priorIssues`. Each input contains `acquisitionItemId`, `documentId`, `versionId`, `artifactId`, `sha256`, `byteLength`, `preparationId` and `profileFingerprint`. Copy these values from the successful owner receipts. `priorIssues` may be empty. Each prior issue requires `id`, `description`, `reviewedThrough` and `snapshotSha256`. Its reviewed-through date must precede the evaluated meeting.

Supply every sampling field explicitly. The comparison profiles are temperature 0.2, top-p 0.95, top-k 40, min-p 0.05, presence penalty 0 and repeat penalty 1 for the earlier effective baseline, and 0.7, 0.8, 20, 0, 1.5 and 1 for Qwen's non-thinking profile. These are experiment profiles, not production defaults. Analysis sends `repeatPenalty` as llama.cpp's `repeat_penalty` and rejects a completed response whose effective settings differ from the request.

## Run

```sh
java -jar services/analysis-service/target/analysis-service-0.1.0-SNAPSHOT.jar evaluate CONFIG_JSON RUN_OUT ADMISSION_FILE
```

The output directory must be new. Its parent and the admission file's parent must already exist. Use the same admission file for every experiment sharing a runtime. Credentials come from the named environment variables and are not copied into receipts.

The command verifies the model digest and reads every selected content page. It counts the actual rendered prompt, including instructions, input and the output schema. Generation uses those exact token IDs. A capacity rejection makes no completion request. Output exhaustion and invalid candidate JSON are failures, even when part of the response looks usable. A structurally successful result still needs human source review. Exit code 2 reports a completed evaluation with a non-successful outcome.

`receipt.json` records the run state, contract revision and detail, requested sampling, and hashes of input, messages, schema and any candidate bytes. `preflight.json` contains the rendered prompt, exact tokens, context and runtime identity. `attempt.json` retains the raw runtime response, effective sampling and termination metadata.

`citation-index.json` maps global lines to units, permanent source identities and exact code-point spans. `model-input.txt` contains the selected text shown to the model, with document and section headers and addresses `L1` through `LN`. Blank lines retain addresses. `model-reply.json` saves a parsed answer. `resolution.json` records each raw citation's spans or problem. `candidates.json` is saved when interpretation reaches canonical validation, even if that validation fails. `validation.json` records typed problem codes and locations. An unresolved range prevents canonical output for the whole reply.

## Read the frozen contract

The T06 contract is `grounded-findings-3`, frozen with the final instruction wording of November 18, 2026. It was developed on two development meetings and was not tuned beyond them. This is a feasibility result, not a production model or profile choice.

The schema places `issues` before `developments`. Issues have `id`, `matterType`, `name` and `priorIssueId`. Developments have `evidence`, `finding`, `does` and `links`. A citation contains only inclusive `firstLine` and `lastLine` global bounds. The model supplies no quotations, permanent UUIDs or evidence identities. Java resolves exact spans without splitting, clamping, repairing or substituting a range.

Java renders descriptions from the finding and `does`, and derives statuses and date roles:

| Kind | Model supplies | Java derives |
|---|---|---|
| `PROPOSAL` | `proposedBy`, `planned` | `PROPOSED`, meeting date and any planned dates |
| `ACTION` | `actedBy`, `planned` | `RECORDED`, occurrence and meeting dates equal to the input meeting date, plus any planned dates |
| `EARLIER_ACTION` | `actedBy`, `occurred`, `planned` | `RECORDED`, the stated earlier occurrence, meeting date and any planned dates |
| `UNCLEAR` | `planned` | `UNCERTAIN`, meeting date and any planned dates |

Single day, month and year values use `value`. Date ranges use `start` and `end`. Unknown dates omit all three. Each model-stated date retains `precision` and `approximate`. Java supplies its role and validates the calendar values. The packaged schema and worked examples define the full wire format.

Kazim decided T5 and T6 on November 17, 2026. T5 includes agenda recommendations to receive a report and provide direction as `PROPOSAL` developments. T6 treats collective phrases as broad subjects rather than issues. Broad grouping belongs to the flat categories under C-49 through C-51, outside T06. These are Kazim's decisions, without a claim of team or instructor approval.

Links with the same issue and basis within a development merge into one canonical association, preserving the first-use order of exact evidence spans. Each later link gets `LINKS_MERGED`. Different bases remain separate and fail as `DUPLICATE_ASSOCIATION`. Raw links and traces remain available.

Every `Problem.Code` invalidates output except `UNUSED_ISSUE` and `LINKS_MERGED`. For example, `MALFORMED_REPLY` rejects invalid shape, `RANGE_CROSSES_UNITS` rejects cross-unit ranges, and date or source-integrity codes reject inconsistent candidates. The [T06 design](../engineering/design/T06_DESIGN.md#citations-and-validation) lists the full code catalog. An unresolved outcome reports citation root causes without errors from partial candidates. Unused issues are omitted from canonical candidates but retained in the raw reply.

Current receipts write `unit-6c-synthetic-wording`. Receipts from the final development trials retain `unit-6c-synthetic-wording-and-name-coverage`. Only the detail label changed. Prompts, schemas and behavior are identical.

Worked examples use synthetic text and line and alias numbers from 90001. Instructions use no concrete live citation numbers or wording copied from development meetings beyond a fixed allowlist of standard agenda boilerplate. Before trials, a private check compares the full system prompt with development inputs for shared four-word sequences. It uses lowercase Unicode letter and digit tokens, no length filter, and a fixed boilerplate allowlist. Other matches require a meaning-preserving rephrasing or a stop for review. The allowlist stays fixed.

If the completion response is lost or does not confirm termination, the admission file remains. Confirm that the earlier inference has stopped before removing that file or starting replacement work. A new output directory is not proof of termination. The command never kills another process.

## Summarize saved trials

```sh
java -jar services/analysis-service/target/analysis-service-0.1.0-SNAPSHOT.jar summarize SUMMARY_OUT TRIAL_DIR...
```

Supply a new output directory and the saved trial directories to compare. The command starts neither Spring nor inference. It writes `summary.json` and `summary.md` without changing the trials.

| Table | Contents |
|---|---|
| Trials | Revision, sampling, state, token counts, canonical counts, kinds, date roles, developments without links, and raw key order compared with the saved schema |
| Citations | Development index, raw citation path, resolved unit, line bounds and lexical overlap |
| Links | Each v3 raw link before merging, its issue name and lexical name coverage |

Overlap is the share of raw `does` tokens found in the cited spans. Name coverage is the share of raw issue-name tokens found in a raw link's evidence, before merging. Both use sets of lowercase Unicode letter and digit tokens of at least three code points, tokenized separately per span. Name coverage prints `n/a` when the name has no such tokens or any link evidence is unresolved. Values use two decimals.

Both columns are lexical diagnostics, with no threshold or effect on acceptance. High coverage does not establish support. Short fragments and umbrella names can have high coverage, while an official alias can have low coverage. Missing canonical output stays unavailable. It is not counted as a valid empty reply.

Historical quotation-shaped trials remain readable by `summarize`. The current scorer cannot re-score those candidate files after the span representation change. Keep earlier trials unchanged and use their matching historical code when necessary. Offline replays establish span and diagnostic consistency, not new quality scores.

## Known limitations

The frozen development findings still require human review:

- Agenda-only material can produce umbrella or combined issue names.
- Different official names for one project can become separate issues.
- Link evidence can start mid-sentence or span an entire compound motion. Read the development evidence as well as the link evidence.
- Meetings with minutes can still omit agenda proposals.
- Staff-report background did not yield `EARLIER_ACTION` findings. Recall remains unscored until references are approved.
- Correctly resolved citations can fail to support their claims. Superseded-motion wording and unsupported program links occurred in earlier trials. They did not recur in the frozen arm, but the contract does not rule them out.
- A `STAFF` proposer can be inferred from a matching staff report rather than the cited agenda passage.

Line resolution establishes where the text came from. It does not establish those semantic judgments or resolve issue aliases across meetings.

## Review and score

Compare prepared text with the original source pages before approving references. Review action status, date precision, specific issue identity, conflicting accounts and unsupported associations. Exact source-range resolution does not prove semantic support.

A reference file contains `split`, `inputSha256`, `reviewer`, `secondReviewer`, `approved` and `associationIds`. The split is `DEVELOPMENT` or `FINAL`. Reference IDs identify expected supported associations in the frozen scope. Keep unresolved cases outside the approved set until the team resolves them.

Keep each reference ID's development, particular issue, original source pages and prepared-text passages in the accompanying annotation notes. An ID list alone does not define ground truth.

A judgment file contains `inputSha256`, `reviewer` and `matches`. Each match contains `developmentId`, `issueId`, `referenceId`, `judgment` and `evidenceSupported`. Judgments are `CORRECT`, `INCORRECT` or `UNRESOLVED`. A correct association identifies one reference ID. Incorrect associations use null. Every returned association needs one resolved judgment before scoring, and a reference cannot match multiple predictions.

```sh
java -jar services/analysis-service/target/analysis-service-0.1.0-SNAPSHOT.jar score RUN_DIR REFERENCES_JSON JUDGMENTS_JSON METRICS_OUT
```

The scorer requires approved references, distinct reference reviewers, matching input hashes and complete judgments. It reports raw counts, precision, recall, evidence presence and evidence support. Zero denominators produce null values. Failed or uncertain generations cannot be scored as empty findings. Parsed but invalid output keeps its failure state alongside any reviewed diagnostic metrics.

Numerical acceptance targets follow development findings and must be agreed before final evaluation. Runtime capacity, automated validation and human quality acceptance are separate results.
