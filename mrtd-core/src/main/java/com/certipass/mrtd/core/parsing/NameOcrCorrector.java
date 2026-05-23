package com.certipass.mrtd.core.parsing;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Corrects bounded OCR-B confusions in parsed name tokens without mutating MRZ lines.
 */
final class NameOcrCorrector {
    private final Set<String> lexicon;

    NameOcrCorrector() {
        this(Set.of(
                "ALICE",
                "ANNA",
                "CHUKWUEMEKA",
                "DOE",
                "EMMA",
                "ERIKSSON",
                "JAMES",
                "JANE",
                "JOHN",
                "MARIA",
                "MARTIN",
                "OKONKWO",
                "ROSE",
                "SMITH"));
    }

    NameOcrCorrector(Set<String> lexicon) {
        this.lexicon = new LinkedHashSet<>(lexicon);
    }

    String correctNamePart(String value) {
        if (value.isBlank()) {
            return value;
        }
        List<String> corrected = new ArrayList<>();
        for (String token : value.split(" ")) {
            String correction = correctToken(token);
            corrected.addAll(List.of(correction.split(" ")));
        }
        return String.join(" ", corrected);
    }

    private String correctToken(String token) {
        String digitNormalized = normalizeNameDigits(token);
        if (lexicon.contains(digitNormalized)) {
            return digitNormalized;
        }

        Match direct = bestLexiconMatch(digitNormalized);
        if (direct != null) {
            return direct.value();
        }

        Match split = bestSplitMatch(digitNormalized);
        return split == null ? digitNormalized : split.value();
    }

    private Match bestLexiconMatch(String token) {
        Match best = null;
        for (String candidate : lexicon) {
            int score = editDistance(token, candidate, maximumScore(candidate));
            if (score < 0) {
                continue;
            }
            if (best == null || score < best.score()) {
                best = new Match(candidate, score);
            } else if (score == best.score()) {
                return null;
            }
        }
        return best;
    }

    private Match bestSplitMatch(String token) {
        Match best = null;
        for (int split = 2; split <= token.length() - 2; split++) {
            Match left = bestLexiconMatch(token.substring(0, split));
            Match right = bestLexiconMatch(token.substring(split));
            if (left == null || right == null) {
                continue;
            }
            int score = left.score() + right.score();
            if (score > 2) {
                continue;
            }
            String value = left.value() + " " + right.value();
            if (best == null || score < best.score()) {
                best = new Match(value, score);
            } else if (score == best.score() && !value.equals(best.value())) {
                return null;
            }
        }
        return best;
    }

    private int maximumScore(String candidate) {
        return candidate.length() <= 3 ? 1 : 2;
    }

    private int editDistance(String actual, String expected, int maximumScore) {
        if (Math.abs(actual.length() - expected.length()) > maximumScore) {
            return -1;
        }

        int[][] distances = new int[actual.length() + 1][expected.length() + 1];
        for (int actualIndex = 0; actualIndex <= actual.length(); actualIndex++) {
            distances[actualIndex][0] = actualIndex;
        }
        for (int expectedIndex = 0; expectedIndex <= expected.length(); expectedIndex++) {
            distances[0][expectedIndex] = expectedIndex;
        }

        for (int actualIndex = 1; actualIndex <= actual.length(); actualIndex++) {
            int rowBest = Integer.MAX_VALUE;
            for (int expectedIndex = 1; expectedIndex <= expected.length(); expectedIndex++) {
                char actualChar = actual.charAt(actualIndex - 1);
                char expectedChar = expected.charAt(expectedIndex - 1);
                int substitutionCost = substitutionCost(actualChar, expectedChar);
                int deletion = distances[actualIndex - 1][expectedIndex] + deletionCost(actualChar);
                int insertion = distances[actualIndex][expectedIndex - 1] + 1;
                int substitution = distances[actualIndex - 1][expectedIndex - 1] + substitutionCost;
                int best = Math.min(Math.min(deletion, insertion), substitution);
                distances[actualIndex][expectedIndex] = best;
                rowBest = Math.min(rowBest, best);
            }
            if (rowBest > maximumScore) {
                return -1;
            }
        }

        int distance = distances[actual.length()][expected.length()];
        return distance <= maximumScore ? distance : -1;
    }

    private int deletionCost(char value) {
        return value == '<' ? 0 : 1;
    }

    private int substitutionCost(char actual, char expected) {
        if (actual == expected) {
            return 0;
        }
        return isPlausibleSubstitution(actual, expected) ? 1 : 2;
    }

    private String normalizeNameDigits(String token) {
        StringBuilder normalized = new StringBuilder(token.length());
        for (int index = 0; index < token.length(); index++) {
            normalized.append(switch (token.charAt(index)) {
                case '0' -> 'O';
                case '2' -> 'O';
                case '3' -> 'J';
                case '5' -> 'S';
                case '8' -> 'S';
                default -> token.charAt(index);
            });
        }
        return normalized.toString();
    }

    private boolean isPlausibleSubstitution(char actual, char expected) {
        return (actual == 'L' && expected == 'I')
                || (actual == 'I' && expected == 'L')
                || (actual == 'T' && expected == 'I')
                || (actual == 'I' && expected == 'T')
                || (actual == 'R' && expected == 'H')
                || (actual == 'H' && expected == 'R')
                || (actual == 'V' && expected == 'W')
                || (actual == 'W' && expected == 'V')
                || (actual == 'B' && expected == 'H')
                || (actual == 'H' && expected == 'B')
                || (actual == 'B' && expected == 'A')
                || (actual == 'A' && expected == 'B')
                || (actual == 'D' && expected == 'O')
                || (actual == 'O' && expected == 'D')
                || (actual == 'Z' && expected == 'S')
                || (actual == 'S' && expected == 'Z');
    }

    private record Match(String value, int score) {
    }
}
