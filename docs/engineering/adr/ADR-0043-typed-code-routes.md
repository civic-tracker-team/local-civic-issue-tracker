# ADR-0043: Use TanStack Router with typed, code-defined routes

- **Status:** Accepted
- **Date:** 2026-11-15
- **Recorded:** 2026-11-18
- **Deciders:** Michael Avila
- **Related requirements:** LIM-03, IF-01, NFR-03

## Context

ADR-0005 left the routing library open. The seven screens link to each other heavily: issues to records, records to meetings, search results to all three. Search filters and a record version should be shareable as links. Much of the frontend work is agent-assisted, so broken links should fail at compile time rather than in review.

## Decision

The frontend uses TanStack Router with routes defined in code, one route per screen:

| Path | Screen |
|---|---|
| `/` | Home |
| `/search?q=&in=&year=` | Search results |
| `/issues` | Browse issues |
| `/issues/$issueId` | Issue timeline |
| `/meetings` | Browse meetings |
| `/meetings/$meetingId` | Meeting |
| `/records/$recordId?version=` | Record |

Path and search parameters are typed. Search parameters are checked by hand-written `validateSearch` functions, without a schema library. The header navigation marks the current section with `aria-current="page"`. Record pages mark none, because a record can be reached from search, an issue or a meeting.

## Alternatives considered

React Router is more widely documented but less type-safe. TanStack file-based routes need the extra `@tanstack/router-plugin` Vite plugin. Nesting records under meetings (`/meetings/$meetingId/records/$recordId`) makes links longer and assumes each record belongs to exactly one meeting. ADR-0014 keeps a record's official relationships rather than a single parent meeting.

## Consequences

- **Positive:** A link to a missing route or a mistyped parameter fails the TypeScript build. Filters and versions survive being shared as links.
- **Negative:** The router types only the browser's URLs. It does not type or validate the data the Java services return.
- **Follow-up:** A new screen needs a new route in the table and a decision to match.

## AI assistance and provenance

The options were prepared with AI assistance as open question 7. Michael chose code-defined TanStack routes and the route table on 2026-11-15. Codex (GPT-6.1 Sol) delegates implemented the router, coordinated by Claude Code.

## References

- SRS v2.0
- ADR-0005: Frontend stack
- ADR-0014: Meeting source identity
- `docs/engineering/ui-design/DECISIONS.md`, "Frontend build"
