# Record preparation

Records turns retained originals into readable content using PDFBox 3.0.8 for PDFs and jsoup for agenda HTML. When content needs recognition, it sends image bytes to the separate PP-OCRv6 medium service. Records validates and saves the result.

## Set up OCR

Preparation uses the existing records database, storage, credentials and enabled worker. The OCR service needs a separate Python 3.12 environment on Linux or WSL, local model files and compatible GPU support.

From the repository root:

```sh
OCR_ENV=.local/venvs/ocr
python3.12 -m venv "$OCR_ENV"
"$OCR_ENV/bin/python" -m pip install paddlepaddle-gpu==3.3.0 --index-url https://www.paddlepaddle.org.cn/packages/stable/cu126/
"$OCR_ENV/bin/python" -m pip install 'services/ocr-service[test]'
```

The service pins its dependencies in `services/ocr-service/pyproject.toml`. Its tested runtime uses PaddleOCR 3.7.0, PaddleX 3.7.2, NumPy 2.3.5 and Pillow 12.1.0, with FastAPI 0.121.0, Starlette 0.49.3 and Pydantic 2.14.3 pinned directly because the service imports them. PaddlePaddle is installed by the separate command above and there is no lock file, so other transitive packages can still vary between installs. The GPU wheel above uses CUDA 12.6. WSL must expose its CUDA driver library to the Python process.

| Environment setting | Purpose |
|---|---|
| `OCR_TOKEN` | Records-to-OCR bearer secret |
| `OCR_SCRATCH` | Temporary execution-cache directory |
| `OCR_DETECTION_DIR` | Local PP-OCRv6_medium_det model |
| `OCR_RECOGNITION_DIR` | Local PP-OCRv6_medium_rec model |
| `OCR_ORIENTATION_DIR` | Local PP-LCNet_x1_0_textline_ori model |
| `OCR_DEVICE` | Device, default `gpu:0` |
| `OCR_ORIENTATION` | `vertical` by default, or `off` for comparison |

Each model directory contains `inference.json`, `inference.pdiparams` and `inference.yml`. The service hashes these files and reports their identities with package versions and orientation settings. Requests cannot choose model locations or download URLs.

After supplying those settings, start the service:

```sh
"$OCR_ENV/bin/python" -m civictracker_ocr --port 8085
```

The service binds to loopback by default. GET `/internal/v1/health` requires the bearer credential and returns the model fingerprint, runtime information and any active execution ID. Configure records with that fingerprint and the matching endpoint:

```properties
records.preparation.ocr-base-url=${OCR_SERVICE_URL}
records.preparation.ocr-token=${OCR_SERVICE_TOKEN}
records.preparation.allow-loopback=true
records.preparation.profiles.default.revision=preparation-1
records.preparation.profiles.default.ocr-fingerprint=${OCR_FINGERPRINT}
```

The loopback option permits local HTTP. Communication across machines requires HTTPS. Java service credentials and CSRF tokens are separate from the OCR bearer secret. No preparation profile is enabled implicitly.

## Commands and reads

POST `/internal/v1/preparation-operations` accepts `operationId`, `acquisitionOperationId`, `profileId` and optional `runId`, `freshRevision` and `replacesOperationId`. Its acquisition must have succeeded. Records saves the exact input versions and resolved profile at acceptance. Repeating an equivalent command returns the same operation.

GET `/internal/v1/operations/{id}/preparations` returns successful result references. Each identifies the acquired input, document, original version, prepared revision and profile fingerprint. A successful acquisition with no originals produces an explicit empty preparation result.

GET `/internal/v1/document-versions/{versionId}/content` accepts `preparationId`, `page` and `size`. Only sealed revisions are readable. The first page may omit the preparation ID when exactly one revision exists. Later pages and ambiguous revision choices require an explicit ID. Missing prepared content returns 404.

Segments contain exact text, extraction method, source location and a `selected` flag. Native alternatives remain available even when OCR supplies the selected text. Unit ordinals are zero-based. PDF locations use one-based physical page numbers and retain printed page labels separately. HTML locations preserve item and section IDs with a version-local structural path.

HTML agenda items use the same `.meeting-item[data-itemid]` rule as acquisition, so every prepared item ID matches an acquired agenda item. HTML text keeps a separator at each block edge: a newline between paragraphs, headings, list items, other blocks and table rows, and a tab before each later cell in a row, so an empty cell keeps its column. Whitespace that the source places at a block edge, including a `<br>` there, is folded into that separator. Inline elements add no separator, and a `<br>` inside a block remains a newline. Form controls, including `select` and `textarea`, are removed with scripts and navigation.

