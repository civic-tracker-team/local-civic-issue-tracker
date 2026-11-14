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
