package com.certipass.mrtd.core.parsing;

import static org.assertj.core.api.Assertions.assertThat;

import com.certipass.mrtd.core.fixtures.MrzTestFixtures;
import com.certipass.mrtd.core.model.DocumentType;
import com.certipass.mrtd.core.model.IdentityData;
import org.junit.jupiter.api.Test;

class Td2MrzParserTest {
    private final Td2MrzParser parser = new Td2MrzParser();

    @Test
    void parsesTd2Document() {
        IdentityData identity = parser.parse(MrzTestFixtures.VALID_TD2.getFirst());

        assertThat(identity.documentType()).isEqualTo(DocumentType.OFFICIAL_TRAVEL_DOCUMENT);
        assertThat(identity.documentCode()).isEqualTo("I");
        assertThat(identity.issuingState()).isEqualTo("UTO");
        assertThat(identity.surname()).isEqualTo("ERIKSSON");
        assertThat(identity.givenNames()).isEqualTo("ANNA MARIA");
        assertThat(identity.documentNumber()).isEqualTo("D0012345");
        assertThat(identity.nationality()).isEqualTo("UTO");
    }
}
