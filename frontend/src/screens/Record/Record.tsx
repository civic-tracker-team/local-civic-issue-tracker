import { useEffect, useRef, useState } from 'react';
import { Link, useParams, useSearch } from '@tanstack/react-router';
import { getRecord, getRecordVersion, type CivicRecord, type Version } from '../../api';
import { BasisTag, PageHeading } from '../../components';
import './Record.css';

export function Record() {
  const { recordId } = useParams({ from: '/records/$recordId' });
  const { version: requestedVersion } = useSearch({ from: '/records/$recordId' });
  const [data, setData] = useState<{ record: CivicRecord | null; version: Version | null }>();
  const [failed, setFailed] = useState(false);
  const [passageIndex, setPassageIndex] = useState(-1);
  const passageRefs = useRef<(HTMLElement | null)[]>([]);
  useEffect(() => {
    let active = true; setData(undefined); setFailed(false); setPassageIndex(-1); passageRefs.current = [];
    Promise.all([getRecord(recordId), getRecordVersion(recordId, requestedVersion)]).then(([record, version]) => { if (active) setData({ record, version }); }).catch(() => { if (active) setFailed(true); });
    return () => { active = false; };
  }, [recordId, requestedVersion]);
  if (failed) return <section className="record-screen"><PageHeading title="Unable to load record" /><p role="alert">Please try this page again.</p></section>;
  if (!data) return <section className="record-screen" aria-busy="true"><p role="status">Loading record…</p></section>;
  if (!data.record) return <section className="record-screen"><PageHeading title="Record not found" /><Link className="tap-target" to="/search" search={{ q: '', in: ['issues', 'records', 'meetings'], year: undefined }}>Search records</Link></section>;
  const { record, version } = data;
  const evidenceCount = version?.passages.filter(p => p.marker).length ?? 0;
  const navigatePassage = (direction: number) => {
    const indexes = version?.passages.flatMap((p, i) => p.marker ? [i] : []) ?? [];
    if (!indexes.length) return;
    const next = passageIndex < 0 ? (direction > 0 ? 0 : indexes.length - 1) : (passageIndex + direction + indexes.length) % indexes.length;
    setPassageIndex(next);
    const targetIndex = indexes[next];
    const target = targetIndex === undefined ? null : passageRefs.current[targetIndex];
    target?.focus({ preventScroll: true }); target?.scrollIntoView({ block: 'center', behavior: 'instant' });
  };
  return <section className="record-screen">
    <div className="record-heading">
      {record.meetingIds.map(id => <Link key={id} className="tap-target record-meeting" to="/meetings/$meetingId" params={{ meetingId: id }}>{record.sourceMeetingDate || id} meeting</Link>)}
      <h1>{record.title}</h1>
      <dl>
        {version?.publicationDate && <div><dt>Published</dt><dd>{version.publicationDate}</dd></div>}
        {version?.correctedDate && <div><dt>Corrected</dt><dd>{version.correctedDate}</dd></div>}
        {version && <div><dt>This version retrieved</dt><dd>{version.retrievedDate}</dd></div>}
        {record.pageCount !== null && <div><dt>Pages</dt><dd>{record.pageCount}</dd></div>}
        {version && <div><dt>Used as evidence</dt><dd>{evidenceCount} passages</dd></div>}
      </dl>
      <div className="record-controls">
        {version?.originalUrl ? <a className="record-original" href={version.originalUrl}>Open the original PDF</a> : <button className="record-original" disabled aria-describedby="record-original-note">Open the original PDF</button>}
        <button type="button" disabled={!evidenceCount} onClick={() => navigatePassage(-1)}>Previous passage</button>
        <button type="button" disabled={!evidenceCount} onClick={() => navigatePassage(1)}>Next passage</button>
      </div>
      {!version?.originalUrl && <p id="record-original-note" className="record-original-note">Original PDF unavailable in this sample.</p>}
    </div>
    {version ? <>
      <p role="note" className="record-version">Version {version.number} of {record.versions.length}. {version.correction && <>{version.correction} This version was retrieved {version.retrievedDate}. </>}{version.id !== record.currentVersionId && <>This is an earlier version. </>}
        {record.versions.filter(v => v.id !== version.id).map(v => <span key={v.id}><Link to="/records/$recordId" params={{ recordId }} search={{ version: v.id }}>See version {v.number}</Link> (retrieved {v.retrievedDate}) · </span>)}Text shown here is extracted from the PDF; layout and images are only in the original.
      </p>
      <article className="record-text" aria-label="Record text">
        {version.passages.map((p, i) => <div className="record-block" key={`${p.heading}-${i}`}>
          {(i === 0 || version.passages[i - 1]?.page !== p.page) && <p className="record-page">Page {p.page}{record.pageCount !== null && ` of ${record.pageCount}`}</p>}
          <section ref={node => { passageRefs.current[i] = node; }} tabIndex={-1} aria-labelledby={`record-passage-${i}`}>
            <h2 id={`record-passage-${i}`}>{p.heading}</h2>
            <p>{p.before}{p.highlight && <><mark className={p.basis === 'inferred' ? 'record-highlight--inferred' : ''}>{p.highlight}</mark>{p.marker && <sup>{p.marker}</sup>}</>}{p.after}</p>
            {p.marker && p.issueId && <aside className={`record-connection ${p.basis === 'inferred' ? 'record-connection--inferred' : ''}`}><span className="record-marker">{p.marker}</span><span>Supports <Link to="/issues/$issueId" params={{ issueId: p.issueId }}>{p.issueName}</Link>: {p.supports}</span>{p.basis && <BasisTag basis={p.basis} />}</aside>}
          </section>
        </div>)}
        {version.passages.length === 0 && <p>No extracted passages are available in this sample.</p>}
      </article>
    </> : <div className="record-version"><p>{requestedVersion ? 'This version is unavailable.' : 'No extracted version is available in this sample.'}</p>{record.versions.length > 0 && <Link className="tap-target" to="/records/$recordId" params={{ recordId }} search={{ version: undefined }}>View current version</Link>}{!record.versions.length && <p>{record.excerpt.before}{record.excerpt.highlight}{record.excerpt.after}</p>}</div>}
  </section>;
}
