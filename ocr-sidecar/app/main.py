from fastapi import FastAPI, File, UploadFile
from fastapi.responses import JSONResponse

from app.errors import OcrSidecarError
from app.models.response import OcrErrorResponse, OcrExtractResponse
from app.service import OcrPipelineService


def create_app(service: OcrPipelineService | None = None) -> FastAPI:
    """Create the FastAPI application."""

    app = FastAPI(title="MRTD OCR Sidecar", version="0.1.0")
    pipeline_service = service or OcrPipelineService()

    @app.get("/health")
    def health() -> dict[str, str]:
        return {"status": "UP"}

    @app.post(
        "/ocr/extract",
        response_model=OcrExtractResponse,
        responses={422: {"model": OcrErrorResponse}},
    )
    async def extract(image: UploadFile = File(...)) -> OcrExtractResponse | JSONResponse:
        try:
            image_bytes = await image.read()
            return pipeline_service.extract(image_bytes)
        except OcrSidecarError as error:
            response = OcrErrorResponse(
                status=error.status,
                message=error.message,
                image_quality_score=error.image_quality_score,
            )
            return JSONResponse(status_code=422, content=response.model_dump(mode="json"))

    return app


app = create_app()
