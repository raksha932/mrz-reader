package com.certipass.mrtd.core.parsing;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import org.junit.jupiter.api.Test;

class MrzDateParserTest {
    private final MrzDateParser parser = new MrzDateParser();

    @Test
    void parsesBirthDatesWithHistoricalCenturyWindow() {
        assertThat(parser.parseBirthDate("740812").parsed()).contains(LocalDate.of(1974, 8, 12));
        assertThat(parser.parseBirthDate("290101").parsed()).contains(LocalDate.of(2029, 1, 1));
        assertThat(parser.parseBirthDate("300101").parsed()).contains(LocalDate.of(1930, 1, 1));
    }

    @Test
    void parsesExpiryDatesWithFutureCenturyWindow() {
        assertThat(parser.parseExpiryDate("120415").parsed()).contains(LocalDate.of(2012, 4, 15));
        assertThat(parser.parseExpiryDate("320220").parsed()).contains(LocalDate.of(2032, 2, 20));
        assertThat(parser.parseExpiryDate("800101").parsed()).contains(LocalDate.of(1980, 1, 1));
    }
}
