/** Browser sample view contracts, not proposed service wire contracts. */
export type Basis = 'stated' | 'inferred';
export type DevelopmentStatus = 'documented' | 'proposed' | 'planned' | 'unknown' | 'conflict';
/** Labels retain the source's precision; never impute a day for a coarse date. */
export interface TemporalDate { label: string; precision: 'day' | 'month' | 'year' | 'approximate' | 'range' | 'unknown'; role: 'occurrence' | 'reported' | 'discussed' | 'expected' | 'publication'; note: string }
export interface Evidence { id: string; recordId: string; versionId: string | null; source: string; passage: string; basis: Basis; explanation: string | null; videoTime: string | null }
export interface ConflictClaim { evidence: Evidence; publicationDate: TemporalDate; account: string; latest: boolean }
export interface Development { id: string; issueId: string; text: string; date: TemporalDate; sourceMeetingDate: TemporalDate | null; publicationDate: TemporalDate | null; status: DevelopmentStatus; basis: Basis; tag: string; evidence: Evidence[]; conflicts: ConflictClaim[]; initiallyOpen: boolean }
export interface Issue { id: string; name: string; summary: string; /** One-line summary for search previews; screens fall back to `summary`. */ previewSummary?: string; latest: string; meetingCount: number; recordCount: number | null; span: string; group: string; basis: Basis | null; status: DevelopmentStatus; tag: string; developments: Development[] }
export interface AgendaItem { id: string; label: string; topic: string; outcome: string; outcomeStatus: 'recorded' | 'unavailable'; basis: Basis | null; tag: string; source: string; recordId: string; issueId: string | null; issueName: string | null; issueBasis: Basis | null; videoTime: string | null }
export interface MeetingResource { label: string; note: string; recordId: string | null; url: string | null }
export interface Meeting { matchingAgendaItems: number | null; id: string; date: string; dateLabel: string; title: string; status: 'held' | 'cancelled' | 'upcoming'; summary: string; /** Short summary for the browse-meetings list; screens fall back to `summary`. */ listingSummary?: string; recordsLabel: string; resources: MeetingResource[]; items: AgendaItem[]; previousId: string | null; nextId: string | null; time: string | null; cancellationEvidence: string | null; videoUrl: string | null }
export interface RecordPassage { heading: string; before: string; highlight: string; after: string; marker: string | null; issueId: string | null; issueName: string | null; supports: string | null; basis: Basis | null; page: number }
export interface Version { id: string; number: number; publicationDate: string | null; correctedDate: string | null; retrievedDate: string; correction: string | null; originalUrl: string | null; passages: RecordPassage[] }
export interface CivicRecord { id: string; title: string; /** Title of the matched part, shown in search results; screens fall back to `title`. */ matchTitle?: string; kind: string; meetingIds: string[]; sourceMeetingDate: string; currentVersionId: string; pageCount: number | null; versions: Version[]; issueId: string | null; basis: Basis | null; excerpt: { before: string; highlight: string; after: string } }
export interface CoverageMonth { year: number; month: number; held: number; cancelled: number; upcoming: number; notYet: boolean }
export interface Coverage { authority: string; range: string; recordTypes: string[]; scheduled: number; held: number; cancelled: number; upcoming: number; months: CoverageMonth[] }
export type SearchKind = 'issues' | 'records' | 'meetings';
export interface SearchParameters { q: string; in: SearchKind[]; year: number | undefined }
export interface SearchResults { query: SearchParameters; issues: Issue[]; records: CivicRecord[]; meetings: Meeting[]; totals: { issues: number; records: number; meetings: number }; sampleSubset: boolean }
export interface HomeData { tries: string[]; days: { date: string; meetingId: string; meeting: string; items: { issueId: string; issue: string; text: string; tag: string; basis: Basis | null; status: DevelopmentStatus }[] }[] }
export interface IssueListing { groups: { label: string; items: Issue[] }[]; total: number; sampleSubset: boolean }
export interface MeetingListing { months: { label: string; items: Meeting[] }[] }
