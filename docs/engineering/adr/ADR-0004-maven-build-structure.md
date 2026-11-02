# ADR-0004: Use a Maven parent and separate Java service modules

- **Status:** Accepted
- **Date:** 2026-10-26
- **Recorded:** 2026-11-01, retrospectively
- **Deciders:** Kazim Yigit Ablay
- **Related requirements:** LIM-05; supporting conventions COM-03, COM-04

## Context

The selected Java backend contains records, search, analysis and coordinator services. Their builds need a common basis without turning the services into one runtime. Python OCR and the browser frontend have separate tooling needs.

Maven was selected during the technology discussion. The later scaffold decision established a root parent/aggregator POM and a child POM for each Java service. SRS v1.0 records Maven 3.10.0 as the build baseline.

## Decision

We will use Maven 3.10.0 with a root parent/aggregator and separate Java service modules. The parent provides a common place for shared build settings. Each service retains its own POM and service-specific dependencies.

Python OCR and frontend tooling will remain outside Maven. Java source follows the selected Google Java Style basis and uses `org.civictracker` as its base namespace. These conventions do not require a new shared implementation library.

A Maven module is a build unit. Its presence in the same reactor does not make services share a JVM, object identity or persistence context.

## Alternatives considered

Gradle was compared for its flexible task configuration and incremental/build-cache facilities. The retained recommendation favored Maven's conventional lifecycle and dependency management because custom build logic was not the project's central problem. Kazim selected Maven, although a more detailed personal preference was not recorded. No project-specific build-performance comparison was performed.

As a retrospective construction comparison, fully separate root builds would avoid a shared parent but duplicate common settings. The chosen parent/child arrangement centralizes those settings. This comparison explains the consequence of the selected structure rather than inventing an earlier benchmark.

## Consequences

- **Positive:** Shared build settings have one maintained location while service-specific dependencies remain visible.
- **Negative:** Parent changes can affect several services and need appropriate build checks. Independent deployment still needs separate configuration.
- **Follow-ups:** Verify actual builds and dependency compatibility. No formatter plugin, shared entity package or dependency-sharing policy is selected by this record.

## AI assistance and provenance

Codex helped organize the build arrangement and checked it against LIM-05 and the scaffold decisions. Kazim selected Maven and the parent/module structure. The missing comparative preference has been left explicit.

## References

- SRS v1.0
- ADR-0003: Backend languages and framework
