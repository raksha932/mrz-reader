package com.certipass.mrtd.api.error;

import com.certipass.mrtd.core.model.ErrorCode;
import com.certipass.mrtd.core.model.MrzErrorResult;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.List;

@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(name = "ApiErrorResponse", description = "Standard error envelope returned by API boundary and fatal pipeline errors.")
public record ApiErrorResponse(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        ApiError error,
        @Schema(description = "Raw OCR MRZ lines when a pipeline error occurs after OCR extraction.")
        ErrorRawMrz rawMrz) {
    public static ApiErrorResponse from(MrzErrorResult result) {
        return from(result, null);
    }

    public static ApiErrorResponse from(MrzErrorResult result, String requestId) {
        return new ApiErrorResponse(new ApiError(
                result.errorCode(),
                result.message(),
                result.timestamp(),
                requestId),
                result.ocrLines().isEmpty()
                        ? null
                        : new ErrorRawMrz(result.ocrLines(), result.originalOcrLines(), result.corrected()));
    }

    public static ApiErrorResponse of(ErrorCode code, String message) {
        return of(code, message, null);
    }

    public static ApiErrorResponse of(ErrorCode code, String message, String requestId) {
        return new ApiErrorResponse(new ApiError(code, message, Instant.now(), requestId), null);
    }

    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    @JsonInclude(JsonInclude.Include.NON_NULL)
    @Schema(name = "ApiError", description = "Machine-readable error details with a correlation request id.")
    public record ApiError(
            @Schema(description = "Stable application error code.", example = "MRZ_NOT_DETECTED")
            ErrorCode code,
            @Schema(description = "Human-readable diagnostic message.", example = "Document found but MRZ zone could not be isolated")
            String message,
            @Schema(description = "UTC time when the error response was produced.", example = "2026-05-21T14:30:00Z")
            Instant timestamp,
            @Schema(description = "Correlation id also returned in the X-Request-ID header.", example = "req_abc123")
            @JsonProperty("request_id") String requestId) {
    }

    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    @Schema(name = "ErrorRawMrz", description = "OCR text available for pipeline failures after OCR extraction.")
    public record ErrorRawMrz(
            @Schema(description = "Final normalized/corrected OCR lines available at failure time.")
            List<String> lines,
            @Schema(description = "MRZ text lines as originally returned by OCR before core normalization/correction.")
            List<String> ocrLines,
            @Schema(description = "True when the core changed OCR lines before the failure.")
            boolean corrected) {
        public ErrorRawMrz {
            lines = lines == null ? List.of() : List.copyOf(lines);
            ocrLines = ocrLines == null || ocrLines.isEmpty() ? lines : List.copyOf(ocrLines);
        }
    }
}
