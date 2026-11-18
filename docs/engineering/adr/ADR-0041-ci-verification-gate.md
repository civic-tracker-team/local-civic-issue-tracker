# ADR-0041: Run the local Java verification gate in CI

- **Status:** Proposed
- **Date:** 2026-11-18
- **Decider:** Kazim Yigit Ablay
- **Related plan:** SPMP sections 5.3.4, 6.4 and 7.5

## Context

Java verification has been local-only. The CDR rubric and SPMP section 5.3.4 call for quality gates. CI needs to exercise the same database setup and build that developers run locally, so the two checks do not drift.

## Decision

Configure the Verify workflow for pushes to `main`, pull requests targeting `main` and manual dispatch. One job runs on Ubuntu 24.04 with a 30-minute timeout. It uses Temurin Java 25 and Maven 3.10.0, then invokes `pwsh -NoProfile -File tools/verify-java.ps1`.

The existing script creates a temporary PostgreSQL container, applies the local bootstrap and runs `mvn clean verify`. Its local behavior stays unchanged. Maven comes from the official Apache archive. The workflow checks the archive against Apache's published SHA-512 before extraction and adds the verified installation to PATH. An unavailable download or checksum mismatch fails the job without selecting another version.

Each action is pinned to a full commit SHA with a release-version comment. Workflow permissions are limited to `contents: read`, and no secrets are configured. A newer run cancels an older run for the same Git ref. Failed runs upload only Surefire reports, retained for seven days. Private local outputs are excluded.

## Alternatives considered

Local-only verification would leave pull requests without a shared automated gate. A separate CI Maven command could bypass or diverge from the local database bootstrap. A GitHub PostgreSQL service container was rejected because it would also create a second bootstrap path.

## Consequences

Pushes to `main` and pull requests targeting it receive the Java and PostgreSQL verification gate. Manual dispatch is also available. The OCR suite and coverage reporting remain outside CI. Search and the coordinator currently have startup tests only.

Branch protection remains a separate team decision. The workflow configuration is not evidence of a passing CI run. Its first GitHub execution is still needed to establish runner compatibility and record an actual result.

## AI assistance and provenance

Codex prepared the workflow and documentation from Kazim's approved CI instructions and checked the configuration locally. Action pins were resolved from official release tags. This record claims no GitHub CI result or broader quality acceptance.
