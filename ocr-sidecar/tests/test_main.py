from fastapi.testclient import TestClient

from app.errors import OcrSidecarError, OcrStatus
from app.main import create_app
from app.models.response import BoundingBox, OcrExtractResponse


class FakeSuccessService:
    def extract(self, image_bytes: bytes) -> OcrExtractResponse:
        assert image_bytes == b"image-bytes"
        return OcrExtractResponse(
            mrz_lines=[
                "P<UTOERIKSSON<<ANNA<MARIA<<<<<<<<<<<<<<<<<<<",
                "L898902C36UTO7408122F1204159ZE184226B<<<<<10",
            ],
            confidence=0.97,
            zone_bbox=BoundingBox(x=50, y=420, width=600, height=80),
            image_quality_score=0.85,
            preprocessing_applied=["grayscale", "adaptive_threshold"],
        )


class FakeErrorService:
    def extract(self, image_bytes: bytes) -> OcrExtractResponse:
        raise OcrSidecarError(
            OcrStatus.MRZ_NOT_DETECTED,
            "Document found but MRZ zone could not be isolated",
        )


def test_health_endpoint() -> None:
    client = TestClient(create_app(FakeSuccessService()))

    response = client.get("/health")

    assert response.status_code == 200
    assert response.json() == {"status": "UP"}


def test_extract_endpoint_success() -> None:
    client = TestClient(create_app(FakeSuccessService()))

    response = client.post(
        "/ocr/extract",
        files={"image": ("document.png", b"image-bytes", "image/png")},
    )

    assert response.status_code == 200
    payload = response.json()
    assert payload["status"] == "SUCCESS"
    assert payload["confidence"] == 0.97
    assert payload["zone_bbox"] == {"x": 50, "y": 420, "width": 600, "height": 80}
    assert payload["mrz_lines"][0].startswith("P<UTO")


def test_extract_endpoint_error() -> None:
    client = TestClient(create_app(FakeErrorService()))

    response = client.post(
        "/ocr/extract",
        files={"image": ("document.png", b"image-bytes", "image/png")},
    )

    assert response.status_code == 422
    assert response.json() == {
        "status": "MRZ_NOT_DETECTED",
        "message": "Document found but MRZ zone could not be isolated",
        "image_quality_score": None,
    }
