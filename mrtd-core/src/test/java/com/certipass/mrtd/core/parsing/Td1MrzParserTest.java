package com.certipass.mrtd.core.parsing;

import static org.assertj.core.api.Assertions.assertThat;

import com.certipass.mrtd.core.fixtures.MrzTestFixtures;
import com.certipass.mrtd.core.model.DocumentType;
import com.certipass.mrtd.core.model.IdentityData;
import org.junit.jupiter.api.Test;

class Td1MrzParserTest {
    private final Td1MrzParser parser = new Td1MrzParser();

    @Test
    void parsesTd1Document() {
        IdentityData identity = parser.parse(MrzTestFixtures.VALID_TD1.getFirst());

        assertThat(identity.documentType()).isEqualTo(DocumentType.ID_CARD);
        assertThat(identity.documentCode()).isEqualTo("I");
        assertThat(identity.issuingState()).isEqualTo("UTO");
        assertThat(identity.surname()).isEqualTo("ERIKSSON");
        assertThat(identity.givenNames()).isEqualTo("ANNA MARIA");
        assertThat(identity.documentNumber()).isEqualTo("D23145890");
        assertThat(identity.nationality()).isEqualTo("UTO");
    }
}
