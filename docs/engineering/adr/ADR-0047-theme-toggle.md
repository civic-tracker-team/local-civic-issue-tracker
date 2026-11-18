# ADR-0047: Add a light and dark theme toggle that starts from the system setting

- **Status:** Accepted
- **Date:** 2026-11-18
- **Recorded:** 2026-11-18
- **Deciders:** Michael Avila
- **Related requirements:** IF-01, NFR-03

## Context

The frontend has light and dark themes (ADR-0042) and at first followed only the reader's system setting. Checking both color schemes during development then meant changing the operating system or the browser's device settings each time.

## Decision

A theme toggle sits at the top right of the header on every screen. On phone-width screens it sits beside the site name, and the navigation wraps to the row below. Until a reader uses it, the page follows the system setting. The choice is then kept in that browser's local storage only and applied before the page first draws.

The dark tokens apply when the system is dark and `<html data-theme>` is not `light`, or when `data-theme` is `dark`. The toggle is a 44px button with an accessible name that says which theme it switches to.

## Alternatives considered

Following the system setting only, as the frontend brief first specified, keeps the designs unchanged but makes checking both schemes slow during development. A toggle hidden behind a development flag would help development but not readers who prefer a different theme on this site.

## Consequences

- **Positive:** Developers and reviewers can check both color schemes in one click. Readers can choose a theme for this site without changing their system.
- **Negative:** The header has a control that the signed-off designs do not have. Screen CSS needs both dark-theme selectors.
- **Follow-up:** The choice is stored per browser, not per account. The site has no accounts.

## AI assistance and provenance

Michael asked for the toggle and gave the reasons: primarily a development aid for checking both color schemes, and secondarily a site preference for readers. Claude Code built and checked it in Chromium at desktop and phone widths.

## References

- SRS v2.0
- ADR-0042: Plain CSS and design tokens
- `docs/engineering/ui-design/DECISIONS.md`, "Theme toggle"
