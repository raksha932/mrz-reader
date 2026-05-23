package com.certipass.mrtd.core.parsing;

import static org.assertj.core.api.Assertions.assertThat;

import com.certipass.mrtd.core.fixtures.MrzTestFixtures;
import com.certipass.mrtd.core.model.DocumentType;
import com.certipass.mrtd.core.model.IdentityData;
import org.junit.jupiter.api.Test;

class Td3MrzParserTest {
    private final Td3MrzParser parser = new Td3MrzParser();

    @Test
    void parsesIcaoPassportExample() {
        IdentityData identity = parser.parse(MrzTestFixtures.VALID_TD3.getFirst());

        assertThat(identity.documentType()).isEqualTo(DocumentType.PASSPORT);
        assertThat(identity.documentCode()).isEqualTo("P");
        assertThat(identity.issuingState()).isEqualTo("UTO");
        assertThat(identity.surname()).isEqualTo("ERIKSSON");
        assertThat(identity.givenNames()).isEqualTo("ANNA MARIA");
        assertThat(identity.documentNumber()).isEqualTo("L898902C3");
        assertThat(identity.nationality()).isEqualTo("UTO");
        assertThat(identity.dateOfBirth().rawValue()).isEqualTo("740812");
        assertThat(identity.dateOfBirth().valid()).isTrue();
        assertThat(identity.sex()).isEqualTo("F");
        assertThat(identity.dateOfExpiry().rawValue()).isEqualTo("120415");
        assertThat(identity.optionalData()).isEqualTo("ZE184226B");
    }
}
