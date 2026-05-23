package com.certipass.mrtd.core.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class ConfidenceScoreTest {
    @Test
    void acceptsInclusiveRange() {
        assertThat(new ConfidenceScore(0.0).value()).isZero();
        assertThat(new ConfidenceScore(1.0).value()).isEqualTo(1.0);
    }

    @Test
    void rejectsValuesOutsideRange() {
        assertThatThrownBy(() -> new ConfidenceScore(-0.01))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new ConfidenceScore(1.01))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
