# ADR-0040: Freeze the host-resolved findings contract for T06

- **Status:** Proposed
- **Date:** 2026-11-17
- **Recorded:** 2026-11-18
- **Decider for the underlying choices:** Kazim Yigit Ablay
- **Related requirements:** SRS v2.0 AS-03 through AS-05, COM-10 through COM-13, NFR-05 through NFR-08, NFR-14 through NFR-18; OQ-01, OQ-05 and OQ-07
- **Refines:** ADR-0038 and ADR-0039

## Context

Host-resolved ranges removed quotation bookkeeping from model output, but exact pointers did not establish meaning. Development trials exposed seven problems:

| Finding | Problem |
|---|---|
| F1 | A citation used the right local line numbers under the wrong unit. |
| F2 | A recommendation addressed to Council was attributed to Council as its proposer. |
| F3 | A scheduled meeting date became an occurrence date on a proposal. |
| F4 | Distinct civic matters became one issue. |
| F5 | Relevant developments were omitted, with recall unscored pending reference approval. |
| F6 | Schema order placed claims before evidence and referenced issues before declaring them. |
| F7 | Some developments had no issue links. |

The implementation also retained quotation searching after exact spans were already available. Separate schema, parsing and rendering definitions increased the chance of contract drift.

This record remains Proposed. The underlying choices and freeze were accepted by Kazim. That acceptance does not imply team or instructor approval, approved reference labels or model-quality acceptance.

## Decision history

- November 17, 2026: Kazim accepted D1 through D8, including Java-rendered descriptions. D9 global line citations became permanent after the development comparison. T5 included agenda recommendations to receive a report and provide direction as `PROPOSAL` developments.
- November 17, 2026, after the first full-contract development trials: Kazim accepted D10's same-issue, same-basis link merge. T6 distinguished collective subjects from specific issues. Broad grouping belongs to C-49 through C-51 categories, outside T06.
- November 18, 2026: Kazim accepted the final instruction wording and froze the contract after the final development trials regardless of their results. No further T06 prompt tuning uses the two development meetings.

## Decision

The contract incorporates D1 through D10 and the freeze:

1. D1 uses complete, addressed plain text with document and section headers. No selected source is dropped or summarized to fit.
2. D2 puts issues first and orders each development as evidence, finding, `does` and links.
3. D3 uses typed finding kinds to derive status and date roles. Java supplies meeting dates and the occurrence date of a current-meeting action. The model states planned dates and earlier occurrence values.
4. D4 renders canonical descriptions in Java from the finding kind, actor and model-written verb phrase.
5. D5 provides offline `summarize` diagnostics. Lexical overlap, name coverage, raw key order and unlinked-development counts do not determine acceptance.
6. D6 retains evidence as exact source spans. Validation checks source identity and Unicode code-point slices instead of searching for quotations.
7. D7 separates input-format and full-contract comparisons on frozen development inputs. The baseline profile precedes the Qwen profile. Final-evaluation inputs remain separate.
8. D8 represents reported historical actions through `EARLIER_ACTION`. Whether background history belongs in scored references remains open.
9. D9 numbers live input globally from `L1` through `LN`. Citations contain inclusive `firstLine` and `lastLine` bounds. Invalid ranges are never split, clamped, repaired or reassigned.
10. D10 merges links within a development when issue and basis match. It unions exact evidence spans in first-use order and records `LINKS_MERGED`. Different bases remain an invalid duplicate association.

The frozen revision is `grounded-findings-3` with the final instruction wording of November 18, 2026. Worked examples use synthetic text and a separate address space. A fixed-allowlist check detects shared four-word sequences between the system prompt and development inputs before trials. The freeze is a feasibility result, not a production model or profile choice.

## Alternatives considered

Prompt-only changes would leave actor, date and citation bookkeeping as independent model choices. Nesting developments under issues would duplicate a multi-issue motion or require a second structure for unlinked developments. Anchor phrases would reintroduce model text copying and pressure for fuzzy matching. Two-stage verification and reasoning mode would change the inference design and require a separate decision.

Keeping JSON input, free-form descriptions, quotation-shaped evidence or script-only diagnostics would preserve the earlier contract at the cost of its identified failure modes and duplicated validation paths.

## Consequences

Analysis has one request and interpretation boundary, typed problems and separate evidence, developments, issues and associations. Invalid citations still reject the whole reply. Unused issues and merged links produce non-invalidating notes. Exact pointers and lexical diagnostics make review easier but cannot establish semantic support.

Issue names remain model-written and are not stable identities. Umbrella naming and duplicate official aliases matter for T07 and OQ-05. Link ranges can remain incomplete or overly broad, and valid citations can support the wrong claim. Actor inference and the distinction between proposed and adopted motion wording still need source review.

Recall remains unscored until references are approved. T1 through T4, reference approval and numerical targets remain open. Production identity, persistence, coordinator admission and publication are outside this increment.

The span representation changes canonical candidate files. Current scoring cannot re-score old quotation-shaped runs. Their artifacts remain immutable and readable by the offline summary. Replays verify consistency without rewriting old receipts or creating new quality scores.

The corrected receipt detail is `unit-6c-synthetic-wording`. Receipts from the final development trials retain `unit-6c-synthetic-wording-and-name-coverage`. That metadata correction leaves prompts, schemas and behavior identical.

## AI assistance and provenance

Claude Opus assisted with architecture. Codex implemented the accepted plan, ran automated verification and prepared source-support observations. Kazim accepted D1 through D10 and the contract freeze. Automated checks and Codex-assisted readings are not human reference approval or model-quality acceptance.

## References

- [T06 design](../design/T06_DESIGN.md)
- [Model evaluation guide](../../development/MODEL_EVALUATION.md)
- [ADR-0038](ADR-0038-model-feasibility-evaluation.md)
- [ADR-0039](ADR-0039-host-resolved-model-citations.md)
- SRS v2.0 and the submitted SPMP, T06
