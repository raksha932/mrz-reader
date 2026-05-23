package com.certipass.mrtd.api.mrz;

import com.certipass.mrtd.api.mrz.dto.IdentityDataDto;
import com.certipass.mrtd.api.mrz.dto.MrzReadResponseDto;
import com.certipass.mrtd.api.mrz.dto.RawMrzDto;
import com.certipass.mrtd.api.mrz.dto.ResultMetadataDto;
import com.certipass.mrtd.api.mrz.dto.ValidationDetailDto;
import com.certipass.mrtd.core.model.IdentityData;
import com.certipass.mrtd.core.model.MrzDate;
import com.certipass.mrtd.core.model.MrzSuccessResult;
import java.time.LocalDate;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class MrzResponseMapper {
    public MrzReadResponseDto toDto(MrzSuccessResult result) {
        return toDto(result, null);
    }

    public MrzReadResponseDto toDto(MrzSuccessResult result, String requestId) {
        return new MrzReadResponseDto(
                result.verdict(),
                result.confidenceScore().value(),
                toIdentityData(result.identityData()),
                result.validations().stream()
                        .map(validation -> new ValidationDetailDto(
                                validation.checkName(),
                                validation.fieldName(),
                                validation.pass(),
                                validation.severity(),
                                validation.message(),
                                validation.expectedDigit(),
                                validation.actualDigit()))
                        .toList(),
                new RawMrzDto(
                        result.rawMrz().format(),
                        result.rawMrz().lines(),
                        result.rawMrz().ocrLines(),
                        result.rawMrz().corrected()),
                new ResultMetadataDto(
                        result.metadata().processingTimeMs(),
                        result.metadata().ocrConfidence(),
                        result.metadata().mrzFormat(),
                        result.metadata().apiVersion(),
                        result.metadata().timestamp(),
                        requestId));
    }

    private IdentityDataDto toIdentityData(IdentityData identityData) {
        return new IdentityDataDto(
                identityData.documentType(),
                identityData.documentCode(),
                identityData.issuingState(),
                identityData.surname(),
                identityData.givenNames(),
                identityData.documentNumber(),
                identityData.nationality(),
                parsedDate(identityData.dateOfBirth()),
                identityData.dateOfBirth().rawValue(),
                identityData.dateOfBirth().valid(),
                identityData.sex(),
                parsedDate(identityData.dateOfExpiry()),
                identityData.dateOfExpiry().rawValue(),
                identityData.dateOfExpiry().valid(),
                identityData.optionalData(),
                identityData.expired());
    }

    private LocalDate parsedDate(MrzDate date) {
        return date.parsed().orElse(null);
    }
}
