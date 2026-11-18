# ADR-0038: Evaluate whole meetings before adopting model inference

- **Status:** Accepted
- **Date:** 2026-11-17
- **Deciders:** Kazim Yigit Ablay
- **Related requirements:** AS-03 through AS-05, COM-10 through COM-12, NFR-05 through NFR-08, NFR-14 through NFR-18

## Context

Prepared records now have immutable text and source locations. Model feasibility still needs evidence from complete meeting inputs, usable output and source review. A valid JSON response alone cannot establish that an issue connection is correct.

## Decision

Analysis owns a developer evaluation runner that reads pinned records through authenticated owner APIs. It makes no civic-domain writes and publishes no results. A trial starts with one whole-meeting request, including all selected source text and any frozen earlier issue context. Capacity checks cover the rendered prompt and reserved output. Overflow, truncation, exhausted output and uncertain execution remain explicit failures.

Qwen3.5-9B is the local feasibility candidate. Artifact hashes, quantization, runtime build, template, prompt and generation settings are recorded for each trial. Ollama is the initial runtime direction, with llama.cpp permitted where exact token preflight cannot be established. Neither is adopted as the production runtime by this decision. Preparation finishes and releases OCR before language-model trials begin.

Candidate evidence, developments and issue associations remain separate. Analysis resolves known segment IDs and literal quotations to Unicode code-point offsets. Dates retain their role and precision. Structural checks establish reference validity, while human source review establishes support and meaning.

ADR-0039 subsequently revises the model-facing format to short source ranges and typed date alternatives. Analysis constructs exact quotations after resolving those ranges. The scope, canonical data separation and human-review requirements in this decision remain unchanged.

Development references remain separate from final evaluation. The evaluation unit is a development-to-issue association with its evidence. Precision, recall, evidence presence and evidence support retain raw counts and undefined denominators. Consequential or ambiguous annotations receive a second team review. Numerical acceptance targets follow development evidence and precede final evaluation.

## Alternatives considered

A standalone experiment would duplicate the Java validation boundary. Immediate production persistence would commit to issue identities and recovery contracts before feasibility is known. Silent truncation, source summaries and automatic chunking would change the evaluated input without resolving the capacity limitation.

## Consequences

T07 and T08 can reuse the tested input and validation boundaries. Model quality and acceptable latency still require team judgment. A failed capacity trial can inform the investigation without approving the configuration. A timed-out request retains an unresolved execution marker until its runtime is reconciled.

## AI assistance and provenance

Codex assisted with architecture and implementation planning. Kazim accepted the six T06 recommendations. Automated checks and AI-assisted annotations do not constitute human review or model acceptance.

## References

- SRS v2.0 and the submitted SPMP, T06
- ADR-0008, ADR-0009, ADR-0015 through ADR-0021
- ADR-0026 through ADR-0028 and ADR-0036
- [Qwen3.5-9B model card](https://huggingface.co/Qwen/Qwen3.5-9B)
- [llama.cpp server interfaces](https://github.com/ggml-org/llama.cpp/blob/08a9b358939e8c99f9e3299bb2584ffd7b285f52/tools/server/README.md)
