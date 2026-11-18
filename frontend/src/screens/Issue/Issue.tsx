import { useEffect, useState } from 'react';
import { Link, useParams } from '@tanstack/react-router';
import { getIssue, listIssueRecords, type Issue as IssueData, type CivicRecord, type Development } from '../../api';
import { BasisTag, EvidencePassage, PageHeading } from '../../components';
import './Issue.css';

function DevelopmentTag({ development: d }: { development: Development }) {
  return d.status === 'documented' ? <BasisTag basis={d.basis} /> : <span className={`issue-tag issue-tag--${d.status}`}>{d.tag}</span>;
}

function TimelineStrip({ developments }: { developments: Development[] }) {
  // These are schematic placements of source labels, not normalized occurrence dates.
  // Coarse dates remain bars; their endpoints must not be presented as exact days.
  const years = developments.flatMap(d => d.date.label.match(/20\d{2}/g) ?? []).map(Number);
  const first = Math.min(...years), last = Math.max(...years);
  const months = ['Jan', 'Feb', 'Mar', 'Apr', 'May', 'Jun', 'Jul', 'Aug', 'Sep', 'Oct', 'Nov', 'Dec'];
  const position = (d: Development) => {
    const year = Number(d.date.label.match(/20\d{2}/)?.[0]);
    const namedMonth = months.findIndex(m => d.date.label.startsWith(m));
    const offset = namedMonth >= 0 ? namedMonth / 12 : d.date.label.startsWith('Spring') ? 2 / 12 : d.date.label.startsWith('Late') ? 10 / 12 : .5;
    const day = d.date.precision === 'day' ? Number(d.date.label.match(/ (\d{1,2}),/)?.[1] ?? 1) : 1;
    return ((year - first + offset + (day - 1) / 360) / (last - first + 1)) * 100;
  };
  const expected = developments.find(d => d.date.role === 'expected');
  const latest = developments.filter(d => d.date.role !== 'expected').at(-1);
  return <section className="issue-strip" aria-labelledby="issue-span"><h2 id="issue-span">At a glance: {developments[0]?.date.label.replace(/ \d{1,2},/, '') ?? first} to {expected?.date.label.toLowerCase() ?? last}</h2>
    <div className="issue-axis" role="img" aria-label={`${developments.length} developments. Approximate dates and ranges are bars, positioned schematically. ${developments.map(d => `${d.date.label}: ${d.text} ${d.date.note}`).join(' ')}`}>
      <span className="issue-axis-past" style={{ width: `${latest ? position(latest) : 100}%` }} />
      {expected && <span className="issue-axis-future" style={{ left: `${latest ? position(latest) : 0}%` }} />}
      {Array.from({ length: last - first + 1 }, (_, i) => <span key={i} className="issue-year" style={{ left: `${i / (last - first + 1) * 100}%` }}>{first + i}</span>)}
      {[...developments].sort((a, b) => Number(['range', 'approximate'].includes(b.date.precision)) - Number(['range', 'approximate'].includes(a.date.precision))).map(d => <span key={d.id} title={`${d.date.label}: ${d.text}`} className={`issue-mark issue-mark--${d.status} ${d.basis === 'inferred' ? 'issue-mark--inferred' : ''} ${d.date.role !== 'expected' && ['range', 'approximate'].includes(d.date.precision) ? 'issue-mark--range' : ''}`} style={{ left: `${position(d)}%` }} />)}
    </div>
    <p>Dots follow the key below. Bars are approximate dates or ranges; the dotted line is still to come.</p>
  </section>;
}

export function Issue() {
  const { issueId } = useParams({ from: '/issues/$issueId' });
  const [data, setData] = useState<{ issue: IssueData | null; records: CivicRecord[] }>();
  const [failed, setFailed] = useState(false);
  useEffect(() => { let active = true; setData(undefined); setFailed(false); Promise.all([getIssue(issueId), listIssueRecords(issueId)]).then(([issue, records]) => { if (active) setData({ issue, records }); }).catch(() => { if (active) setFailed(true); }); return () => { active = false; }; }, [issueId]);
  if (failed) return <section className="issue-screen"><PageHeading title="Unable to load issue" /><p role="alert">Please try this page again.</p></section>;
  if (!data) return <section className="issue-screen" aria-busy="true"><p role="status">Loading issue…</p></section>;
  if (!data.issue) return <section className="issue-screen"><PageHeading title="Issue not found" /><Link className="tap-target" to="/issues">All issues</Link></section>;
  const { issue, records } = data;
  const observed = issue.developments.filter(d => d.date.role !== 'expected');
  const finalSentence = 'Construction has not been approved.';
  return <section className="issue-screen">
    <div className="issue-heading"><Link className="tap-target" to="/issues">All issues</Link><h1>{issue.name}</h1>
      <p className="issue-meta">{issue.meetingCount} meetings{issue.recordCount !== null && ` · ${issue.recordCount} records`}{observed.length > 0 && ` · first mentioned ${observed[0]?.date.label} · latest ${observed.at(-1)?.date.label}`}</p>
      <p className="issue-summary">{issue.summary.endsWith(finalSentence) ? <>{issue.summary.slice(0, -finalSentence.length)}<strong>{finalSentence}</strong></> : issue.summary}</p>
      <p className="issue-caption">Summary written from the records below. Each development links to its source passage.</p>
    </div>
    {issue.developments.length > 0 ? <><TimelineStrip developments={issue.developments} />
    <div className="issue-key" aria-label="How to read this timeline"><strong>Key</strong><span><BasisTag basis="stated" /> the record says so</span><span><BasisTag basis="inferred" /> linked by the system</span><span><span className="issue-tag issue-tag--proposed">Proposed</span> a recommendation, not an action</span><span><span className="issue-tag issue-tag--planned">Expected</span> planned; hasn’t happened</span><span><span className="issue-tag issue-tag--conflict">Sources disagree</span> both sources shown</span></div>
    <ol className="issue-developments">{issue.developments.map(d => <li key={d.id}>
      <div className="issue-date"><span>{d.date.label}</span><small>{d.date.note}</small></div>
      <div className={`issue-development issue-development--${d.status} ${d.basis === 'inferred' ? 'issue-development--inferred' : ''}`}><span className="issue-dot" aria-hidden="true" /><p>{d.text}</p><DevelopmentTag development={d} />
        {d.conflicts.length > 0 && <aside className="issue-conflict"><strong>Sources disagree on the amount</strong>{d.conflicts.map(c => <p key={c.evidence.id}><Link to="/records/$recordId" params={{ recordId: c.evidence.recordId }} search={{ version: c.evidence.versionId ?? undefined }}>{c.evidence.source}</Link>: {c.account}{c.latest && ' Newer, so shown as the latest account.'}</p>)}</aside>}
        {d.evidence.map(e => <EvidencePassage key={e.id} evidence={e} initiallyOpen={d.initiallyOpen} />)}
      </div>
    </li>)}</ol></> : <p>No development passages are included for this issue in the sample.</p>}
    <footer>This timeline reflects the records available now. Later records can add or correct developments; earlier versions stay available from each record.
      <details><summary>Records for this issue</summary><p>The sample includes {records.length} of {issue.recordCount ?? records.length} records.</p><ul>{records.map(r => <li key={r.id}><Link className="tap-target" to="/records/$recordId" params={{ recordId: r.id }} search={{ version: undefined }}>{r.title}</Link></li>)}</ul></details>
    </footer>
  </section>;
}
