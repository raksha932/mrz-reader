package com.certipass.mrtd.core.parsing;

import com.certipass.mrtd.core.model.MrzDate;
import java.time.DateTimeException;
import java.time.LocalDate;

/**
 * Converts YYMMDD MRZ dates while preserving invalid raw values.
 */
public final class MrzDateParser {
    public MrzDate parse(String rawValue) {
        return parseBirthDate(rawValue);
    }

    public MrzDate parseBirthDate(String rawValue) {
        if (rawValue == null || rawValue.length() != 6 || !rawValue.chars().allMatch(Character::isDigit)) {
            return new MrzDate(rawValue == null ? "" : rawValue, null, false);
        }

        int yy = Integer.parseInt(rawValue.substring(0, 2));
        int month = Integer.parseInt(rawValue.substring(2, 4));
        int day = Integer.parseInt(rawValue.substring(4, 6));
        int year = yy <= 29 ? 2000 + yy : 1900 + yy;

        return parsed(rawValue, year, month, day);
    }

    public MrzDate parseExpiryDate(String rawValue) {
        if (rawValue == null || rawValue.length() != 6 || !rawValue.chars().allMatch(Character::isDigit)) {
            return new MrzDate(rawValue == null ? "" : rawValue, null, false);
        }

        int yy = Integer.parseInt(rawValue.substring(0, 2));
        int month = Integer.parseInt(rawValue.substring(2, 4));
        int day = Integer.parseInt(rawValue.substring(4, 6));
        int year = yy <= 79 ? 2000 + yy : 1900 + yy;

        return parsed(rawValue, year, month, day);
    }

    private MrzDate parsed(String rawValue, int year, int month, int day) {
        try {
            return new MrzDate(rawValue, LocalDate.of(year, month, day), true);
        } catch (DateTimeException ignored) {
            return new MrzDate(rawValue, null, false);
        }
    }
}
