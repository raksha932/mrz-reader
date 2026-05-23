package com.certipass.mrtd.core.orchestration;

import com.certipass.mrtd.core.checksum.CheckDigitValidator;
import com.certipass.mrtd.core.checksum.CheckDigitCorrector;
import com.certipass.mrtd.core.detection.MrzFormat;
import com.certipass.mrtd.core.detection.MrzFormatDetector;
import com.certipass.mrtd.core.fieldlogic.FieldLogicChecker;
import com.certipass.mrtd.core.image.ImageValidator;
import com.certipass.mrtd.core.model.MrzErrorResult;
import com.certipass.mrtd.core.model.MrzReadResult;
import com.certipass.mrtd.core.ocr.OcrClient;
import com.certipass.mrtd.core.parsing.MrzParser;
import com.certipass.mrtd.core.parsing.MrzParserFactory;
import com.certipass.mrtd.core.parsing.Td1MrzParser;
import com.certipass.mrtd.core.parsing.Td2MrzParser;
import com.certipass.mrtd.core.parsing.Td3MrzParser;
import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * Public entry point for the pure Java MRZ core engine.
 */
public final class MrzReaderOrchestrator {
    private final ImageValidator imageValidator;
    private final MrzFormatDetector formatDetector;
    private final MrzParserFactory parserFactory;
    private final CheckDigitCorrector checkDigitCorrector;
    private final CheckDigitValidator checkDigitValidator;
    private final FieldLogicChecker fieldLogicChecker;
    private final ResultBuilder resultBuilder;
    private final OcrClient ocrClient;

    public MrzReaderOrchestrator(OcrClient ocrClient) {
        this(
                new ImageValidator(),
                new MrzFormatDetector(),
                defaultParserFactory(),
                new CheckDigitCorrector(),
                new CheckDigitValidator(),
                new FieldLogicChecker(),
                new ResultBuilder(),
                ocrClient);
    }

    public MrzReaderOrchestrator(
            ImageValidator imageValidator,
            MrzFormatDetector formatDetector,
            MrzParserFactory parserFactory,
            CheckDigitCorrector checkDigitCorrector,
            CheckDigitValidator checkDigitValidator,
            FieldLogicChecker fieldLogicChecker,
            ResultBuilder resultBuilder,
            OcrClient ocrClient) {
        this.imageValidator = imageValidator;
        this.formatDetector = formatDetector;
        this.parserFactory = parserFactory;
        this.checkDigitCorrector = checkDigitCorrector;
        this.checkDigitValidator = checkDigitValidator;
        this.fieldLogicChecker = fieldLogicChecker;
        this.resultBuilder = resultBuilder;
        this.ocrClient = ocrClient;
    }

    public MrzReadResult readMrz(byte[] imageData) {
        return readMrz(imageData, ocrClient);
    }

    public MrzReadResult readMrz(byte[] imageData, OcrClient customOcrClient) {
        PipelineContext context = new PipelineContext(imageData);
        PipelineExecutor executor = new PipelineExecutor(List.of(
                new ImageValidationStep(imageValidator),
                new OcrExtractionStep(customOcrClient),
                new FormatDetectionStep(formatDetector),
                new CheckDigitCorrectionStep(checkDigitCorrector),
                new FieldParsingStep(parserFactory),
                new CheckDigitValidationStep(checkDigitValidator),
                new FieldLogicCheckStep(fieldLogicChecker)));

        StepResult<PipelineContext> result = executor.execute(context);
        return switch (result) {
            case StepResult.Success<?> ignored -> resultBuilder.build(context);
            case StepResult.Failure<?> failure ->
                    new MrzErrorResult(
                            failure.errorCode(),
                            failure.message(),
                            Instant.now(),
                            context.ocrResult() == null ? List.of() : context.ocrResult().mrzLines(),
                            context.originalOcrLines());
        };
    }

    private static MrzParserFactory defaultParserFactory() {
        Map<MrzFormat, MrzParser> parsers = Map.of(
                MrzFormat.TD1, new Td1MrzParser(),
                MrzFormat.TD2, new Td2MrzParser(),
                MrzFormat.TD3, new Td3MrzParser());
        return new MrzParserFactory(parsers);
    }
}
