# UI design decisions

Settled decisions for the web frontend's screen designs, with the reason for each. Open design questions live in `OPEN-QUESTIONS.md` beside this file when there are any. Requirement IDs refer to SRS v2.0.

## Screens to design

*Decided 2026-11-13.*

- **Seven screens, in this order:** home/search landing, search results, issue timeline, meeting overview, record/source view, browse issues, browse meetings.
  *Why:* together they cover every user-facing requirement in SRS v2.0 (FR-02 to FR-08, FR-19, FR-21 to FR-32, FR-36 to FR-39, FR-43 to FR-45), in the order a user meets them.
  *How this shapes the product:* evidence and connection detail is a panel inside the issue timeline and record view, not a separate screen.
- **Rejected: an admin or pipeline-status screen.** No user-facing requirement asks for one.
- **Rejected: designing only the five core screens.** Browsing issues and meetings is part of finding information without knowing dates or terms.

## Design exploration

*Decided 2026-11-13.*

- **Five designs per screen, spanning the inspirations plus two originals:** (a) calm single column, after Memos; (b) dense sidebar with list or table, after Faved; (c) search-first cards, after Grimoire; (d) document or reader layout; (e) editorial or newspaper layout.
  *Why:* the five options should be truly different directions, not five tweaks of one layout.
  *How this shapes the product:* ADR-0005 says the issue timeline shows a matter's evolution, not a social-media feed, so the Memos direction borrows its calm and date grouping, not the look of a post stream.
- ~~**One screen at a time, with a stop for review after each.** We pick or comment on a screen's five designs before the next screen starts.~~
  ~~*Why:* choices on early screens (navigation, sidebar or top bar, density) should shape the later screens.~~
  *Decided 2026-11-13:* see the next line.
- **All seven screens are built first and reviewed together.** Each direction (a to e) keeps the same look across all seven screens, so a pick can be a whole direction or a mix by screen.
  *Why:* Michael was away and couldn't review on a phone, so review waits until all screens are ready.
- **Rejected: building all 35 designs and reviewing them at the end.** *Reversed 2026-11-13:* see the line above.
- **Desktop first, with a small phone-width preview of each design, in light and dark themes.**
  *Why:* SRS v2.0 targets Edge and Firefox and calls mobile layouts tentative. A phone preview shows whether a layout can scale down without doubling the work.
- **Rejected: full mobile designs for every variant.** Doubles the work while mobile support is still tentative.
- **Content is clearly labeled sample data:** realistic but made-up San Bernardino-style issues, dates and passages, with a "Sample data" badge and no city logos or branding.
  *Why:* realistic content tests the hard cases (inferred links, approximate dates, conflicts), and the label keeps anyone from mistaking it for real records.
- **The five designs differ in visual style (type, color, density) as well as layout.** Each uses a neutral civic palette with no city branding, and each meets WCAG AA contrast with visible keyboard focus.
  *Why:* each pick should choose a whole look, not just a structure. Contrast and focus follow NFR-03.
- **Rejected: one shared style across all five designs.**
- **No sign-in or personal features** (follow issue, save, personal tags) appear in the designs. The site is public and read-only.
  *Why:* SRS v2.0 has no accounts or saved items, and adding them would introduce requirements the team has not agreed. The inspirations are personal tools, so their account features do not carry over.
- **Rejected: showing personal features as marked optional ideas.**
- **Rejected: real records** (risk of the mockups being read as published results) **and lorem ipsum** (hides content problems).

## Chosen direction

*Decided 2026-11-13.*

- **Direction a (Ledger, after Memos) for all seven screens.** Work continues only on the a designs (1a to 7a and their phone previews).
  *Why:* two teammates chose it independently as "the best, cleanest and simplest", and likely the most approachable for people who are not comfortable with computers.
- **Rejected: b (Index).** Looks good but too complex for our goals; it reads like a developer tool.
- **Rejected: c (Finder).** Looks good but still too complex. The search callout at the top of its home screen was liked and may be borrowed into a.
- **Rejected: d (Reader).** Looks low effort.
- **Rejected: e (Bulletin).** Looks too much like a news site.

## Refining direction a

*Decided 2026-11-13. Answers to open questions 1 to 5; Michael accepted the recommended option for each.*

- **No category filter on browse issues (6a) until FR-39 is confirmed.**
  *Why:* categorization is in SRS v2.0 only if it stays simple (FR-39, FR-40). 6a works without it, and a filter is easy to add later.
- **Rejected: designing for categories now.**
- **Home (1a) takes the search callout from 1c, in a's style:** a bordered search panel with a larger field and "Try" example searches.
  *Why:* it is the only part of c the team asked for.
- **Rejected: keeping 1a's quieter search unchanged.**
- **The issue timeline (3a) gets a compact timeline strip above the dated list,** after 3c.
  *Why:* a long issue such as the parking structure spans nine meetings, and the strip shows that span before the reader scrolls.
- **Rejected: a list-only timeline.**
- **The record view (5a) keeps its connection notes in a's layout,** not beside each passage as in 5d.
  *Why:* it is simpler, already works on phone, and covers the same requirement; the team found d's look low effort.
- **Rejected: notes beside each passage on desktop.**
- **After sign-off, 1a to 7a, their phone previews and `canvas.json` are copied into `docs/engineering/ui-design/designs/`.** The rest of the draft design source stays in `.local/design/`.
  *Why:* the team can see the spec the frontend follows in version control.
- **Rejected: keeping everything in `.local` with the public canvas as the only reference.**
- **One global navigation on every screen: Search, Issues, Meetings,** with the current section marked by `aria-current`. "About the records" stays a link from the coverage notice.
  *Why:* 1a and 2a used a different set from 3a to 7a. These three link to designed screens.

