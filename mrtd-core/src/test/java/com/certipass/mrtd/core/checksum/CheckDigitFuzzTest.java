package com.certipass.mrtd.core.checksum;

import static org.assertj.core.api.Assertions.assertThat;

import com.certipass.mrtd.core.detection.MrzFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.function.Function;
import org.junit.jupiter.api.Test;

class CheckDigitFuzzTest {
    private static final int RAW_FIELD_CASES = 10_000;
    private static final int DOCUMENT_CASES_PER_FORMAT = 1_000;
    private static final String MRZ_CHARS = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789<";

    private final CheckDigitValidator validator = new CheckDigitValidator();
    private final Random random = new Random(9303L);

    @Test
    void fuzzesRawCheckDigitComputationWithoutFalsePassesOrFalseFails() {
        for (int caseIndex = 0; caseIndex < RAW_FIELD_CASES; caseIndex++) {
            String field = randomField(1 + random.nextInt(64));
            int checkDigit = validator.computeCheckDigit(field);

            CheckDigitResult correct = validator.validateCheckDigit("fuzz_field", field, digitChar(checkDigit));
            assertThat(correct.pass())
                    .as("valid check digit must pass for field %s", field)
                    .isTrue();

            for (char wrongDigit = '0'; wrongDigit <= '9'; wrongDigit++) {
                if (wrongDigit == digitChar(checkDigit)) {
                    continue;
                }

                CheckDigitResult incorrect = validator.validateCheckDigit("fuzz_field", field, wrongDigit);
                assertThat(incorrect.pass())
                        .as("wrong check digit %s must fail for field %s with actual %s",
                                wrongDigit, field, checkDigit)
                        .isFalse();
            }
        }
    }

    @Test
    void fuzzesTd3DocumentCheckDigitsWithoutFalsePassesOrFalseFails() {
        fuzzDocuments(MrzFormat.TD3, this::randomValidTd3, List.of(
                new CheckPosition(1, 9, "document_number"),
                new CheckPosition(1, 19, "date_of_birth"),
                new CheckPosition(1, 27, "date_of_expiry"),
                new CheckPosition(1, 42, "optional_data"),
                new CheckPosition(1, 43, "composite")));
    }

    @Test
    void fuzzesTd2DocumentCheckDigitsWithoutFalsePassesOrFalseFails() {
        fuzzDocuments(MrzFormat.TD2, this::randomValidTd2, List.of(
                new CheckPosition(1, 9, "document_number"),
                new CheckPosition(1, 19, "date_of_birth"),
                new CheckPosition(1, 27, "date_of_expiry"),
                new CheckPosition(1, 35, "composite")));
    }

    @Test
    void fuzzesTd1DocumentCheckDigitsWithoutFalsePassesOrFalseFails() {
        fuzzDocuments(MrzFormat.TD1, this::randomValidTd1, List.of(
                new CheckPosition(0, 14, "document_number"),
                new CheckPosition(1, 6, "date_of_birth"),
                new CheckPosition(1, 14, "date_of_expiry"),
                new CheckPosition(1, 29, "composite")));
    }

    private void fuzzDocuments(
            MrzFormat format,
            Function<Integer, List<String>> documentFactory,
            List<CheckPosition> checkPositions) {
        for (int caseIndex = 0; caseIndex < DOCUMENT_CASES_PER_FORMAT; caseIndex++) {
            List<String> validLines = documentFactory.apply(caseIndex);
            assertThat(validator.validateAllCheckDigits(format, validLines))
                    .as("%s valid fuzz document %s", format, caseIndex)
                    .allSatisfy(result -> assertThat(result.pass())
                            .as("%s should pass", result.fieldName())
                            .isTrue());

            for (CheckPosition position : checkPositions) {
                char actualDigit = validLines.get(position.lineIndex()).charAt(position.charIndex());
                for (char wrongDigit = '0'; wrongDigit <= '9'; wrongDigit++) {
                    if (wrongDigit == actualDigit) {
                        continue;
                    }

                    List<String> corruptedLines = replaceChar(validLines, position, wrongDigit);
                    assertThat(validator.validateAllCheckDigits(format, corruptedLines))
                            .as("%s document %s must reject %s check digit changed from %s to %s",
                                    format, caseIndex, position.fieldName(), actualDigit, wrongDigit)
                            .anySatisfy(result -> {
                                assertThat(result.fieldName()).isEqualTo(position.fieldName());
                                assertThat(result.pass()).isFalse();
                            });
                }
            }
        }
    }

