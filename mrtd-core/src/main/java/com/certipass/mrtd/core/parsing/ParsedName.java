package com.certipass.mrtd.core.parsing;

/**
 * Name components decoded from an MRZ name field.
 */
public record ParsedName(String surname, String givenNames) {
}
