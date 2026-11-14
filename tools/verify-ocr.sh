#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/.." && pwd)"
PYTHON="${OCR_PYTHON:-python3}"
if [[ "$PYTHON" == */* && "$PYTHON" != /* ]]; then PYTHON="$PWD/$PYTHON"; fi
cd "$ROOT/services/ocr-service"
"$PYTHON" -m pytest
