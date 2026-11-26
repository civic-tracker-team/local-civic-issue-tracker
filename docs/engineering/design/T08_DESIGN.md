# Finding views

T08 builds issue timelines and summaries, each meeting's developments and each record's developments from saved analysis findings. The views are deterministic. They call no model, read no other service and persist nothing. Agenda items, a meeting's record list and FR-38's unavailable outcomes are added by the T10 routes (ADR-0050).

## Inputs

`FindingViews` takes saved revisions (`domain.SavedFindings`) and nothing else. The caller chooses them: one per meeting, all from one publication. Under ADR-0051 that means the revisions in the pinned manifest. A second revision of the same meeting is rejected, because its developments would appear twice. The views can't check publication membership themselves.

A development's source order is its position in its revision. Its issue links are the revision's associations with that development ID.

An issue is named by its entry in the latest given revision that mentions it. Revisions are ordered by meeting date, then source meeting ID, then meeting ID, so two meetings on one date name an issue the same way every time. Within one revision, T07 keeps an issue's first mention name. The views don't read issue histories: a history can include revisions outside the publication, and loading one per issue would re-read revisions the caller already has. An issue is whatever analysis saved. Under ADR-0049, one real matter may appear as two issues when the model didn't link it to the earlier one.

## Read-side checks

T07 doesn't recheck saved findings when reading them, so the views reject input that breaks these rules:
- each development has exactly one MEETING date, with DAY precision, equal to the meeting date;
- at most one OCCURRENCE date;
- the development and each issue link have evidence;
- one link per issue per development;
- each linked issue is mentioned in the link's own revision;
- span offsets match their text in code points.

**Kind and status.** The views take the status from the finding kind, using the table in [Model evaluation](T06_DESIGN.md) (ADR-0040 D3). EARLIER_ACTION keeps its own kind and shows as RECORDED. A saved status that disagrees with the kind is rejected. Analysis derives status from kind when it reads a reply, so a mismatch means the data is corrupted. Like the other checks, it fails the whole set of views rather than one development. Showing a wrong status publicly would be worse.

These checks are Release 1 choices made in the PR #6 review on November 25, 2026. On November 26, Kazim agreed that failing the whole set is acceptable for Release 1, because T07 validates findings when saving them.

## Dates and order

ADR-0020 sets the date rules:
- A known OCCURRENCE date is the timeline date.
- Otherwise the meeting date is shown with the MEETING role, which readers see as "reported or discussed on".
- PLANNED and PUBLICATION dates never become the timeline date.

Developments sort by the earliest day their date can mean, then the latest, then meeting date, meeting ID and source order. This is a stable display order only. It makes no claim that a month or range came before a day inside it. An issue's first and latest dates are both timeline dates.

## Passages

Evidence always covers whole model lines (ADR-0039). A model line is a newline-split piece of a prepared segment, and every line except a segment's last keeps its trailing line break. Native PDF text is one segment per page, OCR text is one segment per line, and each HTML element is its own unit.

Spans are joined in citation order. A span joins the passage before it in two cases:
- It continues or overlaps that passage's last span in the same segment.
- It starts the next segment from offset 0, on the same version and page or item, and the passage so far reached the end of its own segment, meaning it doesn't end in a line break.

Segments joined this way are separated by a line break. A span already covered by an earlier passage is dropped. Text is only ever copied from saved spans.

The end-of-segment condition matters on pages where native text is followed by OCR lines. Without it, a citation of lines 3–4 followed by the first OCR line would join and hide line 5.

## Record pages

A record's developments are those whose own evidence cites any version of the record. Link evidence doesn't count, because it explains why a development belongs to an issue rather than what the record says.

## Locations and labels

A passage's location comes from the Records locator saved with its first span (ADR-0036):
- **PDF:** the physical page and, if the source has one, its printed label.
- **HTML:** the portal's agenda item and section IDs, and the element path.

An unreadable locator fails rather than guessing.

The views carry no record label. A readable evidence label, such as "Minutes, Feb 5, 2025, item 9", needs the record's title and kind and the agenda item's label from Records. The T10 routes build it. That depends on the Records read API exposing the portal title, which it doesn't yet.
