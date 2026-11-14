import asyncio
import hashlib
import io
import json
import threading
from dataclasses import replace
from uuid import uuid4

import httpx
import numpy as np
import pytest
from fastapi.testclient import TestClient
from PIL import Image

from civictracker_ocr.app import create_app
from civictracker_ocr.config import Settings
from civictracker_ocr.orientation import split_class_ids, vertical


class Engine:
    fingerprint = "fixture-v1"
    manifest = {"fixture": True}

    def __init__(self):
        self.calls = 0
        self.fail = False
        self.entered = threading.Event()
        self.release = threading.Event()
        self.release.set()

    def recognize(self, image):
        self.calls += 1
        self.entered.set()
        assert self.release.wait(10)
        return [{"text": "Road 😀", "score": float("nan") if self.fail else .9,
                 "polygon": [{"x": 1., "y": 1.}, {"x": 9., "y": 1.},
                             {"x": 9., "y": 9.}, {"x": 1., "y": 9.}]}]


def arguments(execution=None, text=None):
    execution = execution or uuid4()
    data = io.BytesIO()
    Image.new("RGB", (10, 10), text or "white").save(data, format="PNG")
    data = data.getvalue()
    metadata = {"executionId": str(execution), "imageSha256": hashlib.sha256(data).hexdigest(),
                "width": 10, "height": 10, "fingerprint": "fixture-v1"}
    return {"headers": {"Authorization": "Bearer test-secret", "X-OCR-Execution-Id": str(execution)},
            "files": {"metadata": (None, json.dumps(metadata)),
                      "image": ("../../source.png", data, "image/png")}}


def settings(tmp_path):
    return Settings(token="test-secret", scratch=tmp_path)


def test_identity_and_replay_preserve_output_without_repeating_inference(tmp_path):
    engine = Engine()
    with TestClient(create_app(settings(tmp_path), engine)) as client:
        request = arguments()
        first = client.post("/internal/v1/ocr", **request)
        assert first.status_code == 200
        assert first.json()["result"]["regions"][0]["text"] == "Road 😀"
        assert client.post("/internal/v1/ocr", **request).json() == first.json()
        identifier = request["headers"]["X-OCR-Execution-Id"]
        changed = client.post("/internal/v1/ocr", **arguments(identifier, "black"))
        assert changed.status_code == 409
        assert engine.calls == 1
        health = client.get("/internal/v1/health", headers={"Authorization": "Bearer test-secret"})
        assert health.json()["fingerprint"] == engine.fingerprint
        assert "test-secret" not in health.text


def test_authentication_and_arbitrary_metadata_cannot_start_inference(tmp_path):
    engine = Engine()
    with TestClient(create_app(settings(tmp_path), engine)) as client:
        request = arguments()
        request["headers"].pop("Authorization")
        assert client.post("/internal/v1/ocr", **request).status_code == 401
        assert client.get("/internal/v1/health").status_code == 401
        request = arguments()
        meta = json.loads(request["files"]["metadata"][1])
        meta["path"] = "../../source.pdf"
        request["files"]["metadata"] = (None, json.dumps(meta))
        assert client.post("/internal/v1/ocr", **request).status_code == 400
        assert engine.calls == 0
        assert client.post("/unimplemented", **arguments()).status_code == 404


@pytest.mark.parametrize("field,value", [("imageSha256", "0" * 64), ("width", 20),
                                         ("fingerprint", "other")])
def test_input_identity_and_dimensions_are_checked_before_inference(tmp_path, field, value):
    engine = Engine()
    with TestClient(create_app(settings(tmp_path), engine)) as client:
        request = arguments()
        metadata = json.loads(request["files"]["metadata"][1])
        metadata[field] = value
        request["files"]["metadata"] = (None, json.dumps(metadata))
        assert client.post("/internal/v1/ocr", **request).status_code in (400, 409)
        assert engine.calls == 0


def test_input_pixel_and_result_limits_are_failures_not_truncated_success(tmp_path):
    engine = Engine()
    with TestClient(create_app(replace(settings(tmp_path), max_pixels=20), engine)) as client:
        assert client.post("/internal/v1/ocr", **arguments()).status_code == 413
        assert engine.calls == 0
    with TestClient(create_app(replace(settings(tmp_path), max_bytes=8), engine)) as client:
        assert client.post("/internal/v1/ocr", **arguments()).status_code == 413
        assert engine.calls == 0
    with TestClient(create_app(replace(settings(tmp_path), max_result_bytes=10), engine)) as client:
        response = client.post("/internal/v1/ocr", **arguments())
        assert response.status_code == 422
        assert response.json()["executionFinished"] is True


def test_invalid_model_output_is_cached_as_a_terminal_failure(tmp_path):
    engine = Engine()
    engine.fail = True
    with TestClient(create_app(settings(tmp_path), engine)) as client:
        request = arguments()
        response = client.post("/internal/v1/ocr", **request)
        assert response.status_code == 422
        assert response.json()["executionFinished"] is True
        assert "result" not in response.json()
        assert client.post("/internal/v1/ocr", **request).status_code == 422
        assert engine.calls == 1


def test_cancelled_http_request_does_not_release_a_running_inference(tmp_path):
    async def scenario():
        engine = Engine()
        engine.release.clear()
        app = create_app(settings(tmp_path), engine)
        async with app.router.lifespan_context(app):
            async with httpx.AsyncClient(transport=httpx.ASGITransport(app), base_url="http://fixture") as client:
                request = arguments()
                task = asyncio.create_task(client.post("/internal/v1/ocr", **request))
                assert await asyncio.to_thread(engine.entered.wait, 3)
                task.cancel()
                with pytest.raises(asyncio.CancelledError):
                    await task
                same = await client.post("/internal/v1/ocr", **request)
                assert same.status_code == 409
                assert same.json()["executionFinished"] is False
                other = await client.post("/internal/v1/ocr", **arguments())
                assert other.status_code == 409
                assert engine.calls == 1
                engine.release.set()
                for _ in range(100):
                    health = await client.get("/internal/v1/health", headers={"Authorization": "Bearer test-secret"})
                    if health.json()["activeExecutionId"] is None:
                        break
                    await asyncio.sleep(.01)
                assert (await client.post("/internal/v1/ocr", **request)).status_code == 200
                assert engine.calls == 1
    asyncio.run(scenario())


def test_orientation_gate_and_per_item_split_preserve_batch_associations():
    assert vertical([[0, 0], [10, 0], [10, 50], [0, 50]])
    assert not vertical([[0, 0], [50, 0], [50, 10], [0, 10]])
    assert split_class_ids({"class_ids": np.array([[0], [1]]), "input_path": ["a", "b"]})["class_ids"] == [[0], [1]]
    with pytest.raises(ValueError):
        split_class_ids({"class_ids": np.array([[0], [1]]), "input_path": ["a"]})


def test_rgb_images_reach_paddle_in_its_bgr_array_order():
    from civictracker_ocr.engine import PaddleEngine

    class Model:
        def predict(self, array):
            assert array[0, 0].tolist() == [0, 0, 255]
            return [type("Result", (), {"json": {"res": {"rec_texts": [], "rec_scores": [], "rec_polys": []}}})()]

    engine = PaddleEngine.__new__(PaddleEngine)
    engine.model = Model()
    assert engine.recognize(Image.new("RGB", (10, 10), "red")) == []
