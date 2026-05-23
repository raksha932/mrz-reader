package com.certipass.mrtd.core.checksum;

import com.certipass.mrtd.core.detection.MrzFormat;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Uses ICAO check digits to repair high-confidence OCR substitutions in protected MRZ fields.
 */
public final class CheckDigitCorrector {
    private static final int MAX_FIELD_EDITS = 2;

    private final CheckDigitValidator validator;

    public CheckDigitCorrector() {
        this(new CheckDigitValidator());
    }

    public CheckDigitCorrector(CheckDigitValidator validator) {
        this.validator = validator;
    }

    public List<String> correct(MrzFormat format, List<String> mrzLines) {
        List<StringBuilder> corrected = mrzLines.stream().map(StringBuilder::new).toList();
        for (FieldSpec spec : fields(format)) {
            correctField(corrected.get(spec.lineIndex()), spec);
        }
        return corrected.stream().map(StringBuilder::toString).toList();
    }

    private void correctField(StringBuilder line, FieldSpec spec) {
        String field = line.substring(spec.fieldStart(), spec.fieldEnd());
        char checkDigit = line.charAt(spec.checkDigitIndex());
        int expectedDigit = normalizedCheckDigit(checkDigit);
        if (expectedDigit < 0) {
            return;
        }

        int currentDigit = validator.computeCheckDigit(field);
        if (currentDigit == expectedDigit) {
            line.setCharAt(spec.checkDigitIndex(), (char) ('0' + expectedDigit));
            return;
        }

        List<Candidate> candidates = new ArrayList<>();
        buildCandidates(field, spec, 0, new StringBuilder(), 0, candidates);
        candidates.stream()
                .sorted(Comparator.comparingInt(Candidate::edits).thenComparing(Candidate::text))
                .filter(candidate -> validator.computeCheckDigit(candidate.text()) == expectedDigit)
                .findFirst()
                .ifPresent(candidate -> {
                    line.replace(spec.fieldStart(), spec.fieldEnd(), candidate.text());
                    line.setCharAt(spec.checkDigitIndex(), (char) ('0' + expectedDigit));
                });
    }

    private void buildCandidates(
            String value,
            FieldSpec spec,
            int index,
            StringBuilder candidate,
            int edits,
            List<Candidate> candidates) {
        if (edits > MAX_FIELD_EDITS) {
            return;
        }
        if (index == value.length()) {
            candidates.add(new Candidate(candidate.toString(), edits));
            return;
        }

        char original = value.charAt(index);
        for (char alternative : alternatives(original, spec)) {
            candidate.append(alternative);
            buildCandidates(value, spec, index + 1, candidate, edits + (alternative == original ? 0 : 1), candidates);
            candidate.setLength(candidate.length() - 1);
        }
    }

    private List<Character> alternatives(char value, FieldSpec spec) {
        List<Character> alternatives = new ArrayList<>();
        alternatives.add(value);
        for (char mapped : mappedAlternatives(value, spec)) {
            if (mapped != value && !alternatives.contains(mapped)) {
                alternatives.add(mapped);
            }
        }
        return alternatives;
    }

    private List<Character> mappedAlternatives(char value, FieldSpec spec) {
        if (spec.numericOnly()) {
            char mapped = toDigit(value);
            return mapped == value ? List.of() : List.of(mapped);
        }
        List<Character> alternatives = new ArrayList<>(switch (value) {
            case '0' -> List.of('O', 'D');
            case 'O', 'Q', 'D' -> List.of('0');
            case '1' -> List.of('I', 'L');
            case 'I', 'L' -> List.of('1');
            case '2' -> List.of('Z');
            case 'Z' -> List.of('2');
            case '5' -> List.of('S');
            case 'S' -> List.of('5');
            case '6' -> List.of('G');
            case 'G' -> List.of('6');
            case '8' -> List.of('B');
            case 'B' -> List.of('8');
            default -> List.of();
        });
        if (spec.lenientOptionalData()) {
            if (value == '4') {
                alternatives.add('1');
            } else if (value == '1') {
                alternatives.add('4');
            }
        }
        return alternatives;
    }

    private int normalizedCheckDigit(char value) {
        if (value >= '0' && value <= '9') {
            return value - '0';
        }
        char mapped = toDigit(value);
        return mapped >= '0' && mapped <= '9' ? mapped - '0' : -1;
    }

    private char toDigit(char value) {
        return switch (value) {
            case 'O', 'Q', 'D' -> '0';
            case 'I', 'L' -> '1';
            case 'Z' -> '2';
            case 'S' -> '5';
            case 'G' -> '6';
            case 'B' -> '8';
            default -> value;
        };
    }

    private List<FieldSpec> fields(MrzFormat format) {
        return switch (format) {
            case TD1 -> List.of(
                    new FieldSpec(0, 5, 14, 14, false, false),
                    new FieldSpec(1, 0, 6, 6, true, false),
                    new FieldSpec(1, 8, 14, 14, true, false));
            case TD2 -> List.of(
                    new FieldSpec(1, 0, 9, 9, false, false),
                    new FieldSpec(1, 13, 19, 19, true, false),
                    new FieldSpec(1, 21, 27, 27, true, false));
            case TD3 -> List.of(
                    new FieldSpec(1, 0, 9, 9, false, false),
                    new FieldSpec(1, 13, 19, 19, true, false),
                    new FieldSpec(1, 21, 27, 27, true, false),
                    new FieldSpec(1, 28, 42, 42, false, true));
        };
    }

    private record FieldSpec(
            int lineIndex,
            int fieldStart,
            int fieldEnd,
            int checkDigitIndex,
            boolean numericOnly,
            boolean lenientOptionalData) {
    }

    private record Candidate(String text, int edits) {
    }
}
