package com.certipass.mrtd.api.mrz;

import com.certipass.mrtd.api.error.ApiErrorResponse;
import com.certipass.mrtd.api.error.ApiException;
import com.certipass.mrtd.api.error.ErrorResponseMapper;
import com.certipass.mrtd.api.config.OpenApiConfig;
import com.certipass.mrtd.api.mrz.dto.MrzReadRequestDto;
import com.certipass.mrtd.api.mrz.dto.MrzReadResponseDto;
import com.certipass.mrtd.api.web.RequestIdProvider;
import com.certipass.mrtd.core.model.ErrorCode;
import com.certipass.mrtd.core.model.MrzErrorResult;
import com.certipass.mrtd.core.model.MrzReadResult;
import com.certipass.mrtd.core.model.MrzSuccessResult;
import com.certipass.mrtd.core.orchestration.MrzReaderOrchestrator;
import jakarta.servlet.http.HttpServletRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.io.IOException;
import java.util.Set;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/v1/mrz")
@Tag(name = "MRZ Reader", description = "Extract, parse, and validate Machine Readable Zone data from document images.")
public class MrzController {
    private static final Set<String> ALLOWED_CONTENT_TYPES = Set.of(
            MediaType.IMAGE_JPEG_VALUE,
            MediaType.IMAGE_PNG_VALUE);

    private final MrzReaderOrchestrator orchestrator;
    private final MrzResponseMapper mapper;
    private final ErrorResponseMapper errorResponseMapper;
    private final RequestIdProvider requestIdProvider;

    public MrzController(
            MrzReaderOrchestrator orchestrator,
            MrzResponseMapper mapper,
            ErrorResponseMapper errorResponseMapper,
            RequestIdProvider requestIdProvider) {
        this.orchestrator = orchestrator;
        this.mapper = mapper;
        this.errorResponseMapper = errorResponseMapper;
        this.requestIdProvider = requestIdProvider;
    }

    @PostMapping(path = "/read", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(
            operationId = "readMrz",
            summary = "Read MRZ data from an uploaded document image",
            description = "Accepts a JPEG or PNG image, sends it through the OCR sidecar, and returns parsed identity data, validation details, raw MRZ lines, and processing metadata.",
            security = @SecurityRequirement(name = OpenApiConfig.API_KEY_SECURITY_SCHEME),
            requestBody = @io.swagger.v3.oas.annotations.parameters.RequestBody(
                    required = true,
                    description = "Multipart form-data payload with a file part named `file`.",
                    content = @Content(
                            mediaType = MediaType.MULTIPART_FORM_DATA_VALUE,
                            schema = @Schema(implementation = MrzReadRequestDto.class))))
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "MRZ read completed. The `verdict` field distinguishes valid, warning, and invalid document data.",
                    content = @Content(
                            mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = MrzReadResponseDto.class))),
            @ApiResponse(
                    responseCode = "400",
                    description = "Invalid upload, empty file, unsupported media type, or malformed multipart request.",
                    content = @Content(
                            mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(
                    responseCode = "401",
                    description = "Missing or invalid API key in the X-API-Key header.",
                    content = @Content(
                            mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(
                    responseCode = "422",
                    description = "Fatal MRZ pipeline error such as unsupported format, incomplete MRZ, or MRZ not detected.",
                    content = @Content(
                            mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(
                    responseCode = "429",
                    description = "Per-key rate limit exceeded. The response includes a Retry-After header.",
                    content = @Content(
                            mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(
                    responseCode = "503",
                    description = "OCR sidecar is unavailable or did not return a usable response.",
                    content = @Content(
                            mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    public ResponseEntity<?> readMrz(
            @Parameter(description = "JPEG or PNG image containing a visible MRZ.", required = true)
            @RequestPart("file") MultipartFile file,
            @Parameter(hidden = true) HttpServletRequest request) throws IOException {
        validateUpload(file);
        MrzReadResult result = orchestrator.readMrz(file.getBytes());
        String requestId = requestIdProvider.currentRequestId(request);
        return switch (result) {
            case MrzSuccessResult success -> ResponseEntity.ok(mapper.toDto(success, requestId));
            case MrzErrorResult error -> ResponseEntity
                    .status(errorResponseMapper.statusFor(error.errorCode()))
                    .body(ApiErrorResponse.from(error, requestId));
        };
    }

    private void validateUpload(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new ApiException(
                    ErrorCode.INVALID_IMAGE,
                    HttpStatus.BAD_REQUEST,
                    "Uploaded file is required and must not be empty.");
        }

        String contentType = file.getContentType();
        if (contentType == null || !ALLOWED_CONTENT_TYPES.contains(contentType)) {
            throw new ApiException(
                    ErrorCode.INVALID_IMAGE,
                    HttpStatus.BAD_REQUEST,
                    "Unsupported file content type. Only image/jpeg and image/png are accepted.");
        }
    }
}
