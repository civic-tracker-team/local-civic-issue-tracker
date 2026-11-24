# Model feasibility evaluation

T06 evaluates local whole-meeting inference before the team adopts a production model or profile. Analysis owns the developer evaluation runner. Records remains authoritative for originals and immutable prepared text. The evaluator writes attempt files; T07 now wraps it with immutable findings persistence and a durable internal analyze-meeting operation.

## Frozen contract

The T06 contract is frozen at `grounded-findings-3` with the final instruction wording of November 18, 2026. It was developed on two development meetings and was not tuned beyond them. This is a feasibility result, not a production model or profile choice. Final evaluation and reference approval remain separate.

`ModelContract.request` builds the input, messages and schema together. `ModelContract.interpret` parses the reply, resolves citations and validates candidates. It returns a typed malformed, unresolved or interpreted outcome.

Each request contains a whole meeting and any frozen earlier issue context. Records reads check owner receipts and retrieve every selected content page. Future issue context is rejected. Selected text stays complete, including blank lines. Capacity failures do not trigger source dropping, summaries or automatic chunking.

The model sees plain text with document and section headers. Every selected line has a global address, `L1` through `LN`, in input order. The index retains each line's unit, permanent source identities, exact text and code-point positions. Display escaping changes neither retained text nor source identity.

The llama.cpp adapter renders the loaded template, obtains token IDs and submits those same IDs for completion. Preflight reserves output capacity against the effective context. The adapter checks model and runtime identity, input count, termination and effective sampling. Truncation, exhausted output and uncertain execution remain failures. OCR stops before model trials use the GPU. The runtime has no application SQL access or authority to publish results.

## Findings and canonical data

The schema orders issues before developments. Each issue has `id`, `matterType`, `name` and `priorIssueId`. Matter types describe specific projects, proposals, policies, properties, named programs or other particular civic matters. Model-written names are not stable identities.

Each development has `evidence`, `finding`, `does` and `links` in schema order. The model supplies a verb phrase in `does`. Java combines it with the finding kind and actor to render the description, status and dates.

| Finding | Model fields | Status | Dates supplied by Java |
|---|---|---|---|
| `PROPOSAL` | `proposedBy`, `planned` | `PROPOSED` | Meeting date and any planned dates |
| `ACTION` | `actedBy`, `planned` | `RECORDED` | Occurrence and meeting dates equal to the input meeting date, plus planned dates |
| `EARLIER_ACTION` | `actedBy`, `occurred`, `planned` | `RECORDED` | Model-stated occurrence, meeting date and planned dates |
| `UNCLEAR` | `planned` | `UNCERTAIN` | Meeting date and any planned dates |

The proposer enum excludes the Council as a collective actor. A recommendation addressed to Council does not establish who proposed it. Day, month and year dates use one value. Ranges use two endpoints, while unknown dates have neither. Java checks calendar validity and ordering. For an occurrence date, the earliest allowed day must not follow the source meeting date. A month starts on its first day, a year on January 1, and a range at its start. Unknown dates remain unknown. `EARLIER_ACTION` represents reported history, but its inclusion in scored reference scope remains open.

Kazim made two scope decisions on November 17, 2026. T5 includes agenda recommendations to receive a report and provide direction as `PROPOSAL` developments. T6 treats collective phrases as broad subjects rather than issues. Broad grouping belongs to the flat categories under C-49 through C-51, outside T06. These are Kazim's decisions, without a claim of team or instructor approval.

## Citations and validation

A citation contains only `firstLine` and `lastLine`, with inclusive global bounds. Analysis resolves it to exact prepared spans. Adjacent lines in one segment retain their exact slice, while separate segments remain separate evidence references. Canonical evidence includes permanent source identities and zero-based Unicode code-point offsets with an exclusive end. Validation checks that slice directly rather than searching for a quotation.

Out-of-range, reversed, cross-unit and whitespace-only selections reject the reply. Analysis never splits, clamps, repairs or reassigns a range. An unresolved outcome reports citation root causes without secondary errors from partial candidates.

Within a development, links with the same issue and basis merge into one canonical association. Evidence is deduplicated by exact span in first-use order. Each later link receives `LINKS_MERGED`. Different bases remain separate and fail as `DUPLICATE_ASSOCIATION`. Raw links and resolution traces remain available.

These codes invalidate a reply or canonical candidate:

