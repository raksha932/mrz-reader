from dataclasses import dataclass
from enum import StrEnum


class OcrStatus(StrEnum):
    """Statuses returned by the sidecar contract."""

    SUCCESS = "SUCCESS"
    NO_DOCUMENT_FOUND = "NO_DOCUMENT_FOUND"
    IMAGE_QUALITY_LOW = "IMAGE_QUALITY_LOW"
    MRZ_NOT_DETECTED = "MRZ_NOT_DETECTED"


@dataclass(frozen=True)
class OcrSidecarError(Exception):
    """Domain error returned as a value-shaped JSON response."""

    status: OcrStatus
    message: str
    image_quality_score: float | None = None
