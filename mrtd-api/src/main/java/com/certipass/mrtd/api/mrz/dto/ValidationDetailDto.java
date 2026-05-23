package com.certipass.mrtd.api.mrz.dto;

import com.certipass.mrtd.core.model.ValidationSeverity;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import io.swagger.v3.oas.annotations.media.Schema;

@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
@Schema(name = "ValidationDetail", description = "Single validation check result.")
public record ValidationDetailDto(
        @Schema(example = "document_number_check_digit")
        String checkName,
        @Schema(example = "document_number")
        String fieldName,
        @Schema(example = "true")
        boolean pass,
        @Schema(example = "INFO")
        ValidationSeverity severity,
        @Schema(example = "Document number check digit is valid")
        String message,
        @Schema(example = "8", nullable = true)
        Integer expectedDigit,
        @Schema(example = "8", nullable = true)
        Integer actualDigit
) {
}
