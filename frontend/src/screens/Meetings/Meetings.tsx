import { Fragment, useEffect, useRef, useState } from 'react';
import { Link } from '@tanstack/react-router';
import { getCoverage, listMeetings, type Coverage, type MeetingListing } from '../../api';
import './Meetings.css';
const monthNames = ['Jan','Feb','Mar','Apr','May','Jun','Jul','Aug','Sep','Oct','Nov','Dec'];
const monthLabel = (year: number, month: number) => new Intl.DateTimeFormat('en-US', { month:'long', year:'numeric', timeZone:'UTC' }).format(new Date(Date.UTC(year, month - 1, 1)));
export function Meetings() {
  const [data, setData] = useState<{ coverage: Coverage; listing: MeetingListing }>(); const [failed, setFailed] = useState(false);
  const [selected, setSelected] = useState<string | null>(null); const selection = useRef<HTMLDivElement>(null);
  useEffect(() => { let live = true; Promise.all([getCoverage(),listMeetings()]).then(([coverage,listing]) => { if (live) setData({coverage,listing}); }).catch(() => { if (live) setFailed(true); }); return () => { live = false; }; }, []);
  useEffect(() => { if (selected) selection.current?.focus(); }, [selected]);
  if (failed) return <section className="meetings-screen"><h1>Meetings</h1><p role="alert">Meetings could not be loaded. Please try again.</p></section>;
  if (!data) return <section className="meetings-screen" aria-busy="true"><p role="status">Loading meetings…</p></section>;
  const { coverage, listing } = data;
  const years = [...new Set(coverage.months.map(m => m.year))].sort((a,b) => b-a);
  const visible = selected ? listing.months.filter(m => m.label === selected) : listing.months;
  return <section className="meetings-screen">
    <div className="meetings-intro"><h1>Meetings</h1><p>{coverage.scheduled} City Council meetings were scheduled from {coverage.range.replace(' – ', ' to ')}: {coverage.held} held and {coverage.cancelled} cancelled. {coverage.upcoming} more is upcoming. Pick a month, or scroll the list.</p></div>
    <section className="meetings-calendar" aria-labelledby="meetings-calendar-heading"><h2 id="meetings-calendar-heading">Meetings by month</h2>
      <div className="meetings-grid-scroll"><div className="meetings-grid"><span />{monthNames.map(m => <span key={m} className="meetings-month-name">{m}</span>)}<span className="meetings-held">Held</span>
        {years.map(year => <Fragment key={year}><span className="meetings-year">{year}</span>{coverage.months.filter(m => m.year === year).sort((a,b) => a.month-b.month).map(m => {
          const label = monthLabel(m.year,m.month); const accessible = `${label}: ${m.notYet ? 'not yet' : m.upcoming ? `${m.upcoming} upcoming meeting` : `${m.held} meeting${m.held === 1 ? '' : 's'} held`}${m.cancelled ? `, ${m.cancelled} cancelled` : ''}`;
          return m.notYet ? <span className="meetings-cell meetings-cell--future" key={m.month} aria-label={accessible} /> : <button type="button" key={m.month} className={`meetings-cell meetings-cell--${Math.min(m.held,3)}${m.upcoming ? ' meetings-cell--upcoming' : ''}${m.cancelled ? ' meetings-cell--cancelled' : ''}`} aria-label={accessible} aria-pressed={selected === label} onClick={() => setSelected(label)}>{m.upcoming ? '' : m.held}</button>;
        })}<span className="meetings-held meetings-year">{coverage.months.filter(m => m.year === year).reduce((n,m) => n + m.held,0)}</span></Fragment>)}
      </div></div>
      <p>Number = meetings held that month; cancelled and upcoming meetings are not counted. Dashed = a meeting that month was cancelled ({coverage.cancelled} in all). Outlined = upcoming ({coverage.upcoming}).</p>
    </section>
    {selected && <div ref={selection} tabIndex={-1} className="meetings-selection"><p role="status">{selected}{!visible.length && ': no individual meeting entries available in this sample. Coverage counts are shown above.'}</p><button type="button" onClick={() => setSelected(null)}>Show all sample meetings</button></div>}
    {visible.map(mo => <section key={mo.label} aria-label={mo.label}><h2 className="meetings-group-heading">{mo.label}</h2>{mo.items.map(m => <div className="meetings-row" key={m.id}><div className="meetings-date"><Link className="tap-target" to="/meetings/$meetingId" params={{meetingId:m.id}}>{m.dateLabel.replace(/, \d{4}$/, '')}</Link><span className={`meetings-status meetings-status--${m.status}`}>{m.status === 'held' ? m.title : m.status === 'upcoming' ? 'Upcoming' : 'Cancelled'}</span></div><div className="meetings-description"><span>{m.listingSummary ?? m.summary}</span><span>{m.recordsLabel}</span></div></div>)}</section>)}
  </section>;
}
