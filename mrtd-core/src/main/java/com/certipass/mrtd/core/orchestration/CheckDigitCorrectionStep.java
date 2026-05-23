package com.certipass.mrtd.core.orchestration;

import com.certipass.mrtd.core.checksum.CheckDigitCorrector;
import com.certipass.mrtd.core.ocr.OcrResult;
import java.util.List;

/**
 * Repairs likely OCR substitutions in fields protected by ICAO check digits.
 */
public final class CheckDigitCorrectionStep implements PipelineStep<Void> {
    private final CheckDigitCorrector corrector;

    public CheckDigitCorrectionStep(CheckDigitCorrector corrector) {
        this.corrector = corrector;
    }

    @Override
    public StepResult<Void> execute(PipelineContext context) {
        OcrResult ocrResult = context.ocrResult();
        List<String> correctedLines = corrector.correct(context.mrzFormat(), ocrResult.mrzLines());
        if (!correctedLines.equals(ocrResult.mrzLines())) {
            context.ocrResult(new OcrResult(
                    correctedLines,
                    ocrResult.confidence(),
                    ocrResult.qualityScore(),
                    ocrResult.zoneBoundingBox(),
                    ocrResult.preprocessingApplied()));
        }
        return new StepResult.Success<>(null);
    }
}
