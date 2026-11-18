import { useEffect, useState } from 'react';
import { Link, useParams } from '@tanstack/react-router';
import { getMeeting, type Meeting as MeetingData } from '../../api';
import { BasisTag } from '../../components';
import './Meeting.css';
const shortDate = (id: string) => new Intl.DateTimeFormat('en-US', { month: 'short', day: 'numeric', year: 'numeric', timeZone: 'UTC' }).format(new Date(`${id}T12:00:00Z`));
export function Meeting() {
  const { meetingId } = useParams({ from: '/meetings/$meetingId' });
  const [meeting, setMeeting] = useState<MeetingData | null>();
  const [failed, setFailed] = useState(false);
  useEffect(() => { let live = true; setMeeting(undefined); setFailed(false); getMeeting(meetingId).then(m => { if (live) setMeeting(m); }).catch(() => { if (live) setFailed(true); }); return () => { live = false; }; }, [meetingId]);
  if (failed) return <section className="meeting-screen"><h1>Meeting unavailable</h1><p role="alert">The meeting could not be loaded. Please try again.</p></section>;
  if (meeting === undefined) return <section className="meeting-screen" aria-busy="true"><p role="status">Loading meeting…</p></section>;
  if (!meeting) return <section className="meeting-screen"><h1>Meeting not found</h1><Link className="tap-target" to="/meetings" activeOptions={{ exact: true }}>All meetings</Link></section>;
  return <section className="meeting-screen">
    <div className="meeting-intro">
      <nav aria-label="Nearby meetings">
        {meeting.previousId ? <Link className="tap-target" to="/meetings/$meetingId" params={{ meetingId: meeting.previousId }} aria-label={`Previous meeting: ${shortDate(meeting.previousId)}`}>← {shortDate(meeting.previousId)}</Link> : <span />}
        <Link className="tap-target" to="/meetings" activeOptions={{ exact: true }}>All meetings</Link>
        {meeting.nextId ? <Link className="tap-target" to="/meetings/$meetingId" params={{ meetingId: meeting.nextId }} aria-label={`Next meeting: ${shortDate(meeting.nextId)}`}>{shortDate(meeting.nextId)} →</Link> : <span />}
      </nav>
      <h1>{meeting.status === 'cancelled' ? 'Cancelled meeting' : meeting.title} · <span className="meeting-heading-date">{new Intl.DateTimeFormat('en-US', { weekday: 'long', month: 'short', day: 'numeric', year: 'numeric', timeZone: 'UTC' }).format(new Date(`${meeting.date}T12:00:00Z`))}</span></h1>
      <p className="meeting-meta">San Bernardino City Council{meeting.time && ` · ${meeting.time}`} · {meeting.status === 'upcoming' ? 'Upcoming' : meeting.status === 'cancelled' ? 'Cancelled' : `${meeting.items.length} agenda items covered here`}</p>
      <div className="meeting-resources">{meeting.resources.map(r => r.recordId ? <Link key={r.label} to="/records/$recordId" params={{ recordId: r.recordId }} search={{ version: undefined }}>{r.label}<span>{r.note}</span></Link> : r.url ? <a key={r.label} href={r.url}>{r.label}<span>{r.note}</span></a> : <span key={r.label} className="meeting-unavailable" aria-disabled="true">{r.label}<span>Unavailable in sample</span></span>)}</div>
    </div>
    {meeting.summary && <section className="meeting-summary" aria-labelledby="meeting-summary"><h2 id="meeting-summary">In short</h2><p>{meeting.summary}</p></section>}
    <section aria-labelledby="meeting-items"><h2 id="meeting-items">Agenda items and outcomes</h2>
      {!meeting.items.length && <p className="meeting-meta">No item-level outcomes are available in this sample{meeting.status === 'upcoming' ? '; this meeting is upcoming' : ''}.</p>}
      {meeting.items.map(item => <article key={item.id} className="meeting-item"><span className="meeting-number">{item.label}</span><div className="meeting-item-body">
        <div className="meeting-topic"><span className="meeting-label">On the agenda</span><span>{item.topic}</span></div>
        <div className={`meeting-outcome ${item.outcomeStatus === 'unavailable' ? 'meeting-outcome--unknown' : ''}`}><span className="meeting-label">What happened</span><span>{item.outcome}</span></div>
        <div className="meeting-item-links">{item.basis ? <BasisTag basis={item.basis} /> : <span className="meeting-unknown-tag">{item.tag}</span>}
          <Link className="tap-target" to="/records/$recordId" params={{ recordId: item.recordId }} search={{ version: undefined }}>{item.source}</Link>
          {item.issueId && <><Link className="tap-target meeting-issue-link" to="/issues/$issueId" params={{ issueId: item.issueId }}>Issue: {item.issueName}</Link>{item.issueBasis === 'inferred' && <BasisTag basis="inferred" />}</>}
          {item.videoTime && (meeting.videoUrl ? <a className="tap-target" href={meeting.videoUrl}>Video at {item.videoTime}</a> : <span className="meeting-video-note">Video at {item.videoTime} · unavailable in sample</span>)}
        </div>
      </div></article>)}
    </section>
    <footer>Agenda topics come from the agenda; outcomes come from the minutes. Where neither establishes an outcome, we say so instead of guessing.</footer>
  </section>;
}
