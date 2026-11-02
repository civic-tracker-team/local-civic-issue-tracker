# ADR-0003: Use Java/Spring Boot application services and Python OCR

- **Status:** Accepted
- **Date:** 2026-10-27
- **Recorded:** 2026-11-01, retrospectively
- **Deciders:** Kazim Yigit Ablay
- **Related requirements:** LIM-04

## Context

The application needs ordinary service APIs, domain persistence and coordination alongside OCR and language-model inference. Kazim's Java experience makes a Java application backend a practical direction, but this does not require inference libraries themselves to run inside the JVM.

The integration review considered Java service development and calling separately served inference. The selected October baseline was Java 25 and Spring Boot 4.1.2. Those are the versions recorded in SRS v1.0, rather than a claim that they will always be the latest releases.

## Decision

We will use Java 25 and Spring Boot 4.1.2 for records, search, analysis and the pipeline coordinator, with Python for OCR. Language-model inference will remain separately served so the analysis service can supply context and receive results without embedding the inference implementation in Java.

Application services retain validation and domain persistence. This choice does not select the OCR API framework, language model, model runtime, Java AI framework or JDK vendor. Spring Batch remains conditional rather than becoming an accepted dependency through this record.

## Alternatives considered

The review considered Python for analysis because it could ease direct use of Python-only NLP tools, while adding another application-framework convention. Separately served inference allowed the selected application responsibilities to remain in Java. This is an integration trade-off, not a measured Java performance advantage or a formal rejection of every competing framework.

The dated version review considered common framework and persistence compatibility instead of choosing a newer Java feature release solely because it was newer. It did not establish that the complete project builds or runs successfully.

## Consequences

- **Positive:** Application responsibilities stay in the chosen Java environment while inference implementations can use their appropriate runtime.
- **Negative:** The project must maintain Java/Python integration and service contracts. A language boundary introduces operational and failure-handling work.
- **Follow-ups:** Verify the selected toolchain and inference contracts during implementation. Record a later baseline change explicitly rather than silently updating this decision.

## AI assistance and provenance

Codex supported the integration and compatibility comparison and checked this record against LIM-04 and the confirmed selections. Kazim selected the language/framework allocation. Compatibility reasoning is not implementation validation.

## References

- SRS v1.0
- ADR-0009: Inference persistence boundary
- ADR-0011: Service communication
