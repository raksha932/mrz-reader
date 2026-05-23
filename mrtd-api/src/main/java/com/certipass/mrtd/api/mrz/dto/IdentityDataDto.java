package com.certipass.mrtd.api.mrz.dto;

import com.certipass.mrtd.core.model.DocumentType;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDate;

@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
@Schema(name = "IdentityData", description = "Structured identity fields parsed from a TD1, TD2, or TD3 MRZ.")
public record IdentityDataDto(
        @Schema(example = "PASSPORT")
        DocumentType documentType,
        @Schema(example = "P")
        String documentCode,
        @Schema(example = "NGA")
        String issuingState,
        @Schema(example = "OKONKWO")
        String surname,
        @Schema(example = "CHUKWUEMEKA JOHN")
        String givenNames,
        @Schema(example = "A12345678")
        String documentNumber,
        @Schema(example = "NGA")
        String nationality,
        @Schema(example = "1990-08-15")
        LocalDate dateOfBirth,
        @Schema(example = "900815")
        String rawDateOfBirth,
        @Schema(example = "true")
        boolean dateOfBirthValid,
        @Schema(example = "M", allowableValues = {"M", "F", "X", "<"})
        String sex,
        @Schema(example = "2030-08-14")
        LocalDate dateOfExpiry,
        @Schema(example = "300814")
        String rawDateOfExpiry,
        @Schema(example = "true")
        boolean dateOfExpiryValid,
        @Schema(example = "")
        String optionalData,
        @Schema(example = "false")
        boolean expired
) {
}
