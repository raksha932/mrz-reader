package com.certipass.mrtd.core.detection;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

class MrzLineNormalizerTest {
    private final MrzLineNormalizer normalizer = new MrzLineNormalizer();

    @Test
    void trimsSmallTd3TrailingOcrNoise() {
        List<String> normalized = normalizer.normalize(List.of(
                "P<GBRSMITH<<JOHN<JAMES<<<<<<<<<<<<<<<<<<<<E<<<EC",
                "1234567897GBR8001014M3001019<<<<<<<<<<<<<<08"));

        assertThat(normalized).containsExactly(
                padded("P<GBRSMITH<<JOHN<JAMES", 44),
                "1234567897GBR8001014M3001019<<<<<<<<<<<<<<08");
    }

    @Test
    void padsSmallTd3MissingFillerRun() {
        List<String> normalized = normalizer.normalize(List.of(
                "P<UTOERIKSSON<<ANNA<MARIA<<<<<<<<<<<<<<<",
                "L898902C36UTO7408122F1204159ZE184226B<<<10"));

        assertThat(normalized).containsExactly(
                padded("P<UTOERIKSSON<<ANNA<MARIA", 44),
                "L898902C36UTO7408122F1204159ZE184226B<<<10<<");
    }

    @Test
    void leavesFarFromKnownShapesUnchanged() {
        List<String> lines = List.of("P<UTOERIKSSON", "L898902");

        assertThat(normalizer.normalize(lines)).isEqualTo(lines);
    }

    @Test
    void selectsTd3WindowWhenOcrReturnsLeadingFragments() {
        List<String> normalized = normalizer.normalize(List.of(
                "SA",
                "P<UTOERIKSSON<<ANNA<MARIA<<<<<<<<<<<<<<<<<<<",
                "L898902C36UTO7408122F1204159ZE184226B<<<<<10"));

        assertThat(normalized).containsExactly(
                "P<UTOERIKSSON<<ANNA<MARIA<<<<<<<<<<<<<<<<<<<",
                "L898902C36UTO7408122F1204159ZE184226B<<<<<10");
    }

    @Test
    void selectsTd3WindowWhenOcrReturnsTrailingFragments() {
        List<String> normalized = normalizer.normalize(List.of(
                "P<NGAOKONKWO<<CHUKWUEMEKA<JOHN<<<<<<<<<<<<<<",
                "A123456784NGA9008157M2908144<<<<<<<<<<<<<<06",
                "1"));

        assertThat(normalized).containsExactly(
                "P<NGAOKONKWO<<CHUKWUEMEKA<JOHN<<<<<<<<<<<<<<",
                "A123456784NGA9008157M2908144<<<<<<<<<<<<<<06");
    }

    @Test
    void selectsTd1WindowWhenOcrReturnsLeadingFragments() {
        List<String> normalized = normalizer.normalize(List.of(
                "1",
                "I<UTOD231458907<<<<<<<<<<<<<<<",
                "7408122F1204159UTO<<<<<<<<<<<6",
                "ERIKSSON<<ANNA<MARIA<<<<<<<<<<"));

        assertThat(normalized).containsExactly(
                "I<UTOD231458907<<<<<<<<<<<<<<<",
                "7408122F1204159UTO<<<<<<<<<<<6",
                "ERIKSSON<<ANNA<MARIA<<<<<<<<<<");
    }

    @Test
    void preservesUnsupportedVisaWindowWithExtraFragments() {
        List<String> normalized = normalizer.normalize(List.of(
                "NOISE",
                "V<UTOTRAVELER<<TEST<ALPHA<<<<<<<<<<<<<<<<<<",
                "V123456785UTO8001014F3001019<<<<<<<<<<<<<<06"));

        assertThat(normalized).containsExactly(
                padded("V<UTOTRAVELER<<TEST<ALPHA", 44),
                "V123456785UTO8001014F3001019<<<<<<<<<<<<<<06");
    }

    @Test
    void recoversMissingTd3DocumentPrefixWhenDataLineIsStrong() {
        List<String> normalized = normalizer.normalize(List.of(
                "U7OERIKSSON<<ANNASMARIA<<<<<SS<SLEECCETTO",
                "1898902C36UT07408122F1204159ZE184226B<<<<<10"));

        assertThat(normalized).containsExactly(
                padded("P<U7OERIKSSON<<ANNASMARIA", 44),
                "1898902C36UT07408122F1204159ZE184226B<<<<<10");
    }

    @Test
    void recoversMisreadTd3DocumentCode() {
        List<String> normalized = normalizer.normalize(List.of(
                "F<UTODERIKSSON<<ANNA<MARIA<<<<<<<<<<<<<<<<<<<<",
                "LB898S02C368UTO7408122F1204159ZR1B4226E<<<<<10"));

        assertThat(normalized.getFirst())
                .startsWith("P<UTODERIKSSON<<ANNA<MARIA")
                .hasSize(44);
        assertThat(normalized.get(1)).hasSize(44);
    }

    @Test
    void recoversMisreadVisaDocumentCodeAsUnsupportedVisaShape() {
        List<String> normalized = normalizer.normalize(List.of(
                "Y<UTUTRAVELER<<IE3T<ALFHAS<<<<<<<<<<<<<<<<<",
                "V1234567850UT08001014F300L019<<<<<<<<<<<<<<06"));

        assertThat(normalized.getFirst())
                .startsWith("V<UTUTRAVELER")
                .hasSize(44);
    }

    @Test
    void doesNotInsertFillerIntoValidTwoCharacterPassportCode() {
        List<String> normalized = normalizer.normalize(List.of(
                "PPGBRUK<SPECIMEN<<ANGELA<ZOE<<<<<<<<<<<<<<<<",
                "9993137658GBR9501016F3511303<<<<<<<<<<<<<<00"));

        assertThat(normalized).containsExactly(
                "PPGBRUK<SPECIMEN<<ANGELA<ZOE<<<<<<<<<<<<<<<<",
                "9993137658GBR9501016F3511303<<<<<<<<<<<<<<00");
    }

    @Test
    void recoversMisreadTwoCharacterPassportCodeWhenStateMatchesDataLine() {
        List<String> normalized = normalizer.normalize(List.of(
                "FFLIESFECIMENSSOUFNIASSSSSSSS<SS<S<KS<S<S8<<<<",
                "PP00000033LIE9001011F3602025<<<<<<<<<<<<<<<2"));

        assertThat(normalized.getFirst())
                .startsWith("PPLIE")
                .hasSize(44);
        assertThat(normalized.get(1)).isEqualTo("PP00000033LIE9001011F3602025<<<<<<<<<<<<<<<2");
    }

    private String padded(String value, int targetLength) {
        return value + "<".repeat(targetLength - value.length());
    }
}
