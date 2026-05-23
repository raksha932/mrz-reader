package com.certipass.mrtd.core.orchestration;

import com.certipass.mrtd.core.checksum.CheckDigitResult;
import com.certipass.mrtd.core.fieldlogic.FieldLogicResult;
import com.certipass.mrtd.core.model.ConfidenceScore;
import com.certipass.mrtd.core.model.IdentityData;
import com.certipass.mrtd.core.model.MrzRawData;
import com.certipass.mrtd.core.model.MrzSuccessResult;
import com.certipass.mrtd.core.model.ResultMetadata;
import com.certipass.mrtd.core.model.ValidationDetail;
import com.certipass.mrtd.core.model.ValidationSeverity;
import com.certipass.mrtd.core.model.Verdict;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Builds the final success result from pipeline state.
 */
public final class ResultBuilder {
    private static final String API_VERSION = "1.0.0";
    private final double confidenceThreshold;

    public ResultBuilder() {
        this(0.90);
    }

    public ResultBuilder(double confidenceThreshold) {
        if (confidenceThreshold < 0.0 || confidenceThreshold > 1.0) {
            throw new IllegalArgumentException("Confidence threshold must be between 0.0 and 1.0");
        }
        this.confidenceThreshold = confidenceThreshold;
    }

    public MrzSuccessResult build(PipelineContext context) {
        IdentityData identityData = context.identityData();
        boolean expired = context.fieldLogicResults().stream()
                .anyMatch(result -> "document_not_expired".equals(result.checkName())
                        && !result.pass()
                        && result.severity() == ValidationSeverity.WARNING);
        if (identityData != null) {
            identityData = identityData.withExpired(expired);
        }

        List<ValidationDetail> validations = new ArrayList<>();
        validations.addAll(context.checkDigitResults().stream().map(this::toValidationDetail).toList());
        validations.addAll(context.fieldLogicResults().stream().map(this::toValidationDetail).toList());

        double ocrConfidence = context.ocrResult().confidence();
        if (ocrConfidence < confidenceThreshold) {
            validations.add(new ValidationDetail(
                    "ocr_confidence_threshold",
                    "ocr_confidence",
                    false,
                    ValidationSeverity.WARNING,
                    "OCR confidence " + ocrConfidence + " is below threshold " + confidenceThreshold,
                    null,
                    null));
        }

        Verdict verdict = verdict(validations);
        return new MrzSuccessResult(
                verdict,
                identityData,
                validations,
                new ConfidenceScore(ocrConfidence),
                new MrzRawData(context.mrzFormat(), context.ocrResult().mrzLines(), context.originalOcrLines()),
                new ResultMetadata(
                        context.processingTimeMs(),
                        ocrConfidence,
                        context.mrzFormat(),
                        API_VERSION,
                        Instant.now()));
    }

    private Verdict verdict(List<ValidationDetail> validations) {
        boolean hasError = validations.stream()
                .anyMatch(result -> !result.pass() && result.severity() == ValidationSeverity.ERROR);
        if (hasError) {
            return Verdict.INVALID;
        }

        boolean hasWarning = validations.stream()
                .anyMatch(result -> !result.pass() && result.severity() == ValidationSeverity.WARNING);
        return hasWarning ? Verdict.VALID_WITH_WARNINGS : Verdict.VALID;
    }

    private ValidationDetail toValidationDetail(CheckDigitResult result) {
        return new ValidationDetail(
                "check_digit",
                result.fieldName(),
                result.pass(),
                result.pass() ? ValidationSeverity.INFO : ValidationSeverity.ERROR,
                result.pass() ? "Check digit passed" : "Check digit failed",
                result.expectedDigit(),
                result.actualDigit());
    }

    private ValidationDetail toValidationDetail(FieldLogicResult result) {
        return new ValidationDetail(
                result.checkName(),
                result.fieldName(),
                result.pass(),
                result.severity(),
                result.message(),
                null,
                null);
    }
}
