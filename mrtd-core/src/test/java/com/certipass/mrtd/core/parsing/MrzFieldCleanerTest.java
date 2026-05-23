package com.certipass.mrtd.core.parsing;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class MrzFieldCleanerTest {
    @Test
    void normalizesStateCodeOcrConfusions() {
        assertThat(MrzFieldCleaner.stateCode("UT0")).isEqualTo("UTO");
        assertThat(MrzFieldCleaner.stateCode("U5A")).isEqualTo("USA");
        assertThat(MrzFieldCleaner.stateCode("GB8")).isEqualTo("GBB");
        assertThat(MrzFieldCleaner.stateCode("UN1")).isEqualTo("UNI");
    }

    @Test
    void preservesLegacyGermanDCodeSpecialCase() {
        assertThat(MrzFieldCleaner.stateCode("D<<")).isEqualTo("DEU");
    }

    @Test
    void correctsBoundedStateCodeOcrConfusionsAgainstAcceptedCodes() {
        assertThat(MrzFieldCleaner.stateCode("N<A")).isEqualTo("NGA");
        assertThat(MrzFieldCleaner.stateCode("UBA")).isEqualTo("USA");
        assertThat(MrzFieldCleaner.stateCode("U7O")).isEqualTo("UTO");
        assertThat(MrzFieldCleaner.stateCode("DTO")).isEqualTo("UTO");
        assertThat(MrzFieldCleaner.stateCode("5TO")).isEqualTo("UTO");
        assertThat(MrzFieldCleaner.stateCode("6BR")).isEqualTo("GBR");
    }
}
