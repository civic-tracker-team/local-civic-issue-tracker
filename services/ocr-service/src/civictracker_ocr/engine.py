"""Pinned Paddle models return literal text and source-image polygons."""

import hashlib
import importlib.metadata
import json
import platform

from .orientation import VerticalOrientation


class PaddleEngine:
    def __init__(self, settings):
        from paddleocr import PaddleOCR

        versions = {name: importlib.metadata.version(name)
                    for name in ("paddleocr", "paddlex", "paddlepaddle-gpu", "numpy", "Pillow")}
        versions["python"] = platform.python_version()
        for name in ("opencv-python", "opencv-contrib-python", "shapely", "pyclipper"):
            try:
                versions[name] = importlib.metadata.version(name)
            except importlib.metadata.PackageNotFoundError:
                versions[name] = "not-installed"
        expected = {"paddleocr": "3.7.0", "paddlex": "3.7.2", "paddlepaddle-gpu": "3.3.0",
                    "numpy": "2.3.5", "Pillow": "12.1.0"}
        if any(versions[name] != version for name, version in expected.items()):
            raise RuntimeError("OCR package versions differ from the qualified runtime")
        models = {}
        for name, directory in (("detection", settings.detection),
                                ("recognition", settings.recognition),
                                ("orientation", settings.orientation)):
            if directory is None:
                raise ValueError("Local model directories are required")
            models[name] = {}
            for filename in ("inference.json", "inference.pdiparams", "inference.yml"):
                models[name][filename] = hashlib.sha256((directory / filename).read_bytes()).hexdigest()
        parameters = dict(
            ocr_version="PP-OCRv6", text_detection_model_name="PP-OCRv6_medium_det",
            text_recognition_model_name="PP-OCRv6_medium_rec", device=settings.device,
            use_doc_orientation_classify=False, use_doc_unwarping=False,
            use_textline_orientation=settings.orientation_policy == "vertical",
        )
        self.manifest = {"pipeline": "ppocr6-1", "versions": versions,
                         "models": models, "parameters": parameters,
                         "orientation_policy": settings.orientation_policy,
                         "image_input": "RGB converted to Paddle BGR",
                         "geometry_gate": {"aspect_ratio": 1.5, "vertical_deviation_degrees": 30}
                         if settings.orientation_policy == "vertical" else None,
                         "alignment": "paddlex-3.7.2-per-item"
                         if settings.orientation_policy == "vertical" else "disabled"}
        self.fingerprint = hashlib.sha256(json.dumps(
            self.manifest, sort_keys=True, separators=(",", ":")).encode()).hexdigest()
        self.model = PaddleOCR(
            **parameters, text_detection_model_dir=str(settings.detection),
            text_recognition_model_dir=str(settings.recognition),
            textline_orientation_model_dir=str(settings.orientation),
        )
        self.orientation = VerticalOrientation(self.model, versions["paddlex"]) if settings.orientation_policy == "vertical" else None

    def recognize(self, image):
        import numpy as np

        output = list(self.model.predict(np.asarray(image)[:, :, ::-1].copy()))
        if len(output) != 1:
            raise ValueError("Expected one image result")
        raw = output[0].json
        raw = json.loads(raw) if isinstance(raw, str) else raw
        raw = raw.get("res", raw)
        texts, scores, polygons = raw["rec_texts"], raw["rec_scores"], raw["rec_polys"]
        if not len(texts) == len(scores) == len(polygons):
            raise ValueError("OCR output arrays differ")
        return [{"text": str(text), "score": float(score),
                 "polygon": [{"x": float(x), "y": float(y)} for x, y in polygon]}
                for text, score, polygon in zip(texts, scores, polygons)]
