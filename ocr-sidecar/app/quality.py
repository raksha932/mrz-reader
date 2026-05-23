import cv2
import numpy as np

from app.models.response import BoundingBox


class ImageQualityScorer:
    """Scores MRZ crop sharpness using normalized Laplacian variance."""

    def score(self, image: np.ndarray, bbox: BoundingBox) -> float:
        crop = image[bbox.y : bbox.y + bbox.height, bbox.x : bbox.x + bbox.width]
        if crop.size == 0:
            return 0.0
        variance = cv2.Laplacian(crop, cv2.CV_64F).var()
        return max(0.0, min(1.0, variance / 1000.0))