    private List<String> randomValidTd3(int caseIndex) {
        String line1 = randomLine(44);
        StringBuilder line2 = new StringBuilder(randomLine(44));
        set(line2, 0, randomField(9));
        set(line2, 13, randomDateLikeField());
        set(line2, 21, randomDateLikeField());
        set(line2, 28, randomField(14));
        line2.setCharAt(9, digitChar(validator.computeCheckDigit(line2.substring(0, 9))));
        line2.setCharAt(19, digitChar(validator.computeCheckDigit(line2.substring(13, 19))));
        line2.setCharAt(27, digitChar(validator.computeCheckDigit(line2.substring(21, 27))));
        line2.setCharAt(42, digitChar(validator.computeCheckDigit(line2.substring(28, 42))));
        line2.setCharAt(43, digitChar(validator.computeCheckDigit(
                line2.substring(0, 10) + line2.substring(13, 20) + line2.substring(21, 43))));
        return List.of(line1, line2.toString());
    }

    private List<String> randomValidTd2(int caseIndex) {
        String line1 = randomLine(36);
        StringBuilder line2 = new StringBuilder(randomLine(36));
        set(line2, 0, randomField(9));
        set(line2, 13, randomDateLikeField());
        set(line2, 21, randomDateLikeField());
        line2.setCharAt(9, digitChar(validator.computeCheckDigit(line2.substring(0, 9))));
        line2.setCharAt(19, digitChar(validator.computeCheckDigit(line2.substring(13, 19))));
        line2.setCharAt(27, digitChar(validator.computeCheckDigit(line2.substring(21, 27))));
        line2.setCharAt(35, digitChar(validator.computeCheckDigit(
                line2.substring(0, 10) + line2.substring(13, 20) + line2.substring(21, 35))));
        return List.of(line1, line2.toString());
    }

    private List<String> randomValidTd1(int caseIndex) {
        StringBuilder line1 = new StringBuilder(randomLine(30));
        StringBuilder line2 = new StringBuilder(randomLine(30));
        String line3 = randomLine(30);
        set(line1, 5, randomField(9));
        set(line2, 0, randomDateLikeField());
        set(line2, 8, randomDateLikeField());
        line1.setCharAt(14, digitChar(validator.computeCheckDigit(line1.substring(5, 14))));
        line2.setCharAt(6, digitChar(validator.computeCheckDigit(line2.substring(0, 6))));
        line2.setCharAt(14, digitChar(validator.computeCheckDigit(line2.substring(8, 14))));
        line2.setCharAt(29, digitChar(validator.computeCheckDigit(
                line1.substring(5, 30) + line2.substring(0, 7) + line2.substring(8, 15)
                        + line2.substring(18, 29))));
        return List.of(line1.toString(), line2.toString(), line3);
    }

    private List<String> replaceChar(List<String> lines, CheckPosition position, char value) {
        List<String> copy = new ArrayList<>(lines);
        StringBuilder line = new StringBuilder(copy.get(position.lineIndex()));
        line.setCharAt(position.charIndex(), value);
        copy.set(position.lineIndex(), line.toString());
        return List.copyOf(copy);
    }

    private String randomLine(int length) {
        return randomField(length);
    }

    private String randomDateLikeField() {
        StringBuilder builder = new StringBuilder(6);
        for (int index = 0; index < 6; index++) {
            builder.append(digitChar(random.nextInt(10)));
        }
        return builder.toString();
    }

    private String randomField(int length) {
        StringBuilder builder = new StringBuilder(length);
        for (int index = 0; index < length; index++) {
            builder.append(MRZ_CHARS.charAt(random.nextInt(MRZ_CHARS.length())));
        }
        return builder.toString();
    }

    private void set(StringBuilder builder, int offset, String value) {
        builder.replace(offset, offset + value.length(), value);
    }

    private char digitChar(int digit) {
        return (char) ('0' + digit);
    }

    private record CheckPosition(int lineIndex, int charIndex, String fieldName) {
    }
}
