from dataclasses import dataclass
import os


@dataclass(frozen=True)
class SidecarSettings:
    """Runtime settings for OCR extraction."""

    quality_threshold: float = 0.40
    tesseract_language: str = "ocrb_int"
    tesseract_config: str = "--psm 6 -c tessedit_char_whitelist=ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789<"
    tesseract_call_timeout_seconds: float = 0.30
    extraction_budget_seconds: float = 1.10

    @staticmethod
    def from_env() -> "SidecarSettings":
        return SidecarSettings(
            quality_threshold=float(os.getenv("OCR_QUALITY_THRESHOLD", "0.40")),
            tesseract_language=os.getenv("TESSERACT_LANGUAGE", "ocrb_int"),
            tesseract_config=os.getenv(
                "TESSERACT_CONFIG",
                "--psm 6 -c tessedit_char_whitelist=ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789<",
            ),
            tesseract_call_timeout_seconds=float(os.getenv("TESSERACT_CALL_TIMEOUT_SECONDS", "0.30")),
            extraction_budget_seconds=float(os.getenv("OCR_EXTRACTION_BUDGET_SECONDS", "1.10")),
        )
