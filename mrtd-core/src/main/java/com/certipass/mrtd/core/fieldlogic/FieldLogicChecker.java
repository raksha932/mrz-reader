package com.certipass.mrtd.core.fieldlogic;

import com.certipass.mrtd.core.model.IdentityData;
import com.certipass.mrtd.core.model.MrzDate;
import com.certipass.mrtd.core.model.ValidationSeverity;
import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * Runs non-checksum business checks over parsed MRZ identity fields.
 */
public final class FieldLogicChecker {
    private final Clock clock;
    private final StateCodeRegistry stateCodeRegistry;

    public FieldLogicChecker() {
        this(Clock.systemUTC(), new StateCodeRegistry());
    }

    public FieldLogicChecker(Clock clock, StateCodeRegistry stateCodeRegistry) {
        this.clock = clock;
        this.stateCodeRegistry = stateCodeRegistry;
    }

    public List<FieldLogicResult> check(IdentityData identityData) {
        List<FieldLogicResult> results = new ArrayList<>();
        results.add(dateValidity("date_of_birth_valid", "date_of_birth", identityData.dateOfBirth()));
        results.add(dateValidity("date_of_expiry_valid", "date_of_expiry", identityData.dateOfExpiry()));
        results.add(expiry(identityData.dateOfExpiry()));
        results.add(stateCode("issuing_state_valid", "issuing_state", identityData.issuingState()));
        results.add(stateCode("nationality_valid", "nationality", identityData.nationality()));
        results.add(sex(identityData.sex()));
        return List.copyOf(results);
    }

    public boolean isExpired(IdentityData identityData) {
        return identityData.dateOfExpiry().parsed()
                .map(expiryDate -> expiryDate.isBefore(LocalDate.now(clock)))
                .orElse(false);
    }

    private FieldLogicResult dateValidity(String checkName, String fieldName, MrzDate date) {
        if (date.valid()) {
            return new FieldLogicResult(
                    checkName,
                    fieldName,
                    true,
                    ValidationSeverity.INFO,
                    "Valid calendar date");
        }
        return new FieldLogicResult(
                checkName,
                fieldName,
                false,
                ValidationSeverity.ERROR,
                "Invalid YYMMDD calendar date: " + date.rawValue());
    }

    private FieldLogicResult expiry(MrzDate expiryDate) {
        if (!expiryDate.valid()) {
            return new FieldLogicResult(
                    "document_not_expired",
                    "date_of_expiry",
                    false,
                    ValidationSeverity.ERROR,
                    "Cannot evaluate expiry because date_of_expiry is invalid");
        }

        LocalDate today = LocalDate.now(clock);
        LocalDate date = expiryDate.parsed().orElseThrow();
        if (date.isBefore(today)) {
            return new FieldLogicResult(
                    "document_not_expired",
                    "date_of_expiry",
                    false,
                    ValidationSeverity.WARNING,
                    "Document expired on " + date);
        }
        return new FieldLogicResult(
                "document_not_expired",
                "date_of_expiry",
                true,
                ValidationSeverity.INFO,
                "Expires " + date);
    }

    private FieldLogicResult stateCode(String checkName, String fieldName, String code) {
        if (stateCodeRegistry.isAccepted(code)) {
            return new FieldLogicResult(
                    checkName,
                    fieldName,
                    true,
                    ValidationSeverity.INFO,
                    code + " is an accepted MRZ state code");
        }
        return new FieldLogicResult(
                checkName,
                fieldName,
                false,
                ValidationSeverity.ERROR,
                code + " is not an accepted MRZ state code");
    }

    private FieldLogicResult sex(String value) {
        if ("M".equals(value) || "F".equals(value) || value == null || value.isBlank()) {
            return new FieldLogicResult(
                    "sex_field_valid",
                    "sex",
                    true,
                    ValidationSeverity.INFO,
                    "Valid value: " + (value == null || value.isBlank() ? "<" : value));
        }
        return new FieldLogicResult(
                "sex_field_valid",
                "sex",
                false,
                ValidationSeverity.ERROR,
                "Invalid sex field value: " + value);
    }
}
