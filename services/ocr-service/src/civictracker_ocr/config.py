"""Deployment settings contain no application database authority."""

import os
from dataclasses import dataclass, field
from pathlib import Path


@dataclass(frozen=True)
class Settings:
    token: str = field(repr=False)
    scratch: Path
    detection: Path | None = None
    recognition: Path | None = None
    orientation: Path | None = None
    orientation_policy: str = "vertical"
    device: str = "gpu:0"
    max_bytes: int = 67108864
    max_pixels: int = 40000000
    max_regions: int = 10000
    max_characters: int = 200000
    max_result_bytes: int = 4194304
    max_executions: int = 10000

    def __post_init__(self):
        if not self.token or self.orientation_policy not in {"off", "vertical"}:
            raise ValueError("Invalid OCR configuration")
        if any(v < 1 for v in (self.max_bytes, self.max_pixels, self.max_regions,
                              self.max_characters, self.max_result_bytes, self.max_executions)):
            raise ValueError("OCR bounds must be positive")

    @classmethod
    def from_env(cls):
        return cls(
            token=os.environ["OCR_TOKEN"], scratch=Path(os.environ["OCR_SCRATCH"]),
            detection=Path(os.environ["OCR_DETECTION_DIR"]),
            recognition=Path(os.environ["OCR_RECOGNITION_DIR"]),
            orientation=Path(os.environ["OCR_ORIENTATION_DIR"]),
            orientation_policy=os.getenv("OCR_ORIENTATION", "vertical"),
            device=os.getenv("OCR_DEVICE", "gpu:0"),
            **{name: int(os.environ[env]) for name, env in {
                "max_bytes": "OCR_MAX_BYTES", "max_pixels": "OCR_MAX_PIXELS",
                "max_regions": "OCR_MAX_REGIONS", "max_characters": "OCR_MAX_CHARACTERS",
                "max_result_bytes": "OCR_MAX_RESULT_BYTES", "max_executions": "OCR_MAX_EXECUTIONS",
            }.items() if env in os.environ},
        )
