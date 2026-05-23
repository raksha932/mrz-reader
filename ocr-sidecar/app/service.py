from app.config import SidecarSettings
from app.detection.zone_detector import MrzZoneDetector
from app.models.response import OcrExtractResponse
from app.ocr.engine import TesseractOcrEngine
from app.preprocessing.pipeline import ImagePreprocessor
from app.quality import ImageQualityScorer


class OcrPipelineService:
    """Coordinates preprocessing, MRZ zone detection, quality scoring, and OCR."""

    def __init__(
        self,
        settings: SidecarSettings | None = None,
        preprocessor: ImagePreprocessor | None = None,
        zone_detector: MrzZoneDetector | None = None,
        quality_scorer: ImageQualityScorer | None = None,
        ocr_engine: TesseractOcrEngine | None = None,
    ) -> None:
        self._settings = settings or SidecarSettings.from_env()
        self._preprocessor = preprocessor or ImagePreprocessor()
        self._zone_detector = zone_detector or MrzZoneDetector()
        self._quality_scorer = quality_scorer or ImageQualityScorer()
        self._ocr_engine = ocr_engine or TesseractOcrEngine(self._settings)

    def extract(self, image_bytes: bytes) -> OcrExtractResponse:
        preprocessed = self._preprocessor.preprocess(image_bytes)
        zone = self._zone_detector.detect(preprocessed.zone_image)
        quality_score = self._quality_scorer.score(preprocessed.zone_image, zone.bbox)
        return self._ocr_engine.extract(
            preprocessed.ocr_images,
            zone.bbox,
            quality_score,
            preprocessed.preprocessing_applied,
        )
