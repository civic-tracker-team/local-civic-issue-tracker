import { coverage, home, issues, meetings, records } from './sample';
import type { Coverage, HomeData, Issue, IssueListing, Meeting, MeetingListing, CivicRecord, SearchParameters, SearchResults, Version } from './types';
const copy = <T,>(value: T): T => structuredClone(value);
export async function getCoverage(): Promise<Coverage> { return copy(coverage); }
export async function getHome(): Promise<HomeData> { return copy(home); }
export async function listIssues(): Promise<IssueListing> {
  const groups = [...new Set(issues.map(i => i.group))].map(label => ({ label, items: issues.filter(i => i.group === label) }));
  return copy({ groups, total: 48, sampleSubset: true });
}
export async function getIssue(issueId: string): Promise<Issue | null> { return copy(issues.find(i => i.id === issueId) ?? null); }
export async function listIssueRecords(issueId: string): Promise<CivicRecord[]> { return copy(records.filter(r => r.issueId === issueId)); }
export async function listMeetings(): Promise<MeetingListing> {
  return copy({ months: ['November', 'October', 'September'].map((month, index) => ({ label: `${month} 2026`, items: meetings.filter(m => m.date.startsWith(`2026-${String(11 - index).padStart(2, '0')}`)) })) });
}
export async function getMeeting(meetingId: string): Promise<Meeting | null> { return copy(meetings.find(m => m.id === meetingId) ?? null); }
export async function getRecord(recordId: string): Promise<CivicRecord | null> { return copy(records.find(r => r.id === recordId) ?? null); }
export async function getRecordVersion(recordId: string, version?: string): Promise<Version | null> {
  const record = records.find(r => r.id === recordId);
  return copy(record?.versions.find(v => v.id === (version ?? record.currentVersionId)) ?? null);
}
export async function search(query: SearchParameters): Promise<SearchResults> {
  const words = query.q.toLowerCase().trim().split(/\s+/).filter(Boolean);
  const matches = (text: string) => words.every(word => text.toLowerCase().includes(word));
  const selected = (kind: SearchParameters['in'][number]) => query.in.includes(kind);
  const yearMatches = (text: string) => query.year === undefined || text.includes(String(query.year));
  const issueResults = selected('issues') ? issues.filter(i => matches(`${i.name} ${i.summary} ${i.latest}`) && yearMatches(i.span)) : [];
  const recordResults = selected('records') ? records.filter(r => matches(`${r.title} ${r.excerpt.before} ${r.excerpt.highlight} ${r.excerpt.after}`) && yearMatches(r.sourceMeetingDate)) : [];
  const meetingResults = selected('meetings') ? meetings.filter(m => {
    const linkedRecords = records.filter(r => r.meetingIds.includes(m.id));
    return matches(`${m.title} ${m.summary} ${linkedRecords.map(r => r.title + ' ' + r.excerpt.highlight).join(' ')}`) && yearMatches(m.date);
  }) : [];
  // The signed-off result board is a preview, with 4 of 41 records and 3 of 12 meetings shown.
  const designQuery = ['parking downtown', 'downtown parking'].includes(query.q.trim().toLowerCase()) && query.year === undefined;
  return copy({ query, issues: designQuery && selected('issues') ? ['downtown-parking-structure', 'civic-center-parking-permits'].flatMap(id => issues.filter(i => i.id === id)) : issueResults, records: designQuery && selected('records') ? records.slice(0, 4) : recordResults, meetings: designQuery && selected('meetings') ? ['2026-10-15', '2025-07-02', '2024-03-06'].flatMap(id => meetings.filter(m => m.id === id)) : meetingResults,
    totals: designQuery ? { issues: selected('issues') ? 2 : 0, records: selected('records') ? 41 : 0, meetings: selected('meetings') ? 12 : 0 } : { issues: issueResults.length, records: recordResults.length, meetings: meetingResults.length }, sampleSubset: true });
}
