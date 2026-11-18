import { Fragment, useEffect, useState } from 'react';
import { Link, useNavigate } from '@tanstack/react-router';
import { getCoverage, getHome, type Coverage, type HomeData, type SearchKind } from '../../api';
import { BasisTag } from '../../components';
import './Home.css';

export function Home() {
  const navigate = useNavigate();
  const [data, setData] = useState<{ home: HomeData; coverage: Coverage }>();
  const [failed, setFailed] = useState(false);
  const [q, setQ] = useState('');
  const [kinds, setKinds] = useState<SearchKind[]>(['issues', 'records', 'meetings']);
  useEffect(() => {
    let active = true;
    Promise.all([getHome(), getCoverage()]).then(([home, coverage]) => { if (active) setData({ home, coverage }); }).catch(() => { if (active) setFailed(true); });
    return () => { active = false; };
  }, []);
  if (failed) return <section className="home-screen"><h1>Home unavailable</h1><p role="alert">The sample collection could not be loaded. Please reload to try again.</p></section>;
  if (!data) return <section className="home-screen"><p role="status">Loading the sample collection…</p></section>;
  const { home, coverage } = data;
  const years = [...new Set(coverage.months.map(m => m.year))].sort();
  return <div className="home-screen">
    <section className="home-intro" aria-labelledby="home-heading">
      <h1 id="home-heading">Follow a City Council issue across meetings</h1>
      <p>Search in your own words. You don't need a meeting date or the city's official terms.</p>
      <form role="search" onSubmit={event => { event.preventDefault(); void navigate({ to: '/search', search: { q, in: kinds, year: undefined } }); }}>
        <label htmlFor="home-query">Search issues, records and meetings</label>
        <div className="home-query-row"><div className="home-query-field"><svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" aria-hidden="true"><circle cx="11" cy="11" r="7"/><path d="m20 20-3.5-3.5"/></svg><input id="home-query" type="search" value={q} onChange={e => setQ(e.target.value)} placeholder="Try “parking structure” or “rental permits”"/></div><button type="submit">Search</button></div>
        <fieldset><legend>Look in</legend>{(['issues', 'records', 'meetings'] as const).map(kind => <label key={kind}><input type="checkbox" checked={kinds.includes(kind)} onChange={e => setKinds(e.target.checked ? [...kinds, kind] : kinds.filter(k => k !== kind))}/>{kind.charAt(0).toUpperCase() + kind.slice(1)}</label>)}</fieldset>
        <div className="home-tries"><span>Try</span>{home.tries.map(term => <Link key={term} to="/search" search={{ q: term, in: kinds, year: undefined }}>{term}</Link>)}</div>
      </form>
    </section>
    <section className="home-coverage" aria-labelledby="home-coverage-heading">
      <div className="home-section-heading"><h2 id="home-coverage-heading">What this collection covers</h2><a className="tap-target" href="#home-records-details">About the records</a></div>
      <p id="home-records-details">{coverage.authority}, <strong>{coverage.range}</strong>: {coverage.recordTypes.slice(0, -1).join(', ')} and {coverage.recordTypes.at(-1)} from {coverage.scheduled} scheduled meetings ({coverage.held} held, {coverage.cancelled} cancelled). Anything outside this set won't appear in results.</p>
      <div className="home-heat" role="img" aria-label={`Meetings held per month, ${coverage.range}. ${coverage.scheduled} scheduled, ${coverage.held} held, ${coverage.cancelled} cancelled; ${coverage.upcoming} upcoming. ${years.map(year => `${year}: ${coverage.months.filter(m => m.year === year).map(m => `${m.month}: ${m.held} held${m.cancelled ? ', cancelled meeting' : ''}${m.upcoming ? ', upcoming' : ''}`).join('; ')}`).join('. ')}`}>
        <span/>{['J', 'F', 'M', 'A', 'M', 'J', 'J', 'A', 'S', 'O', 'N', 'D'].map((month, i) => <span key={i}>{month}</span>)}
        {years.map(year => <Fragment key={year}><strong>{year}</strong>{coverage.months.filter(m => m.year === year).map(m => <span key={m.month} className={`home-heat-cell${m.cancelled ? ' is-cancelled' : ''}${m.upcoming ? ' is-upcoming' : ''}${m.notYet ? ' is-future' : ''}`} style={{ background: m.upcoming || m.notYet ? 'transparent' : `var(--heat${Math.min(m.held, 3)})` }}/>)}</Fragment>)}
      </div>
      <div className="home-legend" aria-hidden="true"><span>Meetings held: fewer</span><i className="heat1"/><i className="heat2"/><i className="heat3"/><span>more</span><i className="cancelled"/><span>a meeting was cancelled</span><i className="upcoming"/><span>upcoming</span></div>
    </section>
    <section className="home-latest" aria-labelledby="home-latest-heading"><div className="home-section-heading"><h2 id="home-latest-heading">Latest developments</h2><Link className="tap-target" to="/issues">All issues</Link></div>
      {home.days.map(day => <div className="home-day" key={day.meetingId}><div className="home-date"><strong>{day.date}</strong><Link className="tap-target" to="/meetings/$meetingId" params={{ meetingId: day.meetingId }}>{day.meeting}</Link></div><ul>{day.items.map(item => <li key={item.issueId}><Link className="tap-target home-issue" to="/issues/$issueId" params={{ issueId: item.issueId }}>{item.issue}</Link><span className="home-development">{item.text}</span>{item.basis ? <BasisTag basis={item.basis}/> : <span className={`home-status home-status--${item.status}`}>{item.tag}</span>}</li>)}</ul></div>)}
    </section>
    <footer>Summaries are written from the official records and link back to them. Results cover only the records imported into this collection; if something isn't here, the city may still have discussed it.</footer>
  </div>;
}
