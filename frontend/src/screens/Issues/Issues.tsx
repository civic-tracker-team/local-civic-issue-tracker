import { useEffect, useState } from 'react';
import { Link } from '@tanstack/react-router';
import { getCoverage, listIssues, type Coverage, type Issue, type IssueListing } from '../../api';
import { BasisTag } from '../../components';
import './Issues.css';
const months = ['Jan','Feb','Mar','Apr','May','Jun','Jul','Aug','Sep','Oct','Nov','Dec'];
// Compare only the month precision actually supplied in the listing.
const spanMonth = (issue: Issue, end: boolean) => { const part = issue.span.split(' – ')[end ? 1 : 0] || ''; const [month, year] = part.split(' '); return Number(year) * 12 + months.indexOf(month || ''); };
export function Issues() {
  const [data, setData] = useState<{ listing: IssueListing; coverage: Coverage }>();
  const [failed, setFailed] = useState(false);
  const [name, setName] = useState(''); const [activity, setActivity] = useState('Any time'); const [sort, setSort] = useState('Most recent activity');
  useEffect(() => { let live = true; Promise.all([listIssues(), getCoverage()]).then(([listing, coverage]) => { if (live) setData({ listing, coverage }); }).catch(() => { if (live) setFailed(true); }); return () => { live = false; }; }, []);
  if (failed) return <section className="issues-screen"><h1>Issues</h1><p role="alert">Issues could not be loaded. Please try again.</p></section>;
  if (!data) return <section className="issues-screen" aria-busy="true"><p role="status">Loading issues…</p></section>;
  const all = data.listing.groups.flatMap(g => g.items);
  const latest = Math.max(...all.map(i => spanMonth(i, true)));
  const filtered = all.filter(i => i.name.toLowerCase().includes(name.trim().toLowerCase()) && (activity === 'Any time' || (activity === 'Past 3 months' ? spanMonth(i, true) >= latest - 2 : Math.floor(spanMonth(i, true) / 12) === Number(activity))));
  const sorted = [...filtered].sort((a,b) => sort === 'A–Z' ? a.name.localeCompare(b.name) : sort === 'Most meetings' ? b.meetingCount - a.meetingCount : sort === 'First mentioned' ? spanMonth(a, false) - spanMonth(b, false) : spanMonth(b, true) - spanMonth(a, true));
  const groups = sort === 'Most recent activity' ? data.listing.groups.map(g => ({label:g.label, items:sorted.filter(i => i.group === g.label)})) : [{label:sort,items:sorted}];
  return <section className="issues-screen">
    <div className="issues-intro"><h1>Issues</h1><p>{data.listing.total} issues followed across City Council records from {data.coverage.range.replace(' – ', ' to ')}, most recently active first.</p></div>
    <form aria-label="Filter issues" onSubmit={e => e.preventDefault()}>
      <label className="issues-name">Filter by name<input type="search" placeholder="e.g. parking" value={name} onChange={e => setName(e.target.value)} /></label>
      <label>Last activity<select aria-label="Last activity" value={activity} onChange={e => setActivity(e.target.value)}>{['Any time','Past 3 months','2026','2025','2024','2023'].map(v => <option key={v}>{v}</option>)}</select></label>
      <label>Sort<select aria-label="Sort" value={sort} onChange={e => setSort(e.target.value)}>{['Most recent activity','Most meetings','A–Z','First mentioned'].map(v => <option key={v}>{v}</option>)}</select></label>
    </form>
    {activity === 'Past 3 months' && <p className="issues-sample-note">Past 3 months is relative to the latest activity in these sample records.</p>}
    {groups.filter(g => g.items.length).map(g => <section key={g.label} aria-label={g.label}><h2>{g.label}</h2><ul>{g.items.map(i => <li key={i.id}><div className="issues-detail"><Link className="tap-target" to="/issues/$issueId" params={{ issueId:i.id }}>{i.name}</Link><span className="issues-latest">{i.latest} {i.basis === 'inferred' ? <BasisTag basis="inferred" /> : i.tag && <span className={`issues-status issues-status--${i.status}`}>{i.tag}</span>}</span></div><span className="issues-meta"><span>{i.meetingCount} meetings</span> · <span>{i.span}</span></span></li>)}</ul></section>)}
    {!sorted.length && <p role="status">No sample issues match these filters.</p>}
    <div className="issues-pagination"><span aria-live="polite">Showing {sorted.length} of {data.listing.total}</span><span className="issues-more" aria-disabled="true">Show more issues</span></div>
    {data.listing.sampleSubset && <p className="issues-sample-note">Only {all.length} sample issues are available. More issues cannot be loaded in this sample.</p>}
  </section>;
}
