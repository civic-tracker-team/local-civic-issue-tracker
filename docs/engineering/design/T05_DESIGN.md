# Record preparation design

Records prepares the exact originals retained by a successful acquisition. It owns validation, checkpoints and saved content. The Python OCR service recognizes supplied images and returns results without application SQL access.

## Native extraction and coverage

PDFBox extracts native PDF text and renders selected pages or regions. jsoup extracts retained agenda text while preserving nested item and section identities. Native text remains preferred where it recovers the required content.

A nonempty heading or page-number footer does not establish that a page's body was recovered. Records examines native text and graphics together. Uncovered image content can use OCR alongside useful native text. A known visible embedded image can retain its original resolution, while composite or transformed content uses rendering.

Painted paths and shading can contain outlined text or map content. Mixed native/vector pages require a recorded routing selection. Without useful native body text, pages with vector content use a full render. This conservative check can also flag decoration, table borders and underlines. A selection records its reason rather than silently classifying those graphics as irrelevant.

## Prepared revisions

Each prepared revision identifies its original document version, file artifact and extraction profile. Segments retain exact text, extraction method and source location. A revision becomes readable only after preparation has finished. Its text and source locations are then immutable. A changed profile or explicit corrective run creates another revision without overwriting earlier citations.

PDF locations preserve physical pages, optional page labels and the displayed coordinate frame after crop and rotation. OCR polygons retain their image dimensions and transform into that frame. HTML uses captured item and section IDs with a version-local structural path. Citation offsets use Unicode code points against the exact retained text.

Normalization happens before sealing. Equal bytes from another source document do not authorize reusing that document's prepared identity or provenance. Repreparation also does not automatically rebuild already-published analysis.

## OCR boundary

The initial engine is PP-OCRv6 medium through PaddleOCR in a pinned Python runtime. The wrapper records model hashes, package versions and orientation settings. Its version-specific orientation adapter preserves item alignment without modifying installed packages.

Records uploads bounded image bytes with an execution ID and expected fingerprint. The wrapper validates identity and admits one inference at a time. It accepts no arbitrary URL, file location or application database credential. A service-only bearer secret protects the API, and communication across machines requires HTTPS.

## Completion and recovery

Records saves page or section checkpoints in short transactions that verify the execution attempt. Rendering and inference happen outside those transactions. Completion requires usable saved content for every selected input, or an explicit justified blank, non-text or no-input result. Unresolved content and exceeded limits fail rather than become successful empty or truncated output.

Records saves an OCR execution reference before the call. An uncertain response retains the running operation and its slot. Resume requires confirmation that both the earlier records worker and referenced OCR inference stopped. Handler cancellation also leaves the wrapper's inference slot occupied until its recognition work ends.

These controls preserve execution state and evidence references. They do not guarantee correct transcription. Recognition quality, whole-meeting model capacity and complete coordinator recovery remain separate evaluations. ADR-0034 through ADR-0037 record the choices, and the preparation guide provides configuration and API details.
