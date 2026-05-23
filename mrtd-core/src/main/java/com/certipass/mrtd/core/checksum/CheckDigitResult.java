package com.certipass.mrtd.core.checksum;

/**
 * Outcome of validating one ICAO check digit.
 */
public record CheckDigitResult(String fieldName, int expectedDigit, int actualDigit, boolean pass) {
}
