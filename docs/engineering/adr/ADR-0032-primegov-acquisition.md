# ADR-0032: Acquire PrimeGov originals from fixed meeting selections

- **Status:** Accepted
- **Date:** 2026-11-13
- **Recorded:** 2026-11-13
- **Deciders:** Kazim Yigit Ablay
- **Related requirements:** FR-01, FR-10, FR-11, FR-18, FR-19, DR-05, DR-16

## Context

Official inputs arrive through listings, generated documents and agenda attachments. Those relationships can change between discovery and download, while unavailable unpublished record types must not become invented mandatory inputs.

## Decision

Discovery captures archived and upcoming City Council listings for configured dates and committees. Its result preserves the source profile, meeting metadata and listed documents. Acquisition handles one selected meeting, revalidates its listing and follows supported official item attachments.

The initial adapter uses the JDK HTTP client, Jackson and jsoup. It handles available English-labelled generated HTML and PDF records, supported PDF attachments, cancellation evidence and supplied video links. Transcript references can use supported document forms without claiming complete transcript discovery.

Each original is retrieved and checked independently, with exact version receipts and observation times. Rescans compare downloaded bytes rather than treating unchanged portal metadata as proof. Unpublished document types remain unavailable without failing the acquisition of other inputs, whereas a selected published link that cannot be retrieved fails its operation.

## Alternatives considered

Browser automation would add a browser runtime for source forms available through HTTP. Arbitrary-link crawling would exceed the selected relationships. Reusing discovery without revalidation could silently import a changed selection under an earlier command.

## Consequences

- **Positive:** Imports retain reproducible selections and official relationships while preserving exact originals.
- **Negative:** Unsupported portal forms require further adapter work, and downloads are not one transactional portal snapshot.
- **Follow-up:** The coordinator's periodic trigger and historical selection policy remain separate from acquisition.

## AI assistance and provenance

Codex assisted with portal inspection, adapter planning and this record. Kazim accepted the source scope, fixed selections and HTTP parsing approach.

## References

- SRS v2.0
- ADR-0014: Portal meeting identity
- ADR-0027: Durable service operations
- ADR-0033: Outbound source controls
