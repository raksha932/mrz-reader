package com.certipass.mrtd.api.mrz.dto;

import com.certipass.mrtd.core.detection.MrzFormat;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;

@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(name = "ResultMetadata", description = "Processing metadata for an MRZ read.")
public record ResultMetadataDto(
        @Schema(example = "1243")
        long processingTimeMs,
        @Schema(example = "0.97")
        double ocrConfidence,
        @Schema(example = "TD3")
        MrzFormat mrzFormat,
        @Schema(example = "1.0.0")
        String apiVersion,
        @Schema(example = "2026-05-21T14:30:00Z")
        Instant timestamp,
        @Schema(description = "Correlation id also returned in the X-Request-ID header.", example = "req_abc123")
        @JsonProperty("request_id") String requestId
) {
}
