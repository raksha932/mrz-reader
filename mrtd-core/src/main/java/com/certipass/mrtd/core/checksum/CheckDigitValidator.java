package com.certipass.mrtd.core.checksum;

import com.certipass.mrtd.core.detection.MrzFormat;
import java.util.ArrayList;
import java.util.List;

/**
 * Implements the ICAO Doc 9303 7-3-1 check digit algorithm.
 */
public final class CheckDigitValidator {
    private static final int[] WEIGHTS = {7, 3, 1};

    public int computeCheckDigit(String field) {
        int sum = 0;
        for (int index = 0; index < field.length(); index++) {
            sum += characterValue(field.charAt(index)) * WEIGHTS[index % WEIGHTS.length];
        }
        return sum % 10;
    }

    public CheckDigitResult validateCheckDigit(String fieldName, String field, char expectedDigit) {
        int expected = numericCheckDigit(expectedDigit);
        int actual = computeCheckDigit(field);
        return new CheckDigitResult(fieldName, expected, actual, expected == actual);
    }

    public List<CheckDigitResult> validateAllCheckDigits(MrzFormat format, List<String> mrzLines) {
        return switch (format) {
            case TD1 -> validateTd1(mrzLines);
            case TD2 -> validateTd2(mrzLines);
            case TD3 -> validateTd3(mrzLines);
        };
    }

    private List<CheckDigitResult> validateTd3(List<String> lines) {
        String line2 = lines.get(1);
        List<CheckDigitResult> results = new ArrayList<>();
        results.add(validateCheckDigit("document_number", line2.substring(0, 9), line2.charAt(9)));
        results.add(validateCheckDigit("date_of_birth", line2.substring(13, 19), line2.charAt(19)));
        results.add(validateCheckDigit("date_of_expiry", line2.substring(21, 27), line2.charAt(27)));
        results.add(validateCheckDigit("optional_data", line2.substring(28, 42), line2.charAt(42)));
        results.add(validateCheckDigit(
                "composite",
                line2.substring(0, 10) + line2.substring(13, 20) + line2.substring(21, 43),
                line2.charAt(43)));
        return List.copyOf(results);
    }

    private List<CheckDigitResult> validateTd2(List<String> lines) {
        String line2 = lines.get(1);
        List<CheckDigitResult> results = new ArrayList<>();
        results.add(validateCheckDigit("document_number", line2.substring(0, 9), line2.charAt(9)));
        results.add(validateCheckDigit("date_of_birth", line2.substring(13, 19), line2.charAt(19)));
        results.add(validateCheckDigit("date_of_expiry", line2.substring(21, 27), line2.charAt(27)));
        results.add(validateCheckDigit(
                "composite",
                line2.substring(0, 10) + line2.substring(13, 20) + line2.substring(21, 35),
                line2.charAt(35)));
        return List.copyOf(results);
    }

    private List<CheckDigitResult> validateTd1(List<String> lines) {
        String line1 = lines.get(0);
        String line2 = lines.get(1);
        List<CheckDigitResult> results = new ArrayList<>();
        results.add(validateCheckDigit("document_number", line1.substring(5, 14), line1.charAt(14)));
        results.add(validateCheckDigit("date_of_birth", line2.substring(0, 6), line2.charAt(6)));
        results.add(validateCheckDigit("date_of_expiry", line2.substring(8, 14), line2.charAt(14)));
        results.add(validateCheckDigit(
                "composite",
                line1.substring(5, 30) + line2.substring(0, 7) + line2.substring(8, 15) + line2.substring(18, 29),
                line2.charAt(29)));
        return List.copyOf(results);
    }

    private int numericCheckDigit(char value) {
        if (value >= '0' && value <= '9') {
            return value - '0';
        }
        return -1;
    }

    private int characterValue(char value) {
        if (value >= '0' && value <= '9') {
            return value - '0';
        }
        if (value >= 'A' && value <= 'Z') {
            return value - 'A' + 10;
        }
        if (value == '<') {
            return 0;
        }
        throw new IllegalArgumentException("Unsupported MRZ character: " + value);
    }
}
