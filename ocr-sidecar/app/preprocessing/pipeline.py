from dataclasses import dataclass

import cv2
import numpy as np

from app.errors import OcrSidecarError, OcrStatus


@dataclass(frozen=True)
class PreprocessingResult:
    """Processed image plus a trace of transformations applied."""

    image: np.ndarray
    zone_image: np.ndarray
    ocr_images: list[np.ndarray]
    preprocessing_applied: list[str]


class ImagePreprocessor:
    """OpenCV preprocessing for MRZ OCR."""

    MAX_IMAGE_DIMENSION = 1600

    def preprocess(self, image_bytes: bytes) -> PreprocessingResult:
        raw = np.frombuffer(image_bytes, dtype=np.uint8)
        decoded = cv2.imdecode(raw, cv2.IMREAD_COLOR)
        if decoded is None:
            raise OcrSidecarError(OcrStatus.NO_DOCUMENT_FOUND, "Image bytes could not be decoded")

        applied: list[str] = []
        decoded = self._resize_if_large(decoded, applied)

        gray = cv2.cvtColor(decoded, cv2.COLOR_BGR2GRAY)
        applied.append("grayscale")

        contrast = self._normalize_contrast(gray)
        applied.append("clahe_contrast")

        blurred = cv2.GaussianBlur(contrast, (5, 5), 0)
        applied.append("gaussian_blur_5x5")

        thresholded = cv2.adaptiveThreshold(
            blurred,
            255,
            cv2.ADAPTIVE_THRESH_GAUSSIAN_C,
            cv2.THRESH_BINARY,
            11,
            2,
        )
        applied.append("adaptive_threshold")

        sharpened = self._sharpen(contrast)
        applied.append("sharpen_clahe")

        return PreprocessingResult(
            image=thresholded,
            zone_image=gray,
            ocr_images=[thresholded, contrast, sharpened],
            preprocessing_applied=applied,
        )

    def _normalize_contrast(self, image: np.ndarray) -> np.ndarray:
        clahe = cv2.createCLAHE(clipLimit=2.0, tileGridSize=(8, 8))
        return clahe.apply(image)

    def _sharpen(self, image: np.ndarray) -> np.ndarray:
        gaussian = cv2.GaussianBlur(image, (0, 0), 1.0)
        return cv2.addWeighted(image, 1.6, gaussian, -0.6, 0)

    def _resize_if_large(self, image: np.ndarray, applied: list[str]) -> np.ndarray:
        height, width = image.shape[:2]
        longest_side = max(width, height)
        if longest_side <= self.MAX_IMAGE_DIMENSION:
            return image

        scale = self.MAX_IMAGE_DIMENSION / longest_side
        resized = cv2.resize(
            image,
            (int(width * scale), int(height * scale)),
            interpolation=cv2.INTER_AREA,
        )
        applied.append(f"resize_max_{self.MAX_IMAGE_DIMENSION}")
        return resized
