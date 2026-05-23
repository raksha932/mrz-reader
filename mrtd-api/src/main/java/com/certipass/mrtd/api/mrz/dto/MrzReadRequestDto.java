package com.certipass.mrtd.api.mrz.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import org.springframework.web.multipart.MultipartFile;

@Schema(name = "MrzReadRequest", description = "Multipart upload payload containing a JPEG or PNG travel document image.")
public record MrzReadRequestDto(
        @Schema(
                description = "JPEG or PNG image containing a TD1, TD2, or TD3 MRZ.",
                type = "string",
                format = "binary",
                requiredMode = Schema.RequiredMode.REQUIRED)
        MultipartFile file
) {
}
