# OCR service

Python 3.12/FastAPI wrapper for PP-OCRv6 medium. Records uploads selected images and receives literal text, scores, polygons and model provenance. Native extraction and application persistence remain in records.

One inference runs per instance. Execution references prevent duplicate calls from starting the same inference again within that instance. Its temporary result cache is not a durable processing database. A disconnected request does not release a running inference. OCR has no application SQL configuration.

Runtime settings, model setup, request contracts and recovery are described in [preparation setup](../../docs/development/RECORDS_PREPARATION.md). Source is under `src/civictracker_ocr/`; contract and regression tests are under `tests/`.
