package com.certipass.mrtd.core.orchestration;

import com.certipass.mrtd.core.detection.MrzFormatDetectionResult;
import com.certipass.mrtd.core.detection.MrzFormatDetector;
import com.certipass.mrtd.core.detection.MrzLineNormalizer;
import com.certipass.mrtd.core.ocr.OcrResult;
import java.util.List;

/**
 * Pipeline step that detects the MRZ document format.
 */
public final class FormatDetectionStep implements PipelineStep<Void> {
    private final MrzFormatDetector formatDetector;
    private final MrzLineNormalizer lineNormalizer;

    public FormatDetectionStep(MrzFormatDetector formatDetector) {
        this(formatDetector, new MrzLineNormalizer());
    }

    public FormatDetectionStep(MrzFormatDetector formatDetector, MrzLineNormalizer lineNormalizer) {
        this.formatDetector = formatDetector;
        this.lineNormalizer = lineNormalizer;
    }

    @Override
    public StepResult<Void> execute(PipelineContext context) {
        OcrResult ocrResult = context.ocrResult();
        List<String> normalizedLines = lineNormalizer.normalize(ocrResult.mrzLines());
        if (!normalizedLines.equals(ocrResult.mrzLines())) {
            context.ocrResult(new OcrResult(
                    normalizedLines,
                    ocrResult.confidence(),
                    ocrResult.qualityScore(),
                    ocrResult.zoneBoundingBox(),
                    ocrResult.preprocessingApplied()));
        }

        MrzFormatDetectionResult result = formatDetector.detect(normalizedLines);
        return switch (result) {
            case MrzFormatDetectionResult.Detected detected -> {
                context.mrzFormat(detected.format());
                yield new StepResult.Success<>(null);
            }
            case MrzFormatDetectionResult.Failure failure ->
                    new StepResult.Failure<>(failure.errorCode(), failure.message());
        };
    }
}
