package com.certipass.mrtd.core.orchestration;

import com.certipass.mrtd.core.checksum.CheckDigitValidator;

/**
 * Pipeline step that validates all check digits for the detected format.
 */
public final class CheckDigitValidationStep implements PipelineStep<Void> {
    private final CheckDigitValidator checkDigitValidator;

    public CheckDigitValidationStep(CheckDigitValidator checkDigitValidator) {
        this.checkDigitValidator = checkDigitValidator;
    }

    @Override
    public StepResult<Void> execute(PipelineContext context) {
        context.checkDigitResults(
                checkDigitValidator.validateAllCheckDigits(context.mrzFormat(), context.ocrResult().mrzLines()));
        return new StepResult.Success<>(null);
    }
}
