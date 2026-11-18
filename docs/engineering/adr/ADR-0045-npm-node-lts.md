# ADR-0045: Use npm with a committed lockfile on the current Node LTS line

- **Status:** Accepted
- **Date:** 2026-11-15
- **Recorded:** 2026-11-18
- **Deciders:** Michael Avila
- **Related requirements:** LIM-03

## Context

The frontend needs a package manager and a Node version every teammate can reproduce without extra setup.

## Decision

The frontend uses npm with a committed `frontend/package-lock.json`. `package.json` names the current Node LTS line in `engines` (Node 26 when recorded) and pins exact package versions. Its scripts are `dev`, `build` (TypeScript type-check, then `vite build`) and `preview`.

Packages are limited to an agreed list: `react`, `react-dom`, `@types/react`, `@types/react-dom`, `typescript`, `vite`, `@vitejs/plugin-react` and `@tanstack/react-router`. Any other package, including linters, test runners and schema libraries, needs the team's agreement first.

## Alternatives considered

pnpm is faster and stricter about dependencies, but every teammate would need another tool. npm ships with Node.

## Consequences

- **Positive:** `npm ci` reproduces the same dependency tree on any machine with the current Node LTS.
- **Negative:** `npm audit` reports advisories in the build tooling (via `picomatch` in Vite) when recorded. They affect development tools only, not the pages visitors load, and are left until upstream fixes land.
- **Follow-up:** Testing and hosting are not chosen yet. The `engines` line moves with Node's LTS schedule.

## AI assistance and provenance

The options were prepared with AI assistance as open question 9. Michael chose npm on the current Node LTS line on 2026-11-15. A Codex (GPT-6.1 Sol) delegate checked Node's release schedule and set up the project, coordinated by Claude Code.

## References

- SRS v2.0
- ADR-0005: Frontend stack
