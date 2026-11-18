# Local Civic Issue Tracker: finish design direction a, then build the frontend

You are the design and frontend agent for this repository, working in two phases for Michael (the user). Phase 1 refines the chosen screen designs on a shared claude.ai Design canvas. Phase 2 builds those screens in the React, TypeScript and Vite frontend. Phase 1 needs Claude Code with the Artifact tool signed in to Michael's claude.ai account; phase 2 can run in any harness.

The workspace is this repository's root. Before changing anything, run `git status` and `git log -3`: the `docs/engineering/ui-design/` folder may still be uncommitted, and `frontend/` contains only a README and an empty `src/`.

## Read first, in this order

1. `AGENTS.md`: the repository rules. They override defaults.
2. `docs/engineering/ui-design/DECISIONS.md`: all of it. "Chosen direction" is the newest section.
3. `docs/engineering/ui-design/OPEN-QUESTIONS.md`: questions 1 to 5 gate phase 1 sign-off, and 6 to 9 gate phase 2. Check for answers before each phase.
4. `.local/design/review/REVIEW.md`: a model-written visual check of all 35 designs. Read "Should fix", "Nice to have" and "By direction → a Ledger". Screenshots of the a boards are in `.local/design/review/shots/`.
5. `.local/design/project/canvas.json`, then `Main.dc.html` and the seven a designs (`Home1a`, `Search2a`, `Issue3a`, `Meeting4a`, `Record5a`, `Issues6a`, `Meetings7a`, each with a `_phone` wrapper). This is the source of the canvas.
6. SRS v2.0 in `docs/engineering/srs/`: the requirements listed in DECISIONS.md under "Screens to design", and NFR-03 (accessibility).
7. ADR-0005 (frontend stack), ADR-0020 (temporal data semantics) and ADR-0018 (evidence and issue model) in `docs/engineering/adr/`. Read these before phase 2.
8. `frontend/README.md` and `infra/api-routing/README.md`. Read these before phase 2.

## What you are building

**Phase 1.** Bring direction a (Ledger) to sign-off quality on all seven screens, on the existing canvas at https://claude.ai/artifact/JKrsn8k4HJTbT9MmZ4wRnF. Fix the problems the review found, apply Michael's answers to open questions 1 to 5, and mark a as the chosen direction on the read-me board.

**Phase 2.** Once a is signed off and questions 6 to 9 are answered, build the seven screens in `frontend/` to match the a designs: light and dark themes, phone reflow, the connection-basis tags and the sample-data labelling.

The binding sources are DECISIONS.md and the SRS. The b to e designs are reference only.

## Precedence

When sources disagree, the higher one wins:

1. SRS v2.0 and accepted ADRs.
2. `DECISIONS.md`, including answered open questions once they are moved there.
3. This brief.
4. The a designs on the canvas.
5. `REVIEW.md`, which is advisory model output.
6. The b to e designs.

If a review suggestion conflicts with a decision, follow the decision and mention the conflict when you report back.

## Phase 1: fixes to direction a

These fixes bring a in line with requirements or decisions already made, so they need no further approval:

1. **Inferred tags on 6a** must say "may be incorrect", as they do on screens 1, 3, 4 and 5.
2. **One global navigation on all seven screens.** Today 1a and 2a show "Issues, Meetings, About the records", while 3a to 7a show "Search, Issues, Meetings". Pick one set, use it everywhere, and mark the current page with `aria-current`.
3. **Visible focus style and a skip link on every screen.** DECISIONS.md promises visible keyboard focus, but none is styled yet.
4. **2a gets an `<h1>`.**
5. **Phone touch targets of at least 44px**, including the source-passage toggles on 3a and the 36px select on 2a.
6. **7a phone heat cells** reach at least 4.5:1 contrast. They measure 4.12:1 now.
7. **Nothing clipped by a frame.** The 3a desktop board overflows by 128px. The phone previews cut off content on 1a (about 2,040px of content in a 1,600px frame), 2a (2,040 in 1,800), 3a (3,260 in 2,200) and 4a (2,250 in 2,000). Raise the board heights in `canvas.json` and in each phone wrapper's `height` and `hint-size`, then move the rows below down so that no boards or titles overlap.
8. **1a coverage strip** is labelled by year, in the style of 7a's month grid.
9. **6a meta text** keeps each date range on one line.
10. **"Part of <issue>" on screen 2** carries a stated or inferred mark.
11. **Sample data agrees with itself:**
    - 7a's month grid sums to 86 meetings held, but the headline says 92. Make the labels say which figure counts held, upcoming and cancelled meetings.
    - Screen 5 says "retrieved Oct 23" but shows version 2, corrected Oct 28.
    - Use one 2026 meeting count across all a screens.
