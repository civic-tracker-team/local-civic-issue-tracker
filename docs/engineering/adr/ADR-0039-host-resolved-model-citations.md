# ADR-0039: Resolve model-selected source ranges in analysis

- **Status:** Accepted
- **Date:** 2026-11-17
- **Deciders:** Kazim Yigit Ablay
- **Related requirements:** AS-03 through AS-05, COM-10 through COM-12, NFR-05 through NFR-08, NFR-14 through NFR-18
- **Revises:** ADR-0038's model-facing quotation and date format

## Context

The first local trials produced quotations attached to a heading or one line instead of the full passage. Another answer used quotation sequence numbers as occurrence positions. A date was rejected because a single day repeated its start value in an unused end field. These were avoidable responsibilities in the model-facing format.

## Decision

Analysis gives the model short unit references and numbered source lines covering every selected text segment in the same whole-meeting request. The model selects inclusive line ranges. Analysis resolves those ranges into the exact prepared segments and Unicode code-point positions, then constructs literal quotations from stored text. The model does not copy quotations, permanent UUIDs or repetition numbers. Invalid ranges remain errors, and analysis does not substitute a similar passage or another document.

Model output centres on developments and their supporting ranges. Analysis assigns temporary evidence and development references and retains separate evidence, developments, issues and associations internally. Permanent source identifiers, sealed preparation revisions and service ownership remain unchanged.

Single-day, month and year values use one model-facing value. Ranges use start and end, while unknown dates have neither. Date roles and uncertainty remain explicit. Analysis converts these forms to its internal date values and checks calendar validity and ordering. Schema alternatives must work with the pinned runtime; Java validation remains required.

Contract changes are evaluated first with the earlier effective sampling settings. Qwen's documented non-thinking profile is then compared separately using the same frozen development inputs and seeds. Neither profile is adopted as a production default by this decision. Reference validity, factual support and the four agreed quality metrics remain distinct.

## Alternatives considered

Prompt-only changes would leave exact text copying and source bookkeeping to the model. Fuzzy reassignment could hide changed qualifications or source identity. More inference stages, source-dropping retrieval and immediate model replacement would change the experiment before testing the simpler contract.

## Consequences

Analysis must retain a versioned mapping from each short reference to exact source spans. Valid pointers still need evidence review because the selected passage may not support the claim. The evaluation records raw replies, resolved passages, requested/effective sampling and failures. Old trials and prepared references remain immutable. No production persistence, publication or coordinator behavior is added.

## AI assistance and provenance

Codex researched citation systems and constrained output, then proposed the revised boundary. Kazim accepted all six documented recommendations and authorized implementation and verification. This acceptance does not approve human reference labels or establish model quality.

## References

- ADR-0036 and ADR-0038
- [LongCite](https://arxiv.org/html/2409.02897v2)
- [LangExtract source alignment](https://github.com/google/langextract/blob/main/langextract/resolver.py)
- [Pinned llama.cpp grammar guide](https://github.com/ggml-org/llama.cpp/blob/08a9b358939e8c99f9e3299bb2584ffd7b285f52/grammars/README.md)
- [Qwen3.5-9B model card](https://huggingface.co/Qwen/Qwen3.5-9B)
- [ALCE citation evaluation](https://arxiv.org/html/2305.14627)
