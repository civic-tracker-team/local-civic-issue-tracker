"""Version-specific orientation adapter qualified against the PR-01 candidate."""

import math


def vertical(polygon):
    if len(polygon) != 4:
        raise ValueError("Expected a quadrilateral")
    points = [(float(x), float(y)) for x, y in polygon]
    if not all(math.isfinite(v) for point in points for v in point):
        raise ValueError("Nonfinite detector coordinates")
    edges = [(points[(n + 1) % 4][0] - points[n][0],
              points[(n + 1) % 4][1] - points[n][1]) for n in range(4)]
    lengths = [math.hypot(x, y) for x, y in edges]
    pairs = [(lengths[0] + lengths[2]) / 2, (lengths[1] + lengths[3]) / 2]
    major = 0 if pairs[0] >= pairs[1] else 1
    minor = pairs[1 - major]
    return minor > 0 and pairs[major] / minor >= 1.5 and all(
        math.degrees(math.atan2(abs(edges[n][0]), abs(edges[n][1]))) <= 30 + 1e-9
        for n in (major, major + 2))


def split_class_ids(processed):
    """PaddleX 3.7.2 does not split ndarray class IDs before wrapping results."""
    values = processed["class_ids"].tolist()
    if len(values) != len(processed["input_path"]) or any(
        not isinstance(row, list) or len(row) != 1 or row[0] not in (0, 1) for row in values
    ):
        raise ValueError("Unexpected classifier batch shape")
    return dict(processed, class_ids=values)


class VerticalOrientation:
    def __init__(self, model, paddlex_version):
        if paddlex_version != "3.7.2":
            raise RuntimeError("Orientation adapter requires qualification for this PaddleX version")
        pipeline = model.paddlex_pipeline.__dict__.get("_pipeline")
        if pipeline is None:
            raise RuntimeError("Expected a single-device OCR pipeline")
        self.cropper = pipeline._crop_by_polys
        self.classifier = pipeline.textline_orientation_model
        original = self.classifier.process

        def aligned(*args, **kwargs):
            return split_class_ids(original(*args, **kwargs))

        self.classifier.process = aligned
        self.eligible = []
        pipeline._crop_by_polys = self.crop
        pipeline.textline_orientation_model = self.classify

    def crop(self, image, polygons):
        crops = list(self.cropper(image, polygons))
        if len(crops) != len(polygons):
            raise ValueError("Crop and polygon counts differ")
        self.eligible = [vertical(poly) for poly, crop in zip(polygons, crops)
                         if crop.shape[0] != 0 and crop.shape[1] != 0]
        return crops

    def classify(self, crops):
        if len(crops) != len(self.eligible):
            raise ValueError("Classifier crop order differs")
        selected = [n for n, eligible in enumerate(self.eligible) if eligible]
        predictions = list(self.classifier([crops[n] for n in selected])) if selected else []
        if len(predictions) != len(selected):
            raise ValueError("Classifier output count differs")
        indexed = dict(zip(selected, predictions))
        for n in range(len(crops)):
            result = indexed.get(n, {"class_ids": [0]})
            if len(result["class_ids"]) != 1 or result["class_ids"][0] not in (0, 1):
                raise ValueError("Classifier output is not aligned")
            yield result
