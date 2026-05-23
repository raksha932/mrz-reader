package com.certipass.mrtd.api.mrz.dto;

import com.certipass.mrtd.core.detection.MrzFormat;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
@Schema(name = "RawMrz", description = "Raw MRZ lines retained alongside the parsed result.")
public record RawMrzDto(
        @Schema(example = "TD3")
        MrzFormat format,
        @Schema(description = "Final normalized/corrected MRZ text lines used by the parser.", example = "[\"P<NGAOKONKWO<<CHUKWUEMEKA<JOHN<<<<<<<<<<<<<<<\", \"A123456784NGA9008157M2908144<<<<<<<<<<<<<<06\"]")
        List<String> lines,
        @Schema(description = "MRZ text lines as originally returned by OCR before core normalization/correction.", example = "[\"P<NGAOKONKWO<<CHUKWUEMEKA<JOHN<<<<<<<<<<<<<<<\", \"A123456784NGA9008157M29O8144<<<<<<<<<<<<<<06\"]")
        List<String> ocrLines,
        @Schema(description = "True when the core changed OCR lines before parsing.", example = "true")
        boolean corrected) {
}
