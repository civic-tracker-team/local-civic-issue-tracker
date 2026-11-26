# ADR-0050: Assemble agenda items, record lists and unavailable outcomes in the T10 routes

- **Status:** Proposed
- **Date:** 2026-11-25
- **Decider:** Michael Avila
- **Related requirements:** FR-04, FR-05, FR-06, FR-38; V-05, V-10
- **Related decisions:** ADR-0009, ADR-0017, ADR-0020, ADR-0048, ADR-0051

## Context

The SPMP gives T08 summaries, agenda and outcome overviews, and dated issue histories, with FR-38 among its requirements. T08's views are built only from saved analysis findings: issue timelines and summaries, each meeting's developments in source order, and each record's developments. They call no model and read no other service.

The rest of a meeting overview needs data that analysis does not hold:
- **Agenda items and the meeting's record list** come from Records.
- **FR-38** asks the system to describe an outcome as unavailable when the selected sources don't establish it. Its check is that absent minutes don't produce a claim that no action occurred. Deciding that needs to know which records a meeting has, for example an agenda with no minutes, and that is Records data. An UNCLEAR finding is not the same thing as an unavailable outcome.
- **Publication membership:** under ADR-0051, public readers pin the current manifest and filter every read against it. The T10 routes already have to do this for every page.

The views were ready for review before any of this was settled. Waiting would hold back T10 and T14, which build on them.

## Decision

T08 provides the finding-based views only. They take the saved revisions the caller chooses and add nothing from Records.

The views are deterministic. No model is called after analysis. An issue's summary is its developments' saved descriptions in timeline order, as the Release 1 scope agreed on November 18, 2026 defers model-written summaries.

The T10 routes put together each public page:
- The routes read the pinned manifest, choose its revisions (one per meeting) and load them through analysis.
- They read the agenda items and record list from Records.
- They add the developments from the T08 views.
- They describe an outcome as unavailable (FR-38) where the meeting's records don't establish one.

The rule for when an outcome counts as unavailable is agreed and tested with the T10 routes.

## Alternatives considered

- **Have the T08 views read Records.** The views would need a Records client and the publication manifest, which would repeat the reads and membership filtering the T10 routes already do. Testing them would also need Records fixtures.
- **Keep T08 open until the routes exist.** The views could not be reviewed or merged on their own, and T10 would wait on an unmerged branch.
- **Decide FR-38 from the findings alone, for example by treating UNCLEAR as unavailable.** That confuses a development whose outcome isn't established with a meeting whose sources don't cover the outcome. It also can't tell when minutes are missing.

## Consequences

- **Positive:**
  - The T08 views stay pure functions of saved findings, so they can be tested without other services and merged now.
  - Records data and the manifest are read in one place, the T10 routes, and filtered once.
  - FR-38 is decided where the meeting's records are known.
- **Negative:**
  - FR-38 and the agenda part of FR-05 move from T08 to T10. The SPMP task table no longer matches until it is revised.
  - T10 carries more work before the Release 1 candidate.
  - Until the routes exist, no code produces a complete meeting overview.
- **Follow-ups:**
  - Agree the FR-38 unavailable-outcome rule and test it with the T10 routes.
  - Add agenda items and the record list to the meeting route.
  - Revise the SPMP task descriptions for T08 and T10.

## AI assistance and provenance

Claude drafted this record from the SRS v2.0 requirements, the SPMP task table, ADR-0051 and the T08 views code. Michael chose to move FR-38 and the agenda part of the meeting overview to T10 for a smoother development flow. No implementation of the T10 routes or of FR-38 is claimed by this record.

This record was first numbered ADR-0052. In the PR #6 review on November 26, 2026, Kazim asked for ADR-0050, the number reserved for T08's deterministic-summaries decision, which keeps ADR-0052 for search storage and ADR-0053 for the public read path.
