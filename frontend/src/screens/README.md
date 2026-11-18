# Screen ownership and shared contracts

Each builder edits **only its assigned screen folders**, including the component and its CSS. Keep the named export and filenames below so the route imports remain valid. A needed shared change belongs in the builder's report; do not edit the router, API, components, global CSS, configuration, or another screen's files. Prefix screen CSS selectors with the screen's root class to prevent cross-screen effects.

| Folder / export | Design | Route |
| --- | --- | --- |
| `Home/` → `Home` | Home1a | `/` |
| `Search/` → `Search` | Search2a | `/search` |
| `Issue/` → `Issue` | Issue3a | `/issues/$issueId` |
| `Meeting/` → `Meeting` | Meeting4a | `/meetings/$meetingId` |
| `Record/` → `Record` | Record5a | `/records/$recordId` |
| `Issues/` → `Issues` | Issues6a | `/issues` |
| `Meetings/` → `Meetings` | Meetings7a | `/meetings` |

The shell owns the header, navigation, sample badge, skip link and `main#main`. Screen components render inside that main: do not add another main/header/global nav. The designs have different screen footers and coverage treatments, so those belong in the relevant screen. No footer or coverage block occurs on every board.

Read the signed-off HTML in `docs/engineering/ui-design/designs/` for exact static headings, labels, explanatory text, and control options. API content covers its sample entities. Use plain React text nodes for source passages, never `dangerouslySetInnerHTML`.

## API (import from `../../api`)

```ts
getCoverage(): Promise<Coverage>
getHome(): Promise<HomeData>
listIssues(): Promise<IssueListing>
getIssue(issueId: string): Promise<Issue | null>
listIssueRecords(issueId: string): Promise<CivicRecord[]>
listMeetings(): Promise<MeetingListing>
getMeeting(meetingId: string): Promise<Meeting | null>
getRecord(recordId: string): Promise<CivicRecord | null>
getRecordVersion(recordId: string, version?: string): Promise<Version | null>
search(query: SearchParameters): Promise<SearchResults>
```

All types are exported from the same barrel; their field definitions are in `api/types.ts`. Components call async functions with React effects or route hooks. Handle loading, null and failure locally. Responses are cloned so component changes cannot mutate the fixture store. Only the client imports `sample.ts`; screens must never import it.

`Basis` is `'stated' | 'inferred'`, separate from a development's documented/proposed/planned/unknown/conflict status. `Evidence` retains source passage, record/version, explanation and optional video time. `Development` separates occurrence/display date, source meeting date and publication date. Each `TemporalDate` retains role, precision, source label and note. Approximate dates and ranges have no fabricated exact endpoints. Preserve the range ordering note and the expected state of late 2027. `conflicts` exposes both budget claims and identifies the newer account; it is not an explicit correction.

`CivicRecord` is a browser sample view, not the records service's authenticated internal `Document` response. Multiple `meetingIds` preserve its many-context semantics. `Version` separates publication, correction and retrieval dates. The two sample minute versions have the same displayed page 6 excerpts; the correction concerns attendance in item 4, which the boards do not excerpt. Missing official URLs/video URLs are `null`: do not make up city links. Show the local source record when available; disable/explain original-file actions until sample originals exist. Records with no excerpted version return null from `getRecordVersion`.

Coverage is one source: monthly held/cancelled/upcoming data yields 92 scheduled, 90 held, 2 cancelled and 1 upcoming. Read it through `getCoverage()` everywhere; do not duplicate figures/month arrays. `month` is one-based. Home uses ascending years, browse meetings descending. Use `--heat*` tokens for the home strip, `--h*` for browse meetings. The 2-meeting cell text color from 7a is `#0B3B36` in light and `var(--ink)` in dark.

Fixtures contain the rows visible on the boards, not 48 fabricated issues, 41 fabricated search records, or the full 92-meeting archive. `sampleSubset` flags that distinction. The canonical search `parking downtown` (also `downtown parking`) has the design's 2/41/12 totals and visible preview rows. Other queries filter available samples and report their actual counts. Do not promise additional hidden fixture pages. Search meeting match notes on 2a are 2 for Oct 15, 1 for Jul 2 and 3 for Mar 6.

Useful sample IDs:

- Issue: `downtown-parking-structure`; other issue IDs are lowercase hyphenated names (all ten browse rows exist).
- Meetings: `2026-10-15`, `2026-10-01`, `2026-11-05`, `2026-09-17`, `2026-09-03` (cancelled), `2025-07-02`, `2024-03-06`.
- Record: `minutes-2026-10-15`, versions `1` and `2` (default). Also `parking-study`, `parking-citation-fees` (unlinked), `design-services-scope`, and source records named in timeline evidence.

## Router hooks and links

```tsx
import { Link, useParams, useSearch } from '@tanstack/react-router';
const { issueId } = useParams({ from: '/issues/$issueId' });
const query = useSearch({ from: '/search' });
const { recordId } = useParams({ from: '/records/$recordId' });
const { version } = useSearch({ from: '/records/$recordId' });
<Link to="/issues/$issueId" params={{ issueId: 'downtown-parking-structure' }}>Issue</Link>
<Link to="/search" search={{ q: 'parking downtown', in: ['issues', 'records', 'meetings'], year: undefined }}>Search</Link>
```

Search parameters: `{ q: string; in: SearchKind[]; year: number | undefined }`. The handwritten validator accepts comma-separated or array `in`, deduplicates allowed types, defaults absent `in` to all types, and preserves an explicit empty selection. Invalid years become undefined. Record search: `{ version: string | undefined }`; omit for current version. Use the same `from` pattern for meeting parameters. Do not import route instances from `router.tsx` (that would create a screen/router cycle).

## Shared components (import from `../../components`)

```ts
BasisTag(props: { basis: Basis }): ReactElement
PageHeading(props: { title: string; children?: ReactNode }): ReactElement
CoverageNotice(props: { coverage: Coverage; children?: ReactNode }): ReactElement
EvidencePassage(props: { evidence: Evidence; initiallyOpen?: boolean }): ReactElement
```

`BasisTag` always renders “Stated” or “Inferred link · may be incorrect”. `PageHeading` renders the h1 and optional supporting content. `CoverageNotice` renders collection scope and figures with space for a coverage grid. `EvidencePassage` renders a native source disclosure with a local source-record link and inference explanation. Supply `initiallyOpen` for the initially expanded inference on 3a.

Global styles load Inter locally, set base typography/forms and the 3px focus ring in both themes. The dark tokens apply when the system is dark and `<html data-theme>` is not `light`, or when `data-theme` is `dark` (set by the header's `ThemeToggle`); screen CSS that needs a dark-only rule must use the same two selectors, as `Meetings.css` does. Color tokens retain `renderVals()` names converted to kebab-case (`--stated-bg`, `--on-accent`, etc.). `--timeline-line` retains 3a's darker divider, while `--line` is the common divider. `.tap-target` gives standalone links a 44px phone target; inline sentence links are exempt. Add screen-specific forms/labels and control layouts in your own CSS. Native controls need accessible labels, and targets must stay at least 44px at 390px.