Citation offsets are zero-based Unicode code points with an exclusive end. Java UTF-16 indexes are not interchangeable with those offsets. OCR locations retain the image hash, dimensions and a six-value transform. For pixel coordinates x,y, page coordinates are a*x+c*y+e and b*x+d*y+f. The page frame starts at the displayed top-left after crop and rotation, with PDF points multiplied by UserUnit. These are page positions, not geographical coordinates.

## Routing and revisions

Native text is preferred, but an isolated title or page-number footer does not establish that the body was recovered. The automatic path preserves useful native text and recognizes uncovered image content. Composite, clipped, rotated or overlaid images use rendering when direct extraction would lose visible content.

Painted paths and shading can contain outlined text or map content. Mixed native/vector pages therefore need an explicit routing selection. Without useful native body text, vector content uses a full-page render. The conservative check can also flag decoration, table rules and underlines.

A profile's `routing` entries specify `version-id`, one-based `page`, `route` and `reason`. Routes are AUTO, NATIVE, OCR, EMPTY and NON_TEXT. OCR can select normalized page regions using x, y, width and height. Explicit selections retain their reasons. A NATIVE selection cannot create text absent from the source layer.

Sealed revisions are immutable. Database triggers refuse changes to a sealed revision and its units and segments, including from the runtime role, while unsealed revisions remain writable so that an interrupted preparation can resume. Reuse requires the same original version, artifact and complete profile. A changed profile or `freshRevision=true` creates another revision. The current pipeline, `native-first-3`, includes vector-content and nested-HTML corrections and the HTML block separators and shared item rule described above. Revisions sealed under an earlier pipeline keep their original text and stay readable by `preparationId`. A new operation UUID uses that fingerprint, while replaying an earlier operation returns its original result. Repreparation does not automatically rebuild published analysis.

The version-specific PaddleX orientation adapter preserves per-item alignment and uses the qualified vertical geometry rule. It does not modify installed packages. The wrapper also converts PIL RGB input to Paddle's BGR array order. These settings form part of the runtime fingerprint.

## Limits

Profile settings include `max-pages`, `max-input-bytes`, `max-pixels`, `max-characters-per-unit`, `max-regions` and `render-dpi`. Defaults are 5,000 units, 256 MiB per original, 40 million pixels, 200,000 characters per unit, 10,000 regions and 150 DPI. For HTML, `max-pages` limits the number of item and section units. HTML also has an 8 MiB parser limit in the current pipeline. Default prepared reads accept at most 20 units.

An encrypted PDF is prepared when it opens without a password and its permissions allow text extraction, as with a PDF that has only an owner password. A PDF that needs a user password, or forbids text extraction, fails with `PDF_PREPARATION_UNSUPPORTED`. Other encryption, such as certificate security, is not supported, and it can be reported as a general load failure instead.

Records bounds operation inputs, uploads, responses and call duration. OCR independently checks image dimensions, bytes, region counts, text length and response size. Its temporary execution cache has a capacity limit and does not silently discard an execution ID to repeat its inference. Exceeded limits fail visibly rather than return truncated success. These settings are resource controls, not extraction-quality targets.

## Recovery and tests

Records saves each completed page or section with its checkpoint. Rendering and inference run outside SQL transactions. Every checkpoint and final seal checks the current attempt, so an older worker cannot commit after replacement.

A confirmed inference error can fail the operation. A timeout or unreadable response can leave inference running, so records retains its RUNNING state and execution slot. Status identifies the outstanding OCR execution and uses `OCR_EXECUTION_UNRESOLVED` for a parked attempt. An outstanding `blockedExecutionId` can also appear during normal inference and does not, by itself, mean a stall.

Before resume, a maintainer confirms that both the earlier records execution and referenced OCR inference have stopped. The command includes the previous attempt and worker IDs, `previousWorkerStopped=true`, `ocrExecutionId` and `previousOcrStopped=true`. These are explicit confirmations, not automatic termination checks. A new HTTP listener or a timed-out client is insufficient evidence.

Java and database checks run through `tools/verify-java.ps1`. OCR contract, input-validation and cancellation tests run with:

```sh
OCR_PYTHON="$OCR_ENV/bin/python" bash tools/verify-ocr.sh
```

Source-image comparisons evaluate recognition separately from service correctness. Neither passing API tests nor a model confidence score establishes complete or accurate transcription.
