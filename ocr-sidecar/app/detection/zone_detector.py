from dataclasses import dataclass

import cv2
import numpy as np

from app.errors import OcrSidecarError, OcrStatus
from app.models.response import BoundingBox


@dataclass(frozen=True)
class ZoneDetectionResult:
    """Detected MRZ zone."""

    bbox: BoundingBox


class MrzZoneDetector:
    """Detects the MRZ band using morphology and contour filtering."""

    def detect(self, image: np.ndarray) -> ZoneDetectionResult:
        height, width = image.shape[:2]
        document_bbox = self._detect_document_bbox(image)
        blackhat_kernel = cv2.getStructuringElement(cv2.MORPH_RECT, (13, 5))
        blackhat = cv2.morphologyEx(image, cv2.MORPH_BLACKHAT, blackhat_kernel)

        gradient = cv2.Sobel(blackhat, ddepth=cv2.CV_32F, dx=1, dy=0, ksize=-1)
        gradient = np.absolute(gradient)
        min_value, max_value = float(np.min(gradient)), float(np.max(gradient))
        if max_value - min_value > 0:
            gradient = ((gradient - min_value) / (max_value - min_value) * 255).astype("uint8")
        else:
            gradient = gradient.astype("uint8")

        closing_kernel = cv2.getStructuringElement(cv2.MORPH_RECT, (21, 7))
        closed = cv2.morphologyEx(gradient, cv2.MORPH_CLOSE, closing_kernel)
        _, thresh = cv2.threshold(closed, 0, 255, cv2.THRESH_BINARY | cv2.THRESH_OTSU)

        contours, _ = cv2.findContours(thresh, cv2.RETR_EXTERNAL, cv2.CHAIN_APPROX_SIMPLE)
        candidates: list[tuple[int, int, int, int]] = []
        for contour in contours:
            x, y, w, h = cv2.boundingRect(contour)
            aspect_ratio = w / max(h, 1)
            area = w * h
            if (
                aspect_ratio > 5.0
                and area > width * height * 0.003
                and self._is_in_mrz_area(width, height, x, y, w, h, document_bbox)
            ):
                candidates.append((x, y, w, h))

        if not candidates:
            if document_bbox is not None:
                return ZoneDetectionResult(self._document_bottom_band_bbox(width, height, document_bbox))
            return self._bottom_band_fallback(width, height)

        x, y, w, h = max(candidates, key=lambda box: box[2] * box[3])
        return ZoneDetectionResult(self._expanded_bbox(width, height, x, y, w, h))

    def _detect_document_bbox(self, image: np.ndarray) -> tuple[int, int, int, int] | None:
        height, width = image.shape[:2]
        if width < 80 or height < 80:
            return None

        gray = image
        if len(image.shape) == 3:
            gray = cv2.cvtColor(image, cv2.COLOR_BGR2GRAY)

        blurred = cv2.GaussianBlur(gray, (5, 5), 0)
        kernel_width = max(15, int(width * 0.035))
        kernel_height = max(9, int(height * 0.018))
        close_kernel = cv2.getStructuringElement(cv2.MORPH_RECT, (kernel_width, kernel_height))

        image_area = width * height
        components: list[tuple[int, int, int, int]] = []
        primary_candidates: list[tuple[int, int, int, int]] = []
        for thresholded in self._document_thresholds(blurred):
            closed = cv2.morphologyEx(thresholded, cv2.MORPH_CLOSE, close_kernel)
            contours, _ = cv2.findContours(closed, cv2.RETR_EXTERNAL, cv2.CHAIN_APPROX_SIMPLE)
            for contour in contours:
                x, y, w, h = cv2.boundingRect(contour)
                area = w * h
                if area < image_area * 0.015:
                    continue
                if area > image_area * 0.85 and y <= 2 and h >= height * 0.90:
                    continue
                if w < width * 0.25 or h < height * 0.035:
                    continue
                aspect_ratio = w / max(h, 1)
                if aspect_ratio < 0.85 or aspect_ratio > 8.0:
                    continue
                if y + h < height * 0.45:
                    continue
                fill_ratio = cv2.contourArea(contour) / max(area, 1)
                if fill_ratio < 0.25:
                    continue
                box = (x, y, w, h)
                components.append(box)
                if (
                    area >= image_area * 0.08
                    and w >= width * 0.35
                    and h >= height * 0.14
                    and aspect_ratio <= 3.8
                ):
                    primary_candidates.append(box)

        if not components:
            return None

        primary = max(primary_candidates or components, key=self._document_box_score)
        related = [box for box in components if self._belongs_to_document_component(primary, box, height)]
        return self._union_bbox(related or [primary])

    def _document_thresholds(self, gray: np.ndarray) -> list[np.ndarray]:
        _, otsu = cv2.threshold(gray, 0, 255, cv2.THRESH_BINARY | cv2.THRESH_OTSU)
        masks = [otsu]
        for threshold in (105, 125, 145, 165):
            _, fixed = cv2.threshold(gray, threshold, 255, cv2.THRESH_BINARY)
            masks.append(fixed)
        return masks

    def _document_box_score(self, box: tuple[int, int, int, int]) -> float:
        _, _, w, h = box
        aspect_ratio = w / max(h, 1)
        aspect_bonus = 1.25 if 1.1 <= aspect_ratio <= 3.2 else 1.0
        return float(w * h) * aspect_bonus

    def _belongs_to_document_component(
        self,
        primary: tuple[int, int, int, int],
        candidate: tuple[int, int, int, int],
        image_height: int,
    ) -> bool:
        primary_x, primary_y, primary_w, primary_h = primary
        x, y, w, h = candidate
        x_overlap = min(primary_x + primary_w, x + w) - max(primary_x, x)
        if x_overlap < min(primary_w, w) * 0.45:
            return False

        primary_bottom = primary_y + primary_h
        candidate_bottom = y + h
        vertical_gap = max(primary_y, y) - min(primary_bottom, candidate_bottom)
        return vertical_gap <= int(image_height * 0.12)

    def _union_bbox(self, boxes: list[tuple[int, int, int, int]]) -> tuple[int, int, int, int]:
        x0 = min(box[0] for box in boxes)
        y0 = min(box[1] for box in boxes)
        x1 = max(box[0] + box[2] for box in boxes)
        y1 = max(box[1] + box[3] for box in boxes)
        return (x0, y0, x1 - x0, y1 - y0)

    def _is_in_mrz_area(
        self,
        _image_width: int,
        image_height: int,
        x: int,
        y: int,
        w: int,
        h: int,
        document_bbox: tuple[int, int, int, int] | None,
    ) -> bool:
        if document_bbox is None:
            return y >= int(image_height * 0.55)

        doc_x, doc_y, doc_w, doc_h = document_bbox
        mrz_start_y = doc_y + int(doc_h * 0.68)
        mrz_end_y = doc_y + doc_h
        candidate_mid_y = y + (h // 2)
        x_overlap = min(x + w, doc_x + doc_w) - max(x, doc_x)

        return (
            mrz_start_y <= candidate_mid_y <= mrz_end_y
            and x_overlap >= min(w, doc_w) * 0.55
            and w >= doc_w * 0.55
            and h <= max(int(doc_h * 0.35), int(image_height * 0.12))
        )

    def _expanded_bbox(self, image_width: int, image_height: int, x: int, y: int, w: int, h: int) -> BoundingBox:
        horizontal_padding = max(4, int(w * 0.02))
        vertical_padding = max(12, int(h * 1.6), int(image_height * 0.04))
        x0 = max(0, x - horizontal_padding)
        y0 = max(0, y - vertical_padding)
        x1 = min(image_width, x + w + horizontal_padding)
        y1 = min(image_height, y + h + vertical_padding)
        return BoundingBox(x=x0, y=y0, width=x1 - x0, height=y1 - y0)

    def _document_bottom_band_bbox(
        self,
        image_width: int,
        image_height: int,
        document_bbox: tuple[int, int, int, int],
    ) -> BoundingBox:
        x, y, w, h = document_bbox
        horizontal_padding = max(4, int(w * 0.02))
        x0 = max(0, x + horizontal_padding)
        x1 = min(image_width, x + w - horizontal_padding)
        y0 = max(0, y + int(h * 0.68))
        y1 = min(image_height, y + int(h * 0.98))
        return BoundingBox(x=x0, y=y0, width=max(0, x1 - x0), height=max(0, y1 - y0))

    def _bottom_band_fallback(self, width: int, height: int) -> ZoneDetectionResult:
        if width < 80 or height < 80:
            raise OcrSidecarError(
                OcrStatus.MRZ_NOT_DETECTED,
                "Document found but MRZ zone could not be isolated",
            )

        x_margin = max(8, int(width * 0.04))
        y0 = int(height * 0.70)
        y1 = min(height, int(height * 0.94))
        return ZoneDetectionResult(
            BoundingBox(x=x_margin, y=y0, width=width - (x_margin * 2), height=y1 - y0)
        )
