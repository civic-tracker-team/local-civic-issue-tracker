# Local Civic Issue Tracker: build the seven screens in the frontend (phase 2)

You are the frontend implementer for this repository, running as a Sol model through a T3 Code delegate, for Michael (the user). Phase 1 is finished: direction a (Ledger) is signed off, and questions 1 to 9 are answered. You build phase 2 only.

The workspace is this repository's root, on branch `t3/align-canvas-boards`. Before changing anything, run `git status` and `git log -3`. Some files under `docs/engineering/ui-design/` may be uncommitted, and that is expected. `frontend/` holds only a README and an empty `src/`.

If `docs/engineering/ui-design/designs/` is missing, or DECISIONS.md has no dated sign-off of direction a, stop and report. Phase 2 has not been opened.

## Read first, in this order

1. `AGENTS.md`: the repository rules. They override your defaults.
2. `docs/engineering/ui-design/DECISIONS.md`: all of it. "Frontend build" holds your stack choices, and "Refining direction a" holds the screen-level choices.
3. `docs/engineering/ui-design/HANDOFF.md`:
   - Read "Precedence", "Hard rules", the phase 2 lines of "Phase discipline" and "Done means", and "Report back".
   - Read "Canvas mechanics" only for how a design file is built.
   - Phase 1 is history.
4. `docs/engineering/ui-design/designs/`: the signed-off specification.
   - `canvas.json` maps the boards.
   - Each `*.dc.html` design keeps its light and dark tokens in `renderVals()`.
   - Each `_phone` wrapper shows the same design at 390px in dark.
5. `.local/design/review/shots/`: screenshots of every a board, desktop and phone. Compare your screens against these.
6. SRS v2.0 in `docs/engineering/srs/`: the requirements DECISIONS.md lists under "Screens to design", and NFR-03 (accessibility).
7. ADR-0005 (frontend stack), ADR-0018 (evidence and issue model) and ADR-0020 (temporal data semantics) in `docs/engineering/adr/`. These shape the types in the API-client layer.
8. `frontend/README.md` and `infra/api-routing/README.md`.
9. For the record screen's types only, the records service's read API under `services/`. Read it and do not change it.

## What you are building

Build the seven screens of direction a as a React, TypeScript and Vite app in `frontend/`. They are home (1a), search results (2a), issue timeline (3a), meeting (4a), record (5a), browse issues (6a) and browse meetings (7a).

The binding sources, highest first, are:
1. the SRS and the ADRs;
2. DECISIONS.md;
3. the designs in `docs/engineering/ui-design/designs/`.

The b to e designs in `.local/design/project/` are reference only.

Keep these parts of the designs exactly:
- **Basis tags.** A stated link is tagged "Stated". An inferred link is tagged "Inferred link · may be incorrect".
- **The "Sample data" badge.**
- **The coverage figures.** 92 scheduled meetings, 90 held and 2 cancelled, plus 1 upcoming. The figures must agree on every screen.
- **One navigation on every screen.** "Search, Issues, Meetings", with `aria-current="page"` on the current section.
- **Skip link and focus ring.** A "Skip to main content" link targets `main#main`, and a visible 3px focus ring shows in both themes.
- **Touch targets of at least 44px at phone width.** Links inside sentences are exempt.

How to build it:
- **Styling:** plain CSS with custom properties.
  - Lift a's light and dark token values from the designs into one tokens file.
  - Follow the reader's system light or dark setting. Do not add a theme toggle, because none of the designs has one.
  - The designs use container queries for the phone layout. A media query or a container query is fine in the app.
- **Routing:** TanStack Router with routes defined in code. Use the route table in DECISIONS.md "Frontend build". Type each route's path parameters and search parameters. Validate search parameters with a hand-written `validateSearch` function, not a schema library.
- **Data:**
  - Put typed sample data behind a small API-client layer, for example `src/api/`.
  - Each screen calls async client functions and never imports sample data directly, so a real endpoint can replace one function later.
  - Name types after the SRS and the ADRs: evidence, basis, issue, meeting, record, version and temporal fields.
  - Take the sample content from the designs.
