package com.certipass.mrtd.core.orchestration;

/**
 * One executable step in the MRZ reading pipeline.
 */
public interface PipelineStep<T> {
    StepResult<T> execute(PipelineContext context);
}
