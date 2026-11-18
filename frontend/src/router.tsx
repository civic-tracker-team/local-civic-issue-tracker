import { createRootRoute, createRoute, createRouter, Link, Outlet, useRouterState } from '@tanstack/react-router';
import type { SearchParameters } from './api';
import { ThemeToggle } from './components/ThemeToggle';
import { Home } from './screens/Home/Home';
import { Search } from './screens/Search/Search';
import { Issue } from './screens/Issue/Issue';
import { Meeting } from './screens/Meeting/Meeting';
import { Record } from './screens/Record/Record';
import { Issues } from './screens/Issues/Issues';
import { Meetings } from './screens/Meetings/Meetings';
export function validateSearch(search: Record<string, unknown>): SearchParameters {
  const raw = Array.isArray(search.in) ? search.in : typeof search.in === 'string' ? search.in.split(',') : undefined;
  const kinds: SearchParameters['in'] = raw === undefined ? ['issues', 'records', 'meetings'] : raw.filter((s): s is SearchParameters['in'][number] => s === 'issues' || s === 'records' || s === 'meetings');
  const year = typeof search.year === 'number' ? search.year : typeof search.year === 'string' && /^\d{4}$/.test(search.year) ? Number(search.year) : undefined;
  return { q: typeof search.q === 'string' ? search.q : '', in: [...new Set(kinds)], year: year !== undefined && Number.isInteger(year) && year >= 1000 && year <= 9999 ? year : undefined };
}
export function validateRecordSearch(search: Record<string, unknown>): { version: string | undefined } {
  return { version: typeof search.version === 'string' && search.version.trim() ? search.version : typeof search.version === 'number' && Number.isInteger(search.version) && search.version > 0 ? String(search.version) : undefined };
}
function Layout() {
  const path = useRouterState({ select: s => s.location.pathname });
  // Records belong to no single section (they are reached from search, issues and meetings), so none is marked current there.
  const section = path.startsWith('/issues') ? 'issues' : path.startsWith('/meetings') ? 'meetings' : path.startsWith('/records') ? null : 'search';
  return <><a className="skip-link" href="#main">Skip to main content</a><header className="app-header"><div className={`header-inner${path === "/" || path === "/search" ? " header-inner--narrow" : ""}`}><Link className="brand" to="/"><svg width="22" height="22" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" aria-hidden="true"><path d="M5 4v16M5 8h9M5 14h13"/><circle cx="17" cy="8" r="2"/></svg>Local Civic Issue Tracker</Link><nav className="main-nav" aria-label="Main"><Link to="/" aria-current={section === 'search' ? 'page' : undefined}>Search</Link><Link to="/issues" aria-current={section === 'issues' ? 'page' : undefined}>Issues</Link><Link to="/meetings" aria-current={section === 'meetings' ? 'page' : undefined}>Meetings</Link><span className="sample-badge">Sample data</span></nav><ThemeToggle/></div></header><main id="main" tabIndex={-1} className="app-main"><Outlet /></main></>;
}
const root = createRootRoute({ component: Layout, notFoundComponent: () => <h1>Page not found</h1> });
const home = createRoute({ getParentRoute: () => root, path: '/', component: Home });
const search = createRoute({ getParentRoute: () => root, path: '/search', validateSearch, component: Search });
const issues = createRoute({ getParentRoute: () => root, path: '/issues', component: Issues });
const issue = createRoute({ getParentRoute: () => root, path: '/issues/$issueId', component: Issue });
const meetings = createRoute({ getParentRoute: () => root, path: '/meetings', component: Meetings });
const meeting = createRoute({ getParentRoute: () => root, path: '/meetings/$meetingId', component: Meeting });
const record = createRoute({ getParentRoute: () => root, path: '/records/$recordId', validateSearch: validateRecordSearch, component: Record });
export const router = createRouter({ routeTree: root.addChildren([home, search, issues, issue, meetings, meeting, record]) });
declare module '@tanstack/react-router' { interface Register { router: typeof router } }
