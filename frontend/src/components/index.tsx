import type { ReactNode } from 'react';
import { Link } from '@tanstack/react-router';
import type { Basis, Coverage, Evidence } from '../api';
export function BasisTag({ basis }: { basis: Basis }) {
  return <span className={`basis-tag basis-tag--${basis}`}>{basis === 'stated' ? 'Stated' : 'Inferred link · may be incorrect'}</span>;
}
export function PageHeading({ title, children }: { title: string; children?: ReactNode }) {
  return <div className="page-heading"><h1>{title}</h1>{children}</div>;
}
export function CoverageNotice({ coverage, children }: { coverage: Coverage; children?: ReactNode }) {
  return <section className="coverage-notice" aria-label="Collection coverage"><h2>What this collection covers</h2><p><strong>{coverage.authority}, {coverage.range}</strong>: {coverage.recordTypes.join(', ')} from {coverage.scheduled} scheduled meetings ({coverage.held} held, {coverage.cancelled} cancelled). {coverage.upcoming} more is upcoming. Anything outside this set won't appear in results.</p>{children}</section>;
}
export function EvidencePassage({ evidence, initiallyOpen = false }: { evidence: Evidence; initiallyOpen?: boolean }) {
  return <details className="evidence-passage" open={initiallyOpen}><summary>Source passage · {evidence.source}</summary><blockquote>{evidence.passage}</blockquote><Link className="tap-target" to="/records/$recordId" params={{ recordId: evidence.recordId }} search={{ version: evidence.versionId ?? undefined }}>Open source record</Link>{evidence.explanation && <p>Why it was linked: {evidence.explanation}</p>}</details>;
}
