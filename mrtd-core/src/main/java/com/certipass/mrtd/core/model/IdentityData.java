package com.certipass.mrtd.core.model;

/**
 * Structured identity fields parsed from a TD1, TD2, or TD3 MRZ.
 */
public record IdentityData(
        DocumentType documentType,
        String documentCode,
        String issuingState,
        String surname,
        String givenNames,
        String documentNumber,
        String nationality,
        MrzDate dateOfBirth,
        String sex,
        MrzDate dateOfExpiry,
        String optionalData,
        boolean expired
) {
    public IdentityData withExpired(boolean expired) {
        return new IdentityData(
                documentType,
                documentCode,
                issuingState,
                surname,
                givenNames,
                documentNumber,
                nationality,
                dateOfBirth,
                sex,
                dateOfExpiry,
                optionalData,
                expired);
    }
}
