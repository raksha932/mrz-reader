package com.certipass.mrtd.core.checksum;

import static org.assertj.core.api.Assertions.assertThat;

import com.certipass.mrtd.core.detection.MrzFormat;
import com.certipass.mrtd.core.fixtures.MrzTestFixtures;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

class CheckDigitValidatorTest {
    private final CheckDigitValidator validator = new CheckDigitValidator();

    @Test
    void computesIcaoExampleCheckDigit() {
        assertThat(validator.computeCheckDigit("L898902C3")).isEqualTo(6);
    }

    @Test
    void validatesAllTd3FixtureCheckDigits() {
        assertAllPass(MrzFormat.TD3, MrzTestFixtures.VALID_TD3);
    }

    @Test
    void validatesAllTd2FixtureCheckDigits() {
        assertAllPass(MrzFormat.TD2, MrzTestFixtures.VALID_TD2);
    }

    @Test
    void validatesAllTd1FixtureCheckDigits() {
        assertAllPass(MrzFormat.TD1, MrzTestFixtures.VALID_TD1);
    }

    @Test
    void detectsFailedCheckDigit() {
        CheckDigitResult result = validator.validateCheckDigit("document_number", "L898902C3", '7');

        assertThat(result.pass()).isFalse();
        assertThat(result.expectedDigit()).isEqualTo(7);
        assertThat(result.actualDigit()).isEqualTo(6);
    }

    @Test
    void treatsNonNumericCheckDigitAsValidationFailure() {
        CheckDigitResult result = validator.validateCheckDigit("document_number", "L898902C3", '<');

        assertThat(result.pass()).isFalse();
        assertThat(result.expectedDigit()).isEqualTo(-1);
        assertThat(result.actualDigit()).isEqualTo(6);
    }

    private void assertAllPass(MrzFormat format, List<List<String>> fixtures) {
        Stream<CheckDigitResult> results = fixtures.stream()
                .flatMap(lines -> validator.validateAllCheckDigits(format, lines).stream());

        assertThat(results).allSatisfy(result -> assertThat(result.pass())
                .as(result.fieldName())
                .isTrue());
    }
}
