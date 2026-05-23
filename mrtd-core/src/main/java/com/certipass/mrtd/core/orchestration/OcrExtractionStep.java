package com.certipass.mrtd.core.orchestration;

import com.certipass.mrtd.core.ocr.OcrClient;
import com.certipass.mrtd.core.ocr.OcrExtractionResult;

/**
 * Pipeline step that calls the configured OCR boundary.
 */
public final class OcrExtractionStep implements PipelineStep<Void> {
    private final OcrClient ocrClient;

    public OcrExtractionStep(OcrClient ocrClient) {
        this.ocrClient = ocrClient;
    }

    @Override
    public StepResult<Void> execute(PipelineContext context) {
        OcrExtractionResult result = ocrClient.extractMrz(context.inputImage());
        return switch (result) {
            case OcrExtractionResult.Success success -> {
                context.ocrResult(success.result());
                context.originalOcrLines(success.result().mrzLines());
                yield new StepResult.Success<>(null);
            }
            case OcrExtractionResult.Failure failure ->
                    new StepResult.Failure<>(failure.errorCode(), failure.message());
        };
    }
}
