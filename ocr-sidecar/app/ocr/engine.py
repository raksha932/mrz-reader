import re
import time
from dataclasses import dataclass

import cv2
import numpy as np
import pytesseract

from app.config import SidecarSettings
from app.errors import OcrSidecarError, OcrStatus
from app.models.response import BoundingBox, OcrExtractResponse


class TesseractOcrEngine:
    """Runs Tesseract OCR against an MRZ zone crop."""

    STRONG_CANDIDATE_SCORE = 13_500

    def __init__(self, settings: SidecarSettings) -> None:
        self._settings = settings

    def extract(
        self,
        image: np.ndarray | list[np.ndarray],
        bbox: BoundingBox,
        quality_score: float,
        preprocessing_applied: list[str],
    ) -> OcrExtractResponse:
        started = time.monotonic()
        crops = [self._crop(source, bbox) for source in self._source_images(image)]
        mrz_lines = self._best_lines(crops, started)
        if not mrz_lines:
            raise OcrSidecarError(OcrStatus.MRZ_NOT_DETECTED, "Tesseract did not return MRZ text")

        confidence = self._confidence(crops[0], mrz_lines)
        return OcrExtractResponse(
            mrz_lines=mrz_lines,
            confidence=confidence,
            zone_bbox=bbox,
            image_quality_score=quality_score,
            preprocessing_applied=preprocessing_applied,
        )

    def _source_images(self, image: np.ndarray | list[np.ndarray]) -> list[np.ndarray]:
        if isinstance(image, list):
            return [source for source in image if source is not None]
        return [image]

    def _crop(self, image: np.ndarray, bbox: BoundingBox) -> np.ndarray:
        return image[bbox.y : bbox.y + bbox.height, bbox.x : bbox.x + bbox.width]

    def _best_lines(self, crop: np.ndarray | list[np.ndarray], started: float | None = None) -> list[str]:
        candidates: list[OcrCandidate] = []
        started = started if started is not None else time.monotonic()
        crops = crop if isinstance(crop, list) else [crop]

        for source_crop in crops:
            line_lines = self._ocr_line_crops(source_crop, started)
            if line_lines:
                candidates.extend(self._candidate_windows(line_lines))
                if self._has_strong_candidate(candidates) or self._has_line_shaped_candidate(candidates):
                    return self._best_candidate_lines(candidates)

        crop_variants = [self._candidate_crops(source_crop) for source_crop in crops]
        max_variants = max((len(variants) for variants in crop_variants), default=0)
        configs = self._candidate_configs()
        for variant_index in range(max_variants):
            for config in configs:
                for variants in crop_variants:
                    if variant_index >= len(variants):
                        continue
                    candidate_crop = variants[variant_index]
                    if self._budget_exhausted(started):
                        return self._best_candidate_lines(candidates)
                    text = self._image_to_string(candidate_crop, config, started)
                    if not text:
                        continue
                    lines = self._normalize_lines(text)
                    candidates.extend(self._candidate_windows(lines))
                if self._has_strong_candidate(candidates):
                    return self._best_candidate_lines(candidates)

        return self._best_candidate_lines(candidates)

    def _ocr_line_crops(self, crop: np.ndarray, started: float) -> list[str]:
        line_crops = self._mrz_line_crops(crop)
        if len(line_crops) < 2:
            return []

        lines: list[str] = []
        config = self._line_config()
        for line_crop in line_crops[:3]:
            if self._budget_exhausted(started):
                return []
            text = self._image_to_string(line_crop, config, started)
            normalized = self._normalize_lines(text)
            if normalized:
                lines.append(max(normalized, key=len))
        return lines if len(lines) in (2, 3) else []

    def _mrz_line_crops(self, crop: np.ndarray) -> list[np.ndarray]:
        if crop.size == 0:
            return []

        gray = crop if len(crop.shape) == 2 else cv2.cvtColor(crop, cv2.COLOR_BGR2GRAY)
        height, width = gray.shape[:2]
        _, threshold = cv2.threshold(gray, 0, 255, cv2.THRESH_BINARY_INV | cv2.THRESH_OTSU)
        kernel = cv2.getStructuringElement(cv2.MORPH_RECT, (max(15, width // 60), 3))
        joined = cv2.morphologyEx(threshold, cv2.MORPH_CLOSE, kernel)
        row_density = np.count_nonzero(joined, axis=1)
        minimum_density = max(8, int(width * 0.08))

        groups: list[tuple[int, int]] = []
        start: int | None = None
        for row, density in enumerate(row_density):
            if density >= minimum_density and start is None:
                start = row
            elif density < minimum_density and start is not None:
                if row - start >= max(8, height // 24):
                    groups.append((start, row))
                start = None
        if start is not None and height - start >= max(8, height // 24):
            groups.append((start, height))

        groups = groups[-3:]
        line_crops: list[np.ndarray] = []
        for start, end in groups:
            padding = max(6, int((end - start) * 0.45))
            y0 = max(0, start - padding)
            y1 = min(height, end + padding)
            line_crops.append(gray[y0:y1, 0:width])
        return line_crops

    def _image_to_string(self, crop: np.ndarray, config: str, started: float) -> str:
        timeout = self._remaining_tesseract_timeout(started)
        if timeout <= 0:
            return ""
        try:
            return pytesseract.image_to_string(
                crop,
                lang=self._settings.tesseract_language,
                config=config,
                timeout=timeout,
            )
        except (RuntimeError, pytesseract.TesseractError):
            return ""

    def _best_candidate_lines(self, candidates: list["OcrCandidate"]) -> list[str]:
        if not candidates:
            return []
        return max(candidates, key=lambda candidate: candidate.score).lines

    def _has_strong_candidate(self, candidates: list["OcrCandidate"]) -> bool:
        return any(
            candidate.score >= self.STRONG_CANDIDATE_SCORE and self._has_required_name_separator(candidate.lines)
            for candidate in candidates
        )

    def _has_line_shaped_candidate(self, candidates: list["OcrCandidate"]) -> bool:
        return any(
            self._has_required_name_separator(candidate.lines)
            and len(candidate.lines) in (2, 3)
            and sum(1 for line in candidate.lines if len(line) >= 30) >= 2
            for candidate in candidates
        )

    def _has_required_name_separator(self, lines: list[str]) -> bool:
        if len(lines) == 2:
            return len(lines[0]) >= 8 and "<<" in lines[0][5:]
        if len(lines) == 3:
            return len(lines[2]) >= 4 and "<<" in lines[2]
        return False

    def _budget_exhausted(self, started: float) -> bool:
        return time.monotonic() - started >= self._settings.extraction_budget_seconds

    def _remaining_tesseract_timeout(self, started: float) -> float:
        remaining = self._settings.extraction_budget_seconds - (time.monotonic() - started)
        return max(0.0, min(self._settings.tesseract_call_timeout_seconds, remaining))

    def _candidate_crops(self, crop: np.ndarray) -> list[np.ndarray]:
        crops = [crop]
        if crop.size == 0:
            return crops

        _, width = crop.shape[:2]
        if width < 1000:
            crops.append(cv2.resize(crop, None, fx=2, fy=2, interpolation=cv2.INTER_CUBIC))

        crops.append(cv2.copyMakeBorder(crop, 16, 16, 16, 16, cv2.BORDER_CONSTANT, value=255))
        return crops

    def _candidate_configs(self) -> list[str]:
        whitelist = "tessedit_char_whitelist=ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789<"
        configs = [
            self._settings.tesseract_config,
            f"--psm 7 -c {whitelist} preserve_interword_spaces=1",
        ]
        return list(dict.fromkeys(configs))

    def _line_config(self) -> str:
        whitelist = "tessedit_char_whitelist=ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789<"
        return f"--psm 7 -c {whitelist} preserve_interword_spaces=1"

    def _sharpen(self, crop: np.ndarray) -> np.ndarray:
        if crop.size == 0:
            return crop
        gaussian = cv2.GaussianBlur(crop, (0, 0), 1.0)
        return cv2.addWeighted(crop, 1.6, gaussian, -0.6, 0)

    def _morph_close(self, crop: np.ndarray) -> np.ndarray:
        if crop.size == 0:
            return crop
        kernel = cv2.getStructuringElement(cv2.MORPH_RECT, (2, 1))
        return cv2.morphologyEx(crop, cv2.MORPH_CLOSE, kernel)

    def _normalize_lines(self, text: str) -> list[str]:
        lines = []
        for line in text.splitlines():
            normalized = re.sub(r"[^A-Z0-9<]", "", line.upper().strip())
            if normalized:
                lines.append(normalized)
        return lines

    def _candidate_windows(self, lines: list[str]) -> list["OcrCandidate"]:
        if not lines:
            return []

        candidates = [OcrCandidate(lines=lines, score=self._mrz_shape_score(lines))]
        for size in (2, 3):
            if len(lines) < size:
                continue
            for start in range(0, len(lines) - size + 1):
                window = lines[start : start + size]
                score = self._mrz_shape_score(window) - start
                candidates.append(OcrCandidate(lines=window, score=score))
        return candidates

    def _mrz_shape_score(self, lines: list[str]) -> int:
        lengths = [len(line) for line in lines]
        score = sum(min(length, 44) for length in lengths)
        score += self._layout_score(lines, lengths)
        score += self._check_digit_score(lines)
        return score

    def _layout_score(self, lines: list[str], lengths: list[int]) -> int:
        if len(lines) == 2:
            return max(
                self._two_line_layout_score(lines, lengths, target=44, document_codes=("P", "V")),
                self._two_line_layout_score(lines, lengths, target=36, document_codes=("I", "A", "C", "V")),
            )
        if len(lines) == 3:
            return self._td1_layout_score(lines, lengths)
        return -2_000 * abs(len(lines) - 2)

    def _two_line_layout_score(
        self,
        lines: list[str],
        lengths: list[int],
        target: int,
        document_codes: tuple[str, ...],
    ) -> int:
        score = 0
        score += 7_000 if lengths == [target, target] else 0
        score -= 80 * sum(abs(target - length) for length in lengths)
        score += 1_500 if lines[0].startswith(document_codes) else -1_500
        score += 600 if len(lines[0]) > 1 and lines[0][1] == "<" else 0
        score += 800 if "<<" in lines[0] else -1_600
        score += self._name_line_noise_score(lines[0], target)
        score += self._state_code_score(lines, target)
        score += 500 if self._has_mrz_at(lines[1], (9, 19, 27)) else 0
        if target == 44:
            score += 500 if self._has_mrz_at(lines[1], (42, 43)) else 0
        else:
            score += 500 if self._has_mrz_at(lines[1], (35,)) else 0
        return score

    def _name_line_noise_score(self, line: str, target: int) -> int:
        if target not in (36, 44):
            return 0
        name_field = line[5:target] if len(line) > 5 else ""
        separator_index = name_field.find("<<")
        score = 0
        if separator_index >= 0:
            score += 600
            tail = name_field[separator_index + 2 :]
        else:
            tail = name_field[max(0, len(name_field) - 20) :]

        trailing_letters = sum(1 for char in tail[-20:] if "A" <= char <= "Z")
        filler_count = tail[-20:].count("<")
        score += filler_count * 20
        score -= trailing_letters * 120
        if "SSSS" in tail or "KK" in tail:
            score -= 900
        return score

    def _td1_layout_score(self, lines: list[str], lengths: list[int]) -> int:
        score = 7_000 if lengths == [30, 30, 30] else 0
        score -= 80 * sum(abs(30 - length) for length in lengths)
        score += 1_500 if lines[0].startswith(("I", "A", "C")) else -1_500
        score += 600 if len(lines[0]) > 1 and lines[0][1] == "<" else 0
        score += 800 if "<<" in lines[2] else -400
        if len(lines[0]) >= 5:
            score += self._alphabetic_triplet_score(lines[0][2:5])
        if len(lines[1]) >= 18:
            score += self._alphabetic_triplet_score(lines[1][15:18])
        score += 500 if self._has_mrz_at(lines[0], (14,)) else 0
        score += 500 if self._has_mrz_at(lines[1], (6, 14, 29)) else 0
        return score

    def _state_code_score(self, lines: list[str], target: int) -> int:
        score = 0
        if len(lines[0]) >= 5:
            score += self._alphabetic_triplet_score(lines[0][2:5])
        if len(lines[1]) >= 13:
            score += self._alphabetic_triplet_score(lines[1][10:13])
        if target == 44 and len(lines[0]) >= 7 and lines[0].startswith("PP"):
            # British passports often encode document code PP and nationality/name starts after GBR.
            score += self._alphabetic_triplet_score(lines[0][2:5])
        return score

    def _alphabetic_triplet_score(self, value: str) -> int:
        if len(value) != 3:
            return -300
        letters = sum(1 for char in value if "A" <= char <= "Z")
        fillers = value.count("<")
        digits = sum(1 for char in value if "0" <= char <= "9")
        return (letters * 180) + (fillers * 40) - (digits * 260)

    def _has_mrz_at(self, line: str, positions: tuple[int, ...]) -> bool:
        return all(len(line) > position and re.match(r"[A-Z0-9<]", line[position]) for position in positions)

    def _check_digit_score(self, lines: list[str]) -> int:
        if len(lines) == 2 and len(lines[0]) == 44 and len(lines[1]) == 44:
            return self._td3_check_digit_score(lines)
        if len(lines) == 2 and len(lines[0]) == 36 and len(lines[1]) == 36:
            return self._td2_check_digit_score(lines)
        if len(lines) == 3 and all(len(line) == 30 for line in lines):
            return self._td1_check_digit_score(lines)
        return 0

    def _td3_check_digit_score(self, lines: list[str]) -> int:
        line2 = lines[1]
        checks = [
            self._check_digit_passes(line2[0:9], line2[9]),
            self._check_digit_passes(line2[13:19], line2[19]),
            self._check_digit_passes(line2[21:27], line2[27]),
            self._check_digit_passes(line2[28:42], line2[42]),
            self._check_digit_passes(line2[0:10] + line2[13:20] + line2[21:43], line2[43]),
        ]
        return self._score_checks(checks)

    def _td2_check_digit_score(self, lines: list[str]) -> int:
        line2 = lines[1]
        checks = [
            self._check_digit_passes(line2[0:9], line2[9]),
            self._check_digit_passes(line2[13:19], line2[19]),
            self._check_digit_passes(line2[21:27], line2[27]),
            self._check_digit_passes(line2[0:10] + line2[13:20] + line2[21:35], line2[35]),
        ]
        return self._score_checks(checks)

    def _td1_check_digit_score(self, lines: list[str]) -> int:
        line1 = lines[0]
        line2 = lines[1]
        checks = [
            self._check_digit_passes(line1[5:14], line1[14]),
            self._check_digit_passes(line2[0:6], line2[6]),
            self._check_digit_passes(line2[8:14], line2[14]),
            self._check_digit_passes(line1[5:30] + line2[0:7] + line2[8:15] + line2[18:29], line2[29]),
        ]
        return self._score_checks(checks)

    def _score_checks(self, checks: list[bool | None]) -> int:
        score = 0
        for check in checks:
            if check is True:
                score += 1_200
            elif check is False:
                score -= 250
        return score

    def _check_digit_passes(self, field: str, expected: str) -> bool | None:
        if not expected.isdigit():
            return None
        try:
            return self._compute_check_digit(field) == int(expected)
        except ValueError:
            return None

    def _compute_check_digit(self, field: str) -> int:
        weights = (7, 3, 1)
        total = 0
        for index, value in enumerate(field):
            total += self._character_value(value) * weights[index % len(weights)]
        return total % 10

    def _character_value(self, value: str) -> int:
        if "0" <= value <= "9":
            return ord(value) - ord("0")
        if "A" <= value <= "Z":
            return ord(value) - ord("A") + 10
        if value == "<":
            return 0
        raise ValueError(f"Unsupported MRZ character: {value}")

    def _confidence(self, crop: np.ndarray, mrz_lines: list[str] | None = None) -> float:
        return self._structural_confidence(mrz_lines)

    def _structural_confidence(self, mrz_lines: list[str] | None) -> float:
        if not mrz_lines:
            return 0.0
        score = self._mrz_shape_score(mrz_lines)
        if score >= 14_000:
            return 0.82
        if score >= 12_000:
            return 0.72
        if score >= 9_000:
            return 0.55
        return 0.35


@dataclass(frozen=True)
class OcrCandidate:
    lines: list[str]
    score: int
