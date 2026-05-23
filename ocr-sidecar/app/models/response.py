from pydantic import BaseModel, Field

from app.errors import OcrStatus


class BoundingBox(BaseModel):
    """MRZ zone bounding box in source image coordinates."""

    x: int
    y: int
    width: int
    height: int


class OcrExtractResponse(BaseModel):
    """Successful OCR extraction response."""

    status: OcrStatus = OcrStatus.SUCCESS
    mrz_lines: list[str] = Field(default_factory=list)
    confidence: float = Field(ge=0.0, le=1.0)
    zone_bbox: BoundingBox
    image_quality_score: float = Field(ge=0.0, le=1.0)
    preprocessing_applied: list[str] = Field(default_factory=list)


class OcrErrorResponse(BaseModel):
    """Error response for recoverable OCR pipeline failures."""

    status: OcrStatus
    message: str
    image_quality_score: float | None = None
