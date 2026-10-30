# OCR service

Python source belongs in `src/civictracker_ocr/`, with tests in `tests/`.

Extracts text from source content when requested by the records service. Returns results to records for validation and persistence; the OCR wrapper does not modify application databases. Native text extraction remains preferred when adequate.

Only directories are scaffolded. The Python version, HTTP framework, OCR dependencies and runtime configuration are not selected by these placeholders. Archived experiments are not copied into the production service.
