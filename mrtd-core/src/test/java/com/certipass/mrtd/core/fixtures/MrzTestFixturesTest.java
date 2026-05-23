package com.certipass.mrtd.core.fixtures;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class MrzTestFixturesTest {
    @Test
    void td3FixturesHaveExpectedShape() {
        MrzTestFixtures.VALID_TD3.forEach(lines -> {
            assertThat(lines).hasSize(2);
            assertThat(lines).allSatisfy(line -> assertThat(line).hasSize(44));
        });
    }

    @Test
    void td2FixturesHaveExpectedShape() {
        MrzTestFixtures.VALID_TD2.forEach(lines -> {
            assertThat(lines).hasSize(2);
            assertThat(lines).allSatisfy(line -> assertThat(line).hasSize(36));
        });
    }

    @Test
    void td1FixturesHaveExpectedShape() {
        MrzTestFixtures.VALID_TD1.forEach(lines -> {
            assertThat(lines).hasSize(3);
            assertThat(lines).allSatisfy(line -> assertThat(line).hasSize(30));
        });
    }
}