12. **Read-me board (`Main.dc.html`)** says direction a was chosen for all screens. Leave the b to e boards in place.

Then apply the answers to questions 1 to 5. If a question is still unanswered, use its recommended option only after Michael confirms it.

### Canvas mechanics

- **Before publishing,** read the artifact with the Artifact tool's `read` action. A publish to an artifact the session has not read is refused, and the read returns the Design type's instructions.
- **Publishing:**
  - Publish to the same `url`, with `root` set to `.local/design` and file paths under `project/`.
  - Edit `.local/design/project/` first. It is the durable source, and the canvas must match it.
- **How the boards are built:**
  - Each desktop design takes a `theme` prop (`light` or `dark`).
  - Its `renderVals()` returns the token object `t`, which inline styles interpolate.
  - Phone wrappers are 390px wide and import the design with `theme="dark"`.
- **Re-checking visually:**
  - `.local/design/review/scripts/` holds the scripts that rendered and audited the boards. `support.js` is the canvas runtime.
  - The scripts have hard-coded tool paths, so adapt them to your machine.
  - Re-render the a boards after your changes, because the published canvas cannot be screenshotted headlessly.

## Phase discipline

| Gate | What it takes | While waiting |
|---|---|---|
| End of phase 1 | All 12 fixes are published and re-rendered with no clipping and no contrast misses. Questions 1 to 5 are answered and moved into DECISIONS.md. Michael signs off direction a in chat, and you record the sign-off in DECISIONS.md with the date. | Do not start the frontend. |
| Start of phase 2 | Phase 1 is signed off, and questions 6 to 9 are answered and recorded. | You may draft a component and token inventory in `.local/design/`. Install nothing and add no files under `frontend/`. |
| During phase 2 | — | Any new question about routes, API shape, tests or hosting goes into OPEN-QUESTIONS.md, and you ask Michael before acting on it. |

Use the "gates" skill to keep the register for each gate. Record evidence: render paths, audit output, build output.

## Hard rules

- **Never modify** the SRS, the SPMP or any ADR. Never edit services under `services/` or `infra/`.
- **Get Michael's agreement before adding** a requirement, assumption, dependency, numeric target or workflow (AGENTS.md). Adding an npm package counts.
- **The canvas link is public.** Everything you publish is visible to teammates straight away. Publish only to https://claude.ai/artifact/JKrsn8k4HJTbT9MmZ4wRnF. Never create another artifact, delete this one, change its sharing, or remove its b to e boards.
- **Use sample data only.** Keep the "Sample data" badge and no city logos or branding. Use no real records, and add no sign-in or personal features.
- **Keep private material in `.local/`.** Draft design source, review output and screenshots stay there. Move nothing into tracked folders unless question 5's answer says so.
- **Write no machine-specific absolute paths** in tracked files.
- **Never start, stop or reconfigure** the Java services, PostgreSQL or Docker. Phase 2 runs on sample data, unless question 8's answer says otherwise.
- **Commit and push only when Michael asks,** on a branch, never on `main`.
- **Never print or log secrets.** Phase 1 and phase 2 need none.

## Done means

- **Phase 1:**
  - The canvas shows the fixed a designs, and `.local/design/project/` matches it.
  - Re-rendered screenshots of all 14 a boards (desktop and phone) are in `.local/design/review/shots/`, with an audit showing a focus style, no clipping and no contrast below 4.5:1.
  - DECISIONS.md records answers 1 to 5 and the sign-off.
- **Phase 2:**
  - The seven screens render in the Vite dev server in light and dark themes and at phone width.
  - Each screen matches its a design.
  - The production build passes.
  - A keyboard-only pass over every screen works.
  - The evidence is recorded in the gate register.

## Report back

At the end of phase 1, send Michael:
- the canvas link;
- a list of what changed per screen;
- the before and after screenshot paths;
- any review suggestion you chose not to follow, and why.

Then stop and wait for sign-off.

During phase 2, report at each gate. Stop and ask instead of guessing whenever:
- a screen needs data whose shape the SRS and ADRs do not settle;
- a design and a requirement disagree;
- a fix would need a new dependency.

## Suggested skills

"decision-log" (moving answers into DECISIONS.md), "gates" (phase registers), "artifact-design" (canvas work), "review-pack" (if Michael wants the result packaged for teammates) and "run" (to see the frontend working in phase 2).