## Phase 1 sign-off

*Decided 2026-11-17.*

- **Michael signed off direction a (1a to 7a and their phone previews) as published at canvas version 77.** Phase 1 is closed and phase 2, the frontend build, is open.
  *How this shapes the product:* the signed-off designs are copied into `designs/` beside this file, and that copy is the specification the frontend follows. Its `canvas.json` maps only the 14 a boards.

## Frontend build

*Decided 2026-11-15. Answers to open questions 6 to 9. Recorded as ADR-0042 (styling), ADR-0043 (routing and routes), ADR-0044 (API-client layer and sample data), ADR-0045 (npm and Node LTS) and ADR-0046 (self-hosted Inter).*

- **Styling is plain CSS with custom properties for direction a's tokens (color, type, spacing), in light and dark themes.** No styling package.
  *Why:* a's designs are already a small token set, and a token file is simpler to restyle than a component library's theme.
- **Rejected: a styled component library such as MUI or Mantine.** Its own look would have to be overridden to match a.
- **Rejected for now: a headless library such as React Aria or Radix.** The seven screens use native links, forms, selects and disclosures; it can be proposed later if a widget needs it.
- **Rejected: a utility framework such as Tailwind.**
- **Routing uses TanStack Router with routes defined in code.**
  *Why:* typed route and search parameters help an agent-assisted workflow catch broken links at compile time. Code routes avoid the extra Vite plugin that file routes need. The router runs only in the browser, so the Java services do not affect the choice; it does not type the data those services return.
- **Rejected: React Router,** more widely documented but less type-safe. **Rejected: TanStack file routes,** which add `@tanstack/router-plugin`.
- **Screens read typed sample data through a small API-client layer,** swapped for real calls as each service adds endpoints. Pages keep the "Sample data" badge.
  *Why:* only the records service has read endpoints so far, and the screens can be built and reviewed now from the sample data already in the designs.
- **Rejected: waiting for each service's API before building its screens.**
- **npm with a committed `package-lock.json`, on the current Node LTS line.**
  *Why:* npm ships with Node, so teammates need no extra tool.
- **Rejected: pnpm.**
- **Routes,** one per screen. Path parameters and search parameters are typed:

  | Path | Screen |
  |---|---|
  | `/` | home (1a) |
  | `/search?q=&in=&year=` | search results (2a) |
  | `/issues` | browse issues (6a) |
  | `/issues/$issueId` | issue timeline (3a) |
  | `/meetings` | browse meetings (7a) |
  | `/meetings/$meetingId` | meeting (4a) |
  | `/records/$recordId?version=` | record (5a) |

  *Why:* each address follows the navigation, and search filters and a record version can be shared as links.
- **Rejected: records nested under meetings** (`/meetings/$meetingId/records/$recordId`). It makes links longer and assumes each record belongs to exactly one meeting.
- **Inter is self-hosted.** Its woff2 files, taken from the official Inter release, are committed under `frontend/public/fonts/` with the font's OFL licence. No npm package is added. `system-ui` is the fallback.
  *Why:* the screens match the designs, and visitors' browsers make no requests to Google.
- **Rejected: a Google Fonts link** (a privacy cost for a civic site), **system fonts only** (the screens would drift from the designs), and **`@fontsource/inter`** (an extra dependency).

## Sharing and storage

*Decided 2026-11-13.*

- **Shared as one claude.ai Artifact through a public link.** ~~One page with a tab for each screen and five designs per tab,~~ updated in place at the same link. *Decided 2026-11-13:* the format is the Design canvas, see below.
  *Why:* no setup, and teammates can open a public link without a Claude account.
- **Built on claude.ai's Design canvas type:** one row of five artboards per screen, added as each screen is designed.
  *Why:* the type is made for screen mockups.
- **Rejected: a hand-built tabbed HTML page.**
- **Each design has an ID (1a to 1e, 2a to 2e, and so on). Kazim sends notes by ID, and Michael makes the pick for each screen.**
  *Why:* public-link visitors cannot comment or vote on the page, and waiting for agreement on every screen would stall the work.
- **Rejected: requiring both teammates to agree before each next screen, and giving Kazim claude.ai access to comment on the page.**
- **Rejected: Cloudflare Pages.** It needs a deploy step on every update.
- **Design decisions are tracked here; draft design source stays in the ignored `.local/design/` folder** until a design is chosen.
  *Why:* the team needs to see design decisions in version control, while the candidate designs are exploration, which the repository rules keep in `.local`.

## Phone layout exceptions

*Decided 2026-11-17. Answer to open question 10.*

- **The phone month calendar on browse meetings (7a) may scroll sideways inside its own panel.** The page itself never scrolls sideways, and each month keeps a 44px target and keyboard access.
  *Why:* twelve 44px month targets cannot fit across a 390px screen, and the signed-off 7a phone design already cuts the calendar off and scrolls it within the panel.
- **Rejected: no sideways scrolling anywhere,** which would need a stacked-month calendar and would change the signed-off phone layout.

## Theme toggle

*Decided 2026-11-18. Recorded as ADR-0047.*

- **A light and dark theme toggle sits at the top right of the header on every screen.** Until a reader uses it, the page follows their system setting; their choice is then kept in that browser only.
  *Why:* mainly for development, as a quick way to check both color schemes without changing system settings. Letting readers choose a theme for the site is an additional reason.
  *How this shapes the product:* this adds a control the signed-off a designs do not have. On phone width it sits beside the site name, and the navigation wraps to the row below.
- **Rejected: following the system setting only,** as the frontend brief first specified.
