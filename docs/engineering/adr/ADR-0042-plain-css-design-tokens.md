# ADR-0042: Style the frontend with plain CSS and design tokens

- **Status:** Accepted
- **Date:** 2026-11-15
- **Recorded:** 2026-11-18
- **Deciders:** Michael Avila
- **Related requirements:** LIM-03, IF-01, NFR-03

## Context

ADR-0005 chose React, TypeScript and Vite but left the UI kit open. The signed-off screen designs (direction a, in `docs/engineering/ui-design/designs/`) define a small set of color, type and spacing values for light and dark themes. The frontend has to match those designs closely and keep keyboard focus and contrast under the project's control.

## Decision

The frontend uses plain CSS with custom properties. Direction a's light and dark values live in one tokens file, `frontend/src/styles/tokens.css`. Each screen keeps its own stylesheet in its screen folder. No styling package is added.

## Alternatives considered

A styled component library such as MUI or Mantine has its own look, which would have to be overridden to match the designs. A utility framework such as Tailwind adds a build dependency and moves the token set into class names. A headless library such as React Aria or Radix is not needed yet: the seven screens use native links, forms, selects and disclosures. It can be proposed later if a widget needs it.

## Consequences

- **Positive:** The tokens match the designs value for value, and restyling means editing one file. Focus rings, contrast and 44px touch targets stay under direct control.
- **Negative:** Accessible behaviour that a component library would supply, such as complex widgets, has to be built and checked by hand.
- **Follow-up:** A screen that needs a dark-only rule must use the same theme selectors as the tokens file (see ADR-0047).

## AI assistance and provenance

The options were prepared with AI assistance as open question 6 in `docs/engineering/ui-design/OPEN-QUESTIONS.md`. Michael chose plain CSS on 2026-11-15. Codex (GPT-6.1 Sol) delegates built the tokens and screens, coordinated by Claude Code. Local verification evidence covers contrast and focus only for the sample screens.

## References

- SRS v2.0
- ADR-0005: Frontend stack
- ADR-0047: Theme toggle
- `docs/engineering/ui-design/DECISIONS.md`, "Frontend build"
