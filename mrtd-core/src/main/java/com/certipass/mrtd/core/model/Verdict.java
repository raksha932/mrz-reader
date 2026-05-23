package com.certipass.mrtd.core.model;

/**
 * Final verification verdict produced by the core MRZ engine.
 */
public enum Verdict {
    VALID,
    VALID_WITH_WARNINGS,
    INVALID
}
