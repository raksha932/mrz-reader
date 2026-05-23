package com.certipass.mrtd.core.parsing;

import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

/**
 * Shared MRZ field cleanup helpers.
 */
final class MrzFieldCleaner {
    private static final Set<String> ACCEPTED_STATE_CODES = acceptedStateCodes();

    private MrzFieldCleaner() {
    }

    static String documentCode(String value) {
        return value.replace("<", "");
    }

    static String text(String value) {
        return value.replace('<', ' ').trim().replaceAll(" +", " ");
    }

    static String compact(String value) {
        return value.replace("<", "");
    }

    static String stateCode(String value) {
        if ("D<<".equals(value)) {
            return "DEU";
        }
        String normalized = value.replace('<', ' ')
                .trim()
                .replace("0", "O")
                .replace("1", "I")
                .replace("5", "S")
                .replace("6", "G")
                .replace("7", "T")
                .replace("8", "B");
        if (ACCEPTED_STATE_CODES.contains(normalized)) {
            return normalized;
        }
        return correctStateCode(normalized);
    }

    private static Set<String> acceptedStateCodes() {
        Set<String> codes = new HashSet<>(Locale.getISOCountries(Locale.IsoCountryCode.PART1_ALPHA3));
        codes.add("UTO");
        codes.add("UNO");
        codes.add("UNA");
        codes.add("GBD");
        codes.add("GBN");
        codes.add("GBO");
        codes.add("GBP");
        return Set.copyOf(codes);
    }

    private static String correctStateCode(String value) {
        if (value.length() != 3) {
            return value;
        }

        String best = null;
        int bestScore = Integer.MAX_VALUE;
        for (String candidate : ACCEPTED_STATE_CODES) {
            int score = stateCodeScore(value, candidate);
            if (score < 0 || score > 1) {
                continue;
            }
            if (score < bestScore) {
                best = candidate;
                bestScore = score;
            } else if (score == bestScore) {
                return value;
            }
        }
        return best == null ? value : best;
    }

    private static int stateCodeScore(String actual, String expected) {
        int score = 0;
        for (int index = 0; index < actual.length(); index++) {
            char actualChar = actual.charAt(index);
            char expectedChar = expected.charAt(index);
            if (actualChar == expectedChar) {
                continue;
            }
            if (actualChar == ' ' || isPlausibleStateSubstitution(actualChar, expectedChar)) {
                score++;
            } else {
                return -1;
            }
        }
        return score;
    }

    private static boolean isPlausibleStateSubstitution(char actual, char expected) {
        return (actual == 'B' && expected == 'S')
                || (actual == 'S' && expected == 'B')
                || (actual == 'S' && expected == 'U')
                || (actual == 'D' && expected == 'U')
                || (actual == 'O' && expected == 'U')
                || (actual == 'I' && expected == 'L')
                || (actual == 'L' && expected == 'I')
                || (actual == 'I' && expected == 'T')
                || (actual == 'T' && expected == 'I')
                || (actual == 'Z' && expected == 'A')
                || (actual == 'A' && expected == 'Z');
    }
}
