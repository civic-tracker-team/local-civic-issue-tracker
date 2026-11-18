import { useEffect, useState } from 'react';
import { Link, useNavigate, useSearch } from '@tanstack/react-router';
import { getCoverage, listIssues, search, type Coverage, type SearchKind, type SearchResults } from '../../api';
import { BasisTag } from '../../components';
import './Search.css';

export function Search() {
  const query = useSearch({ from: '/search' });
  const navigate = useNavigate();
  const [q, setQ] = useState(query.q);
  const [data, setData] = useState<{ results: SearchResults; coverage: Coverage; issueNames: Record<string, string> }>();
  const [failed, setFailed] = useState(false);
  useEffect(() => { setQ(query.q); }, [query.q]);
  useEffect(() => {
    let active = true;
    setFailed(false); setData(undefined);
    Promise.all([search(query), getCoverage(), listIssues()]).then(([results, coverage, listing]) => {
      const issueNames = Object.fromEntries(listing.groups.flatMap(group => group.items.map(issue => [issue.id, issue.name])));
      if (active) setData({ results, coverage, issueNames });
    }).catch(() => { if (active) setFailed(true); });
    return () => { active = false; };
  }, [query.q, query.in.join(','), query.year]);
  const updateKinds = (kind: SearchKind, checked: boolean) => { void navigate({ to: '/search', search: { ...query, in: checked ? [...query.in, kind] : query.in.filter(k => k !== kind) } }); };
  const total = data ? Object.values(data.results.totals).reduce((sum, n) => sum + n, 0) : 0;
  return <div className="search-screen">
    <div className="search-top"><h1>{query.q ? `Results for “${query.q}”` : 'Search the collection'}</h1>
      <form role="search" onSubmit={e => { e.preventDefault(); void navigate({ to: '/search', search: { ...query, q } }); }}>
        <label htmlFor="search-query">Search issues, records and meetings</label>
        <div className="search-query-row"><input id="search-query" type="search" value={q} onChange={e => setQ(e.target.value)}/><button type="submit">Search</button></div>
        <div className="search-filters"><fieldset><legend>Show</legend>{(['issues', 'records', 'meetings'] as const).map(kind => <label key={kind}><input type="checkbox" checked={query.in.includes(kind)} onChange={e => updateKinds(kind, e.target.checked)}/>{kind.charAt(0).toUpperCase() + kind.slice(1)}{data ? ` (${data.results.totals[kind]})` : ''}</label>)}</fieldset>
          <label className="search-year">Meeting year<select value={query.year ?? ''} onChange={e => { void navigate({ to: '/search', search: { ...query, year: e.target.value ? Number(e.target.value) : undefined } }); }}><option value="">Any</option>{(data ? [...new Set(data.coverage.months.map(m => m.year))].sort((a, b) => b - a) : query.year ? [query.year] : []).map(year => <option key={year} value={year}>{year}</option>)}</select></label>
        </div>
      </form>
    </div>
    {failed ? <p role="alert">The sample results could not be loaded. Please reload to try again.</p> : !data ? <p role="status">Loading results…</p> : <>
      <p role="status" className="search-notice">{total} results from the imported collection only: {data.coverage.authority} records, {data.coverage.range}. Records outside it can't appear here. <a href="#search-coverage">What's included</a></p>
      {total === 0 && <p>No matching samples. Try different words, a different meeting year or selecting more record types.</p>}
      {query.in.includes('issues') && data.results.totals.issues > 0 && <section className="search-issues" aria-labelledby="search-issues-heading"><h2 id="search-issues-heading">Issues · {data.results.totals.issues}</h2>{data.results.issues.map(issue => <article key={issue.id}><Link className="tap-target" to="/issues/$issueId" params={{ issueId: issue.id }}>{issue.name}</Link><p>{issue.previewSummary ?? issue.summary}</p><span>{issue.meetingCount} meetings · {issue.span}</span></article>)}</section>}
      {query.in.includes('records') && data.results.totals.records > 0 && <section className="search-records" aria-labelledby="search-records-heading"><h2 id="search-records-heading">Records · {data.results.totals.records}</h2>{data.results.records.map(record => <article key={record.id}>
        <span className="search-record-meta">{record.kind} · {record.sourceMeetingDate}</span>
        <Link className="search-record-title tap-target" to="/records/$recordId" params={{ recordId: record.id }} search={{ version: undefined }}>{record.matchTitle ?? record.title}</Link>
        <p>…{record.excerpt.before}<mark>{record.excerpt.highlight}</mark>{record.excerpt.after}…</p>
        <div className="search-record-links"><span className={record.issueId ? '' : 'search-unlinked'}>{record.issueId ? `Part of: ${data.issueNames[record.issueId] ?? record.issueId}` : 'Not linked to any issue yet'}</span>{record.basis && <BasisTag basis={record.basis}/>}<span className="search-original" aria-disabled="true">Original document unavailable in sample</span></div>
      </article>)}{data.results.records.length < data.results.totals.records && <p className="search-subset">Showing {data.results.records.length} of {data.results.totals.records} records. Only these preview records are available in the sample.</p>}</section>}
      {query.in.includes('meetings') && data.results.totals.meetings > 0 && <section className="search-meetings" aria-labelledby="search-meetings-heading"><h2 id="search-meetings-heading">Meetings · {data.results.totals.meetings}</h2>{data.results.meetings.map(meeting => <div key={meeting.id}><Link className="tap-target" to="/meetings/$meetingId" params={{ meetingId: meeting.id }}>{meeting.dateLabel} · {meeting.title}</Link><span>{meeting.matchingAgendaItems} matching agenda item{meeting.matchingAgendaItems === 1 ? '' : 's'}</span></div>)}{data.results.meetings.length < data.results.totals.meetings && <p className="search-subset">Showing {data.results.meetings.length} of {data.results.totals.meetings} meetings. Only these preview meetings are available in the sample.</p>}</section>}
      <details id="search-coverage" className="search-coverage"><summary>What’s included in this collection</summary><p>{data.coverage.authority}, {data.coverage.range}: {data.coverage.recordTypes.join(', ')}. {data.coverage.scheduled} scheduled meetings ({data.coverage.held} held, {data.coverage.cancelled} cancelled), plus {data.coverage.upcoming} upcoming. This screen shows sample records only.</p></details>
    </>}
  </div>;
}