| Area | Codes |
|---|---|
| Shape and content | `MALFORMED_REPLY`, `EMPTY_TEXT` |
| Citation selection | `LINE_OUT_OF_RANGE`, `REVERSED_RANGE`, `RANGE_CROSSES_UNITS`, `BLANK_RANGE`, `MISSING_EVIDENCE` |
| Identities and references | `DUPLICATE_ID`, `UNKNOWN_ISSUE`, `DUPLICATE_ASSOCIATION`, `UNKNOWN_PRIOR_ISSUE`, `UNRESOLVED_EVIDENCE_REFERENCE` |
| Source integrity | `EVIDENCE_TEXT_MISMATCH` |
| Dates | `INVALID_CALENDAR_DATE`, `REVERSED_DATE_RANGE`, `OCCURRENCE_WITHOUT_RECORD`, `OCCURRENCE_AFTER_MEETING`, `MEETING_DATE_MISMATCH` |

Validation and runtime-handling revision, November 18, 2026: `OCCURRENCE_AFTER_MEETING` is invalidating, and the obsolete unit-address code is removed. A failed runtime-properties check before completion is sent returns `RUNTIME_FAILED` and releases admission. Effective generation settings must echo the requested integer seed. Receipt-save failures preserve the original exception and do not bypass admission cleanup; uncertain execution still retains admission. Display escaping also covers NEL and Unicode line/paragraph separators. The frozen instructions, examples, schema and `grounded-findings-3` revision are unchanged.

`UNUSED_ISSUE` and `LINKS_MERGED` are the only non-invalidating notes. Unused issues stay in the raw reply but are omitted from canonical candidates. Evidence, developments, issues and associations remain separate.

## Prompt hygiene

Worked examples are synthetic, with line and document-alias numbers from 90001. Instructions contain no concrete live citation numbers or wording copied from development meetings beyond a fixed allowlist of standard agenda boilerplate. Before trials, a private check compares the complete system prompt against development inputs for shared four-word sequences. It uses lowercase Unicode letter and digit tokens, with no length filter, and a fixed boilerplate allowlist. Other matches require a meaning-preserving rephrasing or a stop for review. The allowlist is not expanded to make the check pass.

## Artifacts and diagnostics

Runs retain owner input, a source index, `model-input.txt`, messages, schema, rendered template, token IDs, runtime properties and raw output. `resolution.json` records each citation's span or problem. `validation.json` contains typed codes and locations. Canonical candidates are saved when interpretation reaches that stage, including candidates that fail canonical validation. Receipts retain outcomes and provenance hashes. Uncertain completion keeps admission until its runtime is reconciled. There is no automatic retry or repair call.

The `summarize` command reads saved trials without starting Spring or inference. It creates `summary.json` and `summary.md` in a new directory. The trial table reports revision, sampling, state, token counts, canonical counts, finding kinds, date roles, unlinked developments and raw key order. Missing artifacts remain unavailable rather than becoming valid empty findings.

The Citations table reports source units, line bounds and lexical overlap. For v3, overlap compares raw `does` tokens with the union of tokens from each resolved span. The Links table reports every raw link before merging. Name coverage is the share of raw issue-name tokens found in that link's evidence. Both use lowercase Unicode letter and digit tokens of at least three code points. Name coverage is unavailable when the name has no such tokens or any link evidence is unresolved.

Both columns are lexical diagnostics, with no threshold or effect on acceptance. High coverage does not establish support. An official alias can have low name coverage. Existing summary values remain unchanged when newer diagnostics are added.

Current receipts use `unit-6c-synthetic-wording`. Receipts from the final development trials retain `unit-6c-synthetic-wording-and-name-coverage`. The correction changed only the receipt label. Prompts, schemas and behavior are identical.

Historical quotation-shaped runs remain readable by `summarize`. The current scorer cannot re-score their candidate files after the span representation change. Earlier artifacts remain immutable. Offline replays compare saved spans and diagnostics without rewriting receipts or declaring quality scores.

## Known limitations

These findings need human source review. Line resolution does not catch them:

- Agenda-only material can produce umbrella or combined issue names.
- Two official names for one project can become separate issues.
- Link evidence can begin mid-sentence or span a whole compound motion. Readers need development evidence as well as link evidence.
- A meeting with minutes can still omit an agenda proposal.
- Staff-report background did not yield `EARLIER_ACTION` findings in development trials. Recall remains unscored until references are approved.
- A valid citation can fail to support its claim. Earlier trials reported superseded-motion wording as adopted and linked programs absent from the cited passage. These errors did not recur in the frozen arm, but the contract does not rule them out.
- A `STAFF` proposer can depend on a matching staff report rather than the cited agenda lines alone.

The [evaluation guide](../../development/MODEL_EVALUATION.md) describes commands and review files. [ADR-0040](../adr/ADR-0040-frozen-model-findings-contract.md) records the decision history. The [analysis operations guide](../../development/ANALYSIS_OPERATIONS.md) covers saved revisions, finding kinds, prior-issue identities and internal routes. Model-quality approval, coordinator-wide chronological admission and publication remain separate work.