- **Fonts:** follow DECISIONS.md "Frontend build".
  - Download Inter's woff2 files and their OFL licence from the official Inter release.
  - Load them with `@font-face` in the app's CSS, so the browser requests nothing from a third party.

## Precedence

Follow the order in HANDOFF.md "Precedence", with this brief taking HANDOFF.md's place. If a design and a requirement disagree, follow the requirement. Record the conflict in your report and do not redesign around it.

## Phase discipline

Keep a gate register in `.local/design/GATES.md` under a new "Phase 2" heading (use the "gates" skill). Put your evidence in `.local/design/review/frontend/`.

| Gate | What it takes |
|---|---|
| Scaffold | `package.json` and `package-lock.json`, with only the agreed packages. `engines` names the current Node LTS line; check it with `node --version` and Node's release schedule. Scripts `dev`, `build` (type-check, then `vite build`) and `preview`. A clean `npm run build`. |
| Shell | The tokens file, the app layout with header, navigation, skip link, focus style and "Sample data" badge, and all seven routes resolving. |
| Screens | Each screen matches its a design on desktop in light and dark, and on phone at 390px. |
| Verification | All of these are saved in `.local/design/review/frontend/`: screenshots of every screen in light, dark and 390px beside the design shots; a keyboard-only pass of every screen; a contrast check showing no text below 4.5:1; and the build output. |

If a gate needs something only Michael can decide, write the question in `docs/engineering/ui-design/OPEN-QUESTIONS.md` in that file's format, numbered from 10, with options and a recommendation. Then stop and report. Do not guess. This covers:
- data shapes the SRS and the ADRs leave open;
- a route the table lacks;
- testing;
- hosting.

## Hard rules

- **Agreed packages only:**
  - `react`, `react-dom`, `@types/react`, `@types/react-dom`, `typescript`, `vite`, `@vitejs/plugin-react` and `@tanstack/react-router`.
  - Anything else needs Michael's agreement first. That includes linters, test runners, schema libraries, router devtools, font packages and CSS tools.
  - For screenshots and checks, use tools already on the machine from scripts kept in `.local/`. `.local/design/review/scripts/` has examples; adapt their hard-coded tool paths. Never add those tools to `package.json`.
- **Never modify:**
  - the SRS, the SPMP or any ADR;
  - anything under `services/` or `infra/`;
  - `docs/engineering/ui-design/designs/`;
  - `.local/design/project/`.
- **Never start, stop or reconfigure** the Java services, PostgreSQL or Docker.
  - You may run the Vite dev server and `vite preview` on a free localhost port.
  - Stop them before you report.
- **Never create, publish, edit or delete a claude.ai artifact.** The design canvas belongs to the phase 1 session.
- **Use sample data only:**
  - no real records;
  - no network calls to the services;
  - no sign-in, accounts or personal features;
  - no city logos or branding.
- **Write no machine-specific absolute paths** in tracked files, including `vite.config.ts`, the README and the scripts in `package.json`.
- **Never commit, push or open a pull request.** Michael asks for that separately.
- **Never print or log secrets.** This work needs none.

## Done means

All four gates are met with evidence in the register:
- The seven screens render in the Vite dev server in light, in dark and at phone width.
- Each screen matches its a design.
- `npm run build` passes.
- A keyboard-only pass over every screen works.

Update `frontend/README.md` to say how to install, run and build the app, and remove its line saying that no runnable frontend exists. Leave its stack description alone.

## Report back

End with these:
- the files you added or changed;
- the gate register, with each gate met, pending (naming the missing evidence) or skipped (with a reason);
- the evidence paths;
- every place a screen departs from its design, and why;
- any question you added to OPEN-QUESTIONS.md;
- the exact packages and versions installed.

## Suggested skills

"gates" (the phase 2 register), "decision-log" (if you add an open question), "run" (to see the app working) and "review-pack" (only if Michael asks for a packaged review).
