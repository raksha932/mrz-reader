package com.certipass.mrtd.core.orchestration;

import com.certipass.mrtd.core.model.ErrorCode;

/**
 * Result of one pipeline step.
 */
public sealed interface StepResult<T> permits StepResult.Success, StepResult.Failure {
    record Success<T>(T value) implements StepResult<T> {
    }

    record Failure<T>(ErrorCode errorCode, String message) implements StepResult<T> {
    }
}
