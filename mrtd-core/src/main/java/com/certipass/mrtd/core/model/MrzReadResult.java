package com.certipass.mrtd.core.model;

/**
 * Top-level result returned by the core engine.
 */
public sealed interface MrzReadResult permits MrzSuccessResult, MrzErrorResult {
}
