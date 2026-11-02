# ADR-0013: Prefer native PDF extraction and use OCR when needed

- **Status:** Accepted
- **Date:** 2026-10-08
- **Recorded:** 2026-11-01, retrospectively
- **Deciders:** Kazim Yigit Ablay
- **Related requirements:** LIM-10, FR-12, DR-06

## Context

Official records may contain usable native PDF text, scanned pages, maps or a mixture of these. The existence of some native text does not establish that all substantive content has been recovered.

The PR-01 feasibility work investigated native extraction and OCR on selected records. Its purpose was to establish a workable extraction direction. It did not measure extraction quality across the entire intended collection or select a production OCR model.

## Decision

Records will use native PDF text extraction where it adequately recovers the required content. When selected content needs OCR, records will request that work from the OCR service, validate the result and retain prepared readable content with its original-record association.

OCR applies to ordinary scanned or image-based document content as well as maps and similar graphics. A nonempty text layer is not, by itself, a reason to assume OCR is unnecessary. This decision allows the preparation workflow to use the appropriate method for the content without requiring OCR on every page.

The exact parser, extraction-adequacy checks, OCR configuration and fallback thresholds remain open.

## Alternatives considered

The project discussed applying OCR more broadly, including to pages already containing native text. Native-first extraction remained the preferred approach rather than replacing usable text with OCR by default.

Native extraction alone would not handle scanned or omitted substantive content, so it is not sufficient as the only preparation method. The feasibility results do not establish universal accuracy for either route.

## Consequences

- **Positive:** Usable existing text can be recovered without relying on recognition, while scans and graphic labels remain addressable.
- **Negative:** Records must identify content that native extraction has not adequately recovered. OCR can still produce incomplete or incorrect text.
- **Follow-ups:** Evaluate representative mixed documents and define adequacy checks. Extraction loss must not be concealed as ordinary absence of source information. Preserve originals independently of prepared text.

## AI assistance and provenance

Codex helped organize extraction comparisons and check this record against the native-first decision and SRS. Kazim selected OCR as a fallback. The record makes no broader accuracy claim from the prototype.

## References

- SRS v1.0
- ADR-0006: File and SQL storage
- ADR-0009: Inference persistence boundary
