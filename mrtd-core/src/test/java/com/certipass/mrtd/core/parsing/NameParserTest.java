package com.certipass.mrtd.core.parsing;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class NameParserTest {
    private final NameParser parser = new NameParser();

    @Test
    void splitsSurnameAndGivenNames() {
        ParsedName name = parser.parse("ERIKSSON<<ANNA<MARIA<<<<<<<<<<");

        assertThat(name.surname()).isEqualTo("ERIKSSON");
        assertThat(name.givenNames()).isEqualTo("ANNA MARIA");
    }

    @Test
    void handlesSurnameOnly() {
        ParsedName name = parser.parse("ERIKSSON<<<<<<<<<<<<<<<<<<<<<<");

        assertThat(name.surname()).isEqualTo("ERIKSSON");
        assertThat(name.givenNames()).isEmpty();
    }

    @Test
    void correctsBoundedOcrConfusionsInParsedNameTokens() {
        ParsedName name = parser.parse("ERLKSSON<<ANNA<MARLA<<<<<<<<<<");

        assertThat(name.surname()).isEqualTo("ERIKSSON");
        assertThat(name.givenNames()).isEqualTo("ANNA MARIA");
    }

    @Test
    void correctsNameOnlyDigitAndExtraStrokeConfusions() {
        ParsedName name = parser.parse("OKONKHWO<<CHUKHWUEMEKA<J0HN<<<");

        assertThat(name.surname()).isEqualTo("OKONKWO");
        assertThat(name.givenNames()).isEqualTo("CHUKWUEMEKA JOHN");
    }

    @Test
    void correctsRemainingBenchmarkNameNoise() {
        ParsedName name = parser.parse("2KONKWO<<CHUKVUEMEKA<JOHRN<<<");

        assertThat(name.surname()).isEqualTo("OKONKWO");
        assertThat(name.givenNames()).isEqualTo("CHUKWUEMEKA JOHN");
    }

    @Test
    void correctsLeadingNoiseAndJoinedGivenNameTokens() {
        ParsedName name = parser.parse("SMTTH<<WJANE<ALTICE<EMMASROSE<<<");

        assertThat(name.surname()).isEqualTo("SMITH");
        assertThat(name.givenNames()).isEqualTo("JANE ALICE EMMA ROSE");
    }

    @Test
    void correctsBenchmarkMariaAndMartinConfusions() {
        ParsedName name = parser.parse("MARTTN<<ANNA<MARTA<<<");

        assertThat(name.surname()).isEqualTo("MARTIN");
        assertThat(name.givenNames()).isEqualTo("ANNA MARIA");
    }

    @Test
    void correctsDoeRoundLetterConfusion() {
        ParsedName name = parser.parse("DDE<<JANE<ALICE<<<");

        assertThat(name.surname()).isEqualTo("DOE");
        assertThat(name.givenNames()).isEqualTo("JANE ALICE");
    }
}
