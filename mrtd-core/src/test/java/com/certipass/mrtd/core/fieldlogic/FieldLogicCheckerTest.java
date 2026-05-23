package com.certipass.mrtd.core.fieldlogic;

import static org.assertj.core.api.Assertions.assertThat;

import com.certipass.mrtd.core.fixtures.MrzTestFixtures;
import com.certipass.mrtd.core.model.IdentityData;
import com.certipass.mrtd.core.model.ValidationSeverity;
import com.certipass.mrtd.core.parsing.Td3MrzParser;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;

class FieldLogicCheckerTest {
    private final Td3MrzParser parser = new Td3MrzParser();
    private final FieldLogicChecker checker = new FieldLogicChecker(
            Clock.fixed(Instant.parse("2026-05-21T00:00:00Z"), ZoneOffset.UTC),
            new StateCodeRegistry());

    @Test
    void expiredDocumentIsAWarning() {
        IdentityData identity = parser.parse(MrzTestFixtures.VALID_TD3.getFirst());

        assertThat(checker.check(identity))
                .anySatisfy(result -> {
                    assertThat(result.checkName()).isEqualTo("document_not_expired");
                    assertThat(result.pass()).isFalse();
                    assertThat(result.severity()).isEqualTo(ValidationSeverity.WARNING);
                });
    }

    @Test
    void acceptsConfiguredBritishStatusCodes() {
        StateCodeRegistry registry = new StateCodeRegistry();

        assertThat(registry.isAccepted("GBD")).isTrue();
        assertThat(registry.isAccepted("GBN")).isTrue();
        assertThat(registry.isAccepted("GBO")).isTrue();
        assertThat(registry.isAccepted("GBP")).isTrue();
    }
}
