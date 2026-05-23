package com.certipass.mrtd.core.orchestration;

import java.util.List;

/**
 * Executes pipeline steps sequentially and stops at the first terminal failure.
 */
public final class PipelineExecutor {
    private final List<PipelineStep<?>> steps;

    public PipelineExecutor(List<PipelineStep<?>> steps) {
        this.steps = List.copyOf(steps);
    }

    public StepResult<PipelineContext> execute(PipelineContext context) {
        long started = System.nanoTime();
        for (PipelineStep<?> step : steps) {
            StepResult<?> result = step.execute(context);
            if (result instanceof StepResult.Failure<?> failure) {
                context.processingTimeMs(elapsedMillis(started));
                return new StepResult.Failure<>(failure.errorCode(), failure.message());
            }
        }
        context.processingTimeMs(elapsedMillis(started));
        return new StepResult.Success<>(context);
    }

    private long elapsedMillis(long startedNanos) {
        return (System.nanoTime() - startedNanos) / 1_000_000L;
    }
}
