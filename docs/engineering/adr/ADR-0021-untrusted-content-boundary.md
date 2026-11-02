# ADR-0021: Keep imported content outside application authority

- **Status:** Accepted
- **Date:** 2026-10-30
- **Recorded:** 2026-11-01, retrospectively
- **Deciders:** Kazim Yigit Ablay
- **Related requirements:** NFR-02, NFR-14, NFR-15, NFR-16; related inference access NFR-17

## Context

Imported documents and inference responses contain externally derived content. Official provenance makes a document relevant evidence, but it does not make text inside it an authorized instruction to operate the application.

The same separation matters in the browser and in model processing. Displayed markup must not execute, and source text resembling a command must not gain command or write authority merely because the model sees it.

## Decision

We will treat imported content as untrusted analysis data. Analysis will separate trusted processing instructions from source content. Application controls, outside the model, will determine permitted behavior and validate returned output before accepting it as a usable persisted result.

Instructions embedded in records will have no authority to execute commands, modify application data or bypass validation. Browser display will prevent embedded scripts from executing. OCR and model serving remain without application SQL access under ADR-0009, with writes controlled by the requesting owner.

Prompt separation is part of this boundary rather than a security guarantee by itself. Structural validity also does not establish factual support. This record does not select a security library, authentication workflow, autonomous agent or mandatory human approval of every result.

## Alternatives considered

The accepted discussion placed authority and validation in application controls rather than trusting source instructions or model compliance alone.

Relying only on prompt wording would leave enforcement inside the component interpreting the untrusted content. This is a retrospective explanation of why the selected external controls matter, not a claim that the team previously built or selected an autonomous tool-using agent.

## Consequences

- **Positive:** Evidence content and application authority remain separate, with owners responsible for accepting and persisting results.
- **Negative:** Callers must handle invalid output and design enforcement that cannot be bypassed by source text. Safe prompting alone does not complete the security work.
- **Follow-ups:** Specify safeguards and verify representative malicious-content, unsafe-display and invalid-output cases. Do not confuse a well-formed response with evidence that its claims are true.

## AI assistance and provenance

Codex helped organize the trust-boundary reasoning and checked this record against the imported-content and inference requirements. Kazim selected application-controlled authority. No completed security testing or specific safeguard implementation is asserted.

## References

- SRS v1.0
- ADR-0009: Inference persistence boundary
- ADR-0018: Source evidence
