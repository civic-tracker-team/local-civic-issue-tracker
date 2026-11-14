"""Synchronous inference has one admission slot and no application SQL connection."""

import asyncio
import hashlib
import hmac
import io
import json
import math
import tempfile
import threading
from contextlib import asynccontextmanager
from pathlib import Path
from uuid import UUID, uuid4

from fastapi import FastAPI, Request
from fastapi.responses import JSONResponse
from PIL import Image
from pydantic import BaseModel, ConfigDict, Field, ValidationError
from starlette.datastructures import UploadFile
from starlette.exceptions import HTTPException

from .config import Settings


class Metadata(BaseModel):
    model_config = ConfigDict(extra="forbid", strict=True)
    executionId: UUID
    imageSha256: str = Field(pattern=r"^[0-9a-f]{64}$")
    width: int = Field(gt=0)
    height: int = Field(gt=0)
    fingerprint: str = Field(min_length=1, max_length=128)


class InputLimit(Exception):
    pass


class BodyLimit:
    def __init__(self, app, limit):
        self.app, self.limit = app, limit

    async def __call__(self, scope, receive, send):
        count = 0

        async def bounded():
            nonlocal count
            message = await receive()
            count += len(message.get("body", b""))
            if count > self.limit:
                raise InputLimit()
            return message

        await self.app(scope, bounded if scope["type"] == "http" else receive, send)


def validate_regions(regions, width, height, settings):
    if not isinstance(regions, list) or len(regions) > settings.max_regions:
        raise ValueError("Region limit")
    characters = 0
    for region in regions:
        text, score, polygon = region["text"], region["score"], region["polygon"]
        if not isinstance(text, str) or not math.isfinite(score) or not 0 <= score <= 1 or len(polygon) != 4:
            raise ValueError("Invalid region")
        characters += len(text)
        text.encode("utf-8", errors="strict")
        if characters > settings.max_characters:
            raise ValueError("Character limit")
        area = 0
        for n, point in enumerate(polygon):
            x, y = point["x"], point["y"]
            if not math.isfinite(x + y) or not 0 <= x <= width or not 0 <= y <= height:
                raise ValueError("Invalid polygon")
            next_point = polygon[(n + 1) % 4]
            area += x * next_point["y"] - next_point["x"] * y
        if abs(area) < 0.00001:
            raise ValueError("Degenerate polygon")


