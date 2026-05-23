package com.certipass.mrtd.core.image;

import static org.assertj.core.api.Assertions.assertThat;

import com.certipass.mrtd.core.fixtures.ImageFixtures;
import com.certipass.mrtd.core.model.ErrorCode;
import org.junit.jupiter.api.Test;

class ImageValidatorTest {
    private final ImageValidator validator = new ImageValidator();

    @Test
    void acceptsPngImage() {
        ImageValidationResult result = validator.validate(ImageFixtures.png1x1());

        assertThat(result).isInstanceOf(ImageValidationResult.Valid.class);
        ImageValidationResult.Valid valid = (ImageValidationResult.Valid) result;
        assertThat(valid.metadata().format()).isEqualTo("PNG");
        assertThat(valid.metadata().width()).isEqualTo(1);
        assertThat(valid.metadata().height()).isEqualTo(1);
    }

    @Test
    void rejectsEmptyImage() {
        ImageValidationResult result = validator.validate(new byte[0]);

        assertThat(result).isInstanceOf(ImageValidationResult.Invalid.class);
        assertThat(((ImageValidationResult.Invalid) result).errorCode()).isEqualTo(ErrorCode.INVALID_IMAGE);
    }
}
