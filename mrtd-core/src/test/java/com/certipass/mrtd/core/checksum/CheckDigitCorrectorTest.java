package com.certipass.mrtd.core.checksum;

import static org.assertj.core.api.Assertions.assertThat;

import com.certipass.mrtd.core.detection.MrzFormat;
import com.certipass.mrtd.core.fixtures.MrzTestFixtures;
import java.util.List;
import org.junit.jupiter.api.Test;

class CheckDigitCorrectorTest {
    private final CheckDigitCorrector corrector = new CheckDigitCorrector();
    private final CheckDigitValidator validator = new CheckDigitValidator();

    @Test
    void correctsAmbiguousDocumentNumberUsingCheckDigit() {
        List<String> lines = List.of(
                MrzTestFixtures.VALID_TD3.getFirst().get(0),
                "L8989O2C36UTO7408122F1204159ZE184226B<<<<<10");

        List<String> corrected = corrector.correct(MrzFormat.TD3, lines);

        assertThat(corrected).containsExactlyElementsOf(MrzTestFixtures.VALID_TD3.getFirst());
        assertThat(validator.validateAllCheckDigits(MrzFormat.TD3, corrected))
                .allSatisfy(result -> assertThat(result.pass()).as(result.fieldName()).isTrue());
    }

    @Test
    void correctsLetterConfusionInDateFieldUsingCheckDigit() {
        List<String> lines = List.of(
                MrzTestFixtures.VALID_TD2.getFirst().get(0),
                "D0012345<0UTO74O8122F1204159<<<<<<<0");

        List<String> corrected = corrector.correct(MrzFormat.TD2, lines);

        assertThat(corrected).containsExactlyElementsOf(MrzTestFixtures.VALID_TD2.getFirst());
    }

    @Test
    void correctsMisreadCheckDigitWhenFieldAlreadyMatches() {
        List<String> lines = List.of(
                MrzTestFixtures.VALID_TD1.getFirst().get(0),
                "740812ZF1204159UTO<<<<<<<<<<<6",
                MrzTestFixtures.VALID_TD1.getFirst().get(2));

        List<String> corrected = corrector.correct(MrzFormat.TD1, lines);

        assertThat(corrected).containsExactlyElementsOf(MrzTestFixtures.VALID_TD1.getFirst());
    }

    @Test
    void correctsTd3OptionalDataUsingOptionalAndCompositeCheckDigits() {
        List<String> lines = List.of(
                "P<LVASILINS<<OLEGS<<<<<<<<<<<<<<<<<<<<<<<<<<",
                "LV34819870LVA0304270M2806011270403<28740<<28");

        List<String> corrected = corrector.correct(MrzFormat.TD3, lines);

        assertThat(corrected).containsExactly(
                "P<LVASILINS<<OLEGS<<<<<<<<<<<<<<<<<<<<<<<<<<",
                "LV34819870LVA0304270M2806011270403<28710<<28");
        assertThat(validator.validateAllCheckDigits(MrzFormat.TD3, corrected))
                .allSatisfy(result -> assertThat(result.pass()).as(result.fieldName()).isTrue());
    }

}
