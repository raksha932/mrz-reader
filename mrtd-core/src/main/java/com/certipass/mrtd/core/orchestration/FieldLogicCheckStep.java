package com.certipass.mrtd.core.orchestration;

import com.certipass.mrtd.core.fieldlogic.FieldLogicChecker;

/**
 * Pipeline step that runs non-checksum field logic.
 */
public final class FieldLogicCheckStep implements PipelineStep<Void> {
    private final FieldLogicChecker fieldLogicChecker;

    public FieldLogicCheckStep(FieldLogicChecker fieldLogicChecker) {
        this.fieldLogicChecker = fieldLogicChecker;
    }

    @Override
    public StepResult<Void> execute(PipelineContext context) {
        context.fieldLogicResults(fieldLogicChecker.check(context.identityData()));
        return new StepResult.Success<>(null);
    }
}