def create_app(settings=None, engine=None):
    settings = settings or Settings.from_env()
    gate = threading.Lock()
    active = None
    ledger = {}
    pending = set()
    instance = str(uuid4())

    @asynccontextmanager
    async def lifespan(app):
        nonlocal engine
        settings.scratch.mkdir(parents=True, exist_ok=True)
        with tempfile.TemporaryDirectory(prefix="ocr-", dir=settings.scratch) as directory:
            app.state.cache = Path(directory)
            if engine is None:
                from .engine import PaddleEngine
                engine = PaddleEngine(settings)
            try:
                yield
            finally:
                if pending:
                    await asyncio.gather(*pending, return_exceptions=True)

    app = FastAPI(lifespan=lifespan, docs_url=None, redoc_url=None, openapi_url=None)
    app.add_middleware(BodyLimit, limit=settings.max_bytes + 65536)

    def authorized(request):
        return hmac.compare_digest(request.headers.get("authorization", "").encode(),
                                   ("Bearer " + settings.token).encode())

    def response(execution, status, code, finished=True):
        return JSONResponse(status_code=status, content={"executionId": str(execution),
                            "executionFinished": finished, "error": code}, headers={"Cache-Control": "no-store"})

    @app.get("/internal/v1/health")
    async def health(request: Request):
        if not authorized(request):
            return JSONResponse(status_code=401, content={"error": "AUTHENTICATION_REQUIRED"})
        return {"instanceId": instance, "fingerprint": engine.fingerprint, "activeExecutionId": active,
                "manifest": engine.manifest}

    @app.post("/internal/v1/ocr")
    async def recognize(request: Request):
        nonlocal active
        try:
            execution = UUID(request.headers.get("x-ocr-execution-id", ""))
        except ValueError:
            return response("", 400, "EXECUTION_ID_REQUIRED")
        if not authorized(request):
            return response(execution, 401, "AUTHENTICATION_REQUIRED")
        if not gate.acquire(blocking=False):
            return response(execution, 409, "OCR_BUSY", finished=str(execution) != active)
        detached = False
        try:
            if len(ledger) >= settings.max_executions and execution not in ledger:
                return response(execution, 503, "EXECUTION_CACHE_FULL")
            active = str(execution)
            length = request.headers.get("content-length")
            if length is not None and (not length.isdigit() or int(length) > settings.max_bytes + 65536):
                return response(execution, 413, "INPUT_LIMIT")
            async with request.form(max_files=1, max_fields=1, max_part_size=16384) as form:
                if set(form) != {"metadata", "image"} or len(form.getlist("metadata")) != 1 or len(form.getlist("image")) != 1:
                    return response(execution, 400, "INVALID_INPUT")
                metadata = Metadata.model_validate_json(form["metadata"])
                upload = form["image"]
                if not isinstance(upload, UploadFile):
                    return response(execution, 400, "INVALID_INPUT")
                data = await upload.read(settings.max_bytes + 1)
            if len(data) > settings.max_bytes:
                return response(execution, 413, "INPUT_LIMIT")
            digest = hashlib.sha256(data).hexdigest()
            identity = (digest, metadata.width, metadata.height, metadata.fingerprint)
            if metadata.executionId != execution or digest != metadata.imageSha256 or metadata.fingerprint != engine.fingerprint:
                return response(execution, 409, "INPUT_IDENTITY_MISMATCH")
            if execution in ledger:
                if ledger[execution] != identity:
                    return response(execution, 409, "EXECUTION_INPUT_CONFLICT")
                cached = json.loads((app.state.cache / f"{execution}.json").read_text())
                return JSONResponse(status_code=200 if "result" in cached else 422,
                                    content=cached, headers={"Cache-Control": "no-store"})
            with Image.open(io.BytesIO(data)) as opened:
                if opened.format not in {"PNG", "JPEG"} or opened.size != (metadata.width, metadata.height):
                    return response(execution, 400, "IMAGE_IDENTITY_MISMATCH")
                if opened.width * opened.height > settings.max_pixels:
                    return response(execution, 413, "PIXEL_LIMIT")
                image = opened.convert("RGB")
            ledger[execution] = identity
            def compute():
                try:
                    regions = engine.recognize(image)
                    validate_regions(regions, metadata.width, metadata.height, settings)
                    result = {"executionId": str(execution), "imageSha256": digest,
                              "fingerprint": engine.fingerprint, "width": metadata.width,
                              "height": metadata.height, "regions": regions,
                              "provenance": engine.manifest}
                    payload = {"executionId": str(execution), "executionFinished": True, "result": result}
                    encoded = json.dumps(payload, allow_nan=False, separators=(",", ":"))
                    if len(encoded.encode()) > settings.max_result_bytes:
                        raise ValueError("Result limit")
                except Exception:
                    payload = {"executionId": str(execution), "executionFinished": True, "error": "INFERENCE_FAILED"}
                    encoded = json.dumps(payload)
                (app.state.cache / f"{execution}.json").write_text(encoded)
                return payload

            task = asyncio.create_task(asyncio.to_thread(compute))
            pending.add(task)
            task.add_done_callback(pending.discard)
            try:
                payload = await asyncio.shield(task)
            except asyncio.CancelledError:
                detached = True

                def release(completed):
                    nonlocal active
                    active = None
                    gate.release()
                    if not completed.cancelled():
                        completed.exception()

                task.add_done_callback(release)
                raise
            return JSONResponse(status_code=200 if "result" in payload else 422, content=payload, headers={"Cache-Control": "no-store"})
        except (InputLimit, HTTPException):
            return response(execution, 413, "INPUT_LIMIT")
        except (ValidationError, ValueError, TypeError, KeyError, OSError):
            return response(execution, 400, "INVALID_INPUT")
        finally:
            if not detached:
                active = None
                gate.release()

    return app
