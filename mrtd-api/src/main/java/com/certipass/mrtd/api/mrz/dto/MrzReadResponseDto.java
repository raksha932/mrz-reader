package com.certipass.mrtd.api.mrz.dto;

import com.certipass.mrtd.core.model.Verdict;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
@Schema(name = "MrzReadResponse", description = "Completed MRZ read result with parsed identity data and validation details.")
public record MrzReadResponseDto(
        @Schema(description = "Overall business verdict for the parsed MRZ.", example = "VALID")
        Verdict verdict,
        @Schema(description = "Normalized confidence score from 0.0 to 1.0.", example = "0.97")
        double confidenceScore,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        IdentityDataDto identityData,
        @Schema(description = "Check digit, field logic, and warning validations.")
        List<ValidationDetailDto> validationResults,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        RawMrzDto rawMrz,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        ResultMetadataDto metadata
) {
}
