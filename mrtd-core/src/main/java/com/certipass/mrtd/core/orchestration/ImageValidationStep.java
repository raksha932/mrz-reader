package com.certipass.mrtd.core.orchestration;

import com.certipass.mrtd.core.image.ImageValidationResult;
import com.certipass.mrtd.core.image.ImageValidator;

/**
 * Pipeline step that validates image bytes and stores image metadata.
 */
public final class ImageValidationStep implements PipelineStep<Void> {
    private final ImageValidator imageValidator;

    public ImageValidationStep(ImageValidator imageValidator) {
        this.imageValidator = imageValidator;
    }

    @Override
    public StepResult<Void> execute(PipelineContext context) {
        ImageValidationResult result = imageValidator.validate(context.inputImage());
        return switch (result) {
            case ImageValidationResult.Valid valid -> {
                context.imageMetadata(valid.metadata());
                yield new StepResult.Success<>(null);
            }
            case ImageValidationResult.Invalid invalid ->
                    new StepResult.Failure<>(invalid.errorCode(), invalid.message());
        };
    }
}
