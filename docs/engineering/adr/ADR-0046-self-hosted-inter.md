# ADR-0046: Self-host the Inter font

- **Status:** Accepted
- **Date:** 2026-11-15
- **Recorded:** 2026-11-18
- **Deciders:** Michael Avila
- **Related requirements:** IF-01

# Michael - My thinking
This is the more in-depth option so it would be easier to switch from this to using a third part hosted font to comply with our design.

## Context

The signed-off designs use Inter. The designs load it from Google Fonts, but a public civic site should not make every visitor's browser contact a third party.

## Decision

Inter's woff2 files and its OFL licence, taken from the official Inter release, are committed under `frontend/public/fonts/` and loaded with `@font-face`. No npm font package is added. `system-ui` is the fallback.

## Alternatives considered

A Google Fonts link would send each visitor's address to Google. System fonts only would make the screens drift from the designs. `@fontsource/inter` would add a dependency for files that can simply be committed.

## Consequences

- **Positive:** Pages make no third-party font requests and match the designs.
- **Negative:** Font updates are manual: download the new release and replace the files.

## AI assistance and provenance

Michael chose self-hosting on 2026-11-15. A Codex (GPT-6.1 Sol) delegate downloaded Inter 4.1 and its licence from the official release, coordinated by Claude Code.

## References

- SRS v2.0
- `frontend/public/fonts/`
