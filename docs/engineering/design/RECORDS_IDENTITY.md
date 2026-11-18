# Records identity and versions

Records separates an application's identity for a document from the identifiers and URLs supplied by the source portal. This allows a document to keep its internal reference when the portal replaces an identifier, while retaining the evidence for that continuity.

## Internal and official identifiers

The owning service assigns UUIDv4 identifiers to new meetings, documents, original versions and file artifacts. Matched objects keep their existing UUIDs. UUID generation does not perform deduplication or reconcile independently imported databases.

Official identifiers remain separately associated with their source portal, meeting and identifier family. Generated document IDs, attachment-history IDs and template IDs are not interchangeable. HTML and PDF outputs also remain separate originals even when they share a template.

## Matching after a portal ID changes

Known current or historical identifiers resolve to the existing document. For a new portal identifier, automatic reuse requires exactly one equal-SHA-256 candidate within the permitted context:

| Source form | Required matching context |
|---|---|
| Agenda-item document | Same portal, meeting and agenda item |
| Meeting-level generated document | Same portal, meeting, template ID, output type and language |

Missing required fields, no match or several candidates produce a separate document. Titles, filenames, position and equal file size do not establish continuity. Shared file bytes do not justify borrowing another document's source identity.

When continuity is established, the newer authoritative portal identifier becomes primary. Earlier identifiers remain historical aliases. Numeric or lexical order does not establish which identifier is newer, and fetching an old reference does not make it current again.

## Missing official IDs

The initial fallback uses the exact absolute source URL within its portal, meeting and item context. Query parameters are retained. A different URL does not establish that a document is the same, while a known official identifier takes precedence.

Temporary URLs, redirects, fragments, duplicate links and later official-ID assignment still need further consideration. This limited fallback does not include a general URL-normalization or similarity-matching policy.

## Original bytes and observations

SHA-256 detects changes after document identity has been established. Changed bytes create a retained version without removing earlier versions. If the source returns to previously saved bytes, records reuses that version and adds the new observation. Metadata and relationships can change even when bytes do not.

Each observation retains its source context, URLs and supplied metadata. Publication time is separate from retrieval time and meeting date. A strictly newer authoritative observation can advance current references. Older or conflicting observations remain recorded without automatically demoting the current one. Missing values do not instruct the service to erase known metadata.

These rules apply to source documents. They do not merge civic issues or decide whether a changed input requires historical analysis to be rebuilt. ADR-0022 through ADR-0025 explain the choices and alternatives.
