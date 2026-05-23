import numpy as np

from app.models.response import BoundingBox, OcrExtractResponse
from app.preprocessing.pipeline import PreprocessingResult
from app.service import OcrPipelineService


class LowQualityPreprocessor:
    def preprocess(self, image_bytes: bytes) -> PreprocessingResult:
        image = np.full((100, 240), 255, dtype=np.uint8)
        return PreprocessingResult(
            image=image,
            zone_image=image,
            ocr_images=[image],
            preprocessing_applied=["test"],
        )


class FixedZoneDetector:
    def detect(self, image: np.ndarray):
        class Zone:
            bbox = BoundingBox(x=10, y=60, width=200, height=30)

        return Zone()


class LowQualityScorer:
    def score(self, image: np.ndarray, bbox: BoundingBox) -> float:
        return 0.05


class RecordingOcrEngine:
    def extract(
        self,
        image: list[np.ndarray],
        bbox: BoundingBox,
        quality_score: float,
        preprocessing_applied: list[str],
    ) -> OcrExtractResponse:
        self.called = True
        return OcrExtractResponse(
            mrz_lines=[
                "P<LVAALKSNIS<<LIVA<<<<<<<<<<<<<<<<<<<<<<<<<<<",
                "LV57865342LVA9703083F2903161080397<12462<<22",
            ],
            confidence=0.82,
            zone_bbox=bbox,
            image_quality_score=quality_score,
            preprocessing_applied=preprocessing_applied,
        )


def test_low_quality_score_does_not_prevent_ocr_attempt() -> None:
    ocr_engine = RecordingOcrEngine()
    ocr_engine.called = False
    service = OcrPipelineService(
        preprocessor=LowQualityPreprocessor(),
        zone_detector=FixedZoneDetector(),
        quality_scorer=LowQualityScorer(),
        ocr_engine=ocr_engine,
    )

    response = service.extract(b"image")

    assert ocr_engine.called
    assert response.image_quality_score == 0.05
    assert response.mrz_lines[0].startswith("P<LVA")
