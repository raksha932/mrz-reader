package com.certipass.mrtd.core.detection;

import static org.assertj.core.api.Assertions.assertThat;

import com.certipass.mrtd.core.fixtures.MrzTestFixtures;
import com.certipass.mrtd.core.model.ErrorCode;
import java.util.List;
import org.junit.jupiter.api.Test;

class MrzFormatDetectorTest {
    private final MrzFormatDetector detector = new MrzFormatDetector();

    @Test
    void detectsTd3() {
        assertDetected(MrzTestFixtures.VALID_TD3.getFirst(), MrzFormat.TD3);
    }

    @Test
    void detectsTd2() {
        assertDetected(MrzTestFixtures.VALID_TD2.getFirst(), MrzFormat.TD2);
    }

    @Test
    void detectsTd1() {
        assertDetected(MrzTestFixtures.VALID_TD1.getFirst(), MrzFormat.TD1);
    }

    @Test
    void rejectsVisaFormatsAsUnsupported() {
        MrzFormatDetectionResult result = detector.detect(List.of(
                "V<UTOERIKSSON<<ANNA<MARIA<<<<<<<<<<<<<<<<<<<",
                "L898902C36UTO7408122F1204159ZE184226B<<<<<10"));

        assertThat(result).isInstanceOf(MrzFormatDetectionResult.Failure.class);
        assertThat(((MrzFormatDetectionResult.Failure) result).errorCode())
                .isEqualTo(ErrorCode.UNSUPPORTED_FORMAT);
    }

    @Test
    void returnsIncompleteForInvalidShape() {
        MrzFormatDetectionResult result = detector.detect(List.of("P<UTOERIKSSON"));

        assertThat(result).isInstanceOf(MrzFormatDetectionResult.Failure.class);
        assertThat(((MrzFormatDetectionResult.Failure) result).errorCode())
                .isEqualTo(ErrorCode.MRZ_INCOMPLETE);
    }

    @Test
    void rejectsTd2LengthNoiseWithInvalidIssuingStateSlot() {
        MrzFormatDetectionResult result = detector.detect(List.of(
                "IS<<AIVIS<<<<<<<<<<<<<<<<<<<<<<<<<<<",
                "AWJAAMONMNCAZAMAMNACAZAAZACNAOA7ZAFE"));

        assertThat(result).isInstanceOf(MrzFormatDetectionResult.Failure.class);
        assertThat(((MrzFormatDetectionResult.Failure) result).errorCode())
                .isEqualTo(ErrorCode.MRZ_INCOMPLETE);
    }

    private void assertDetected(List<String> lines, MrzFormat expectedFormat) {
        MrzFormatDetectionResult result = detector.detect(lines);

        assertThat(result).isInstanceOf(MrzFormatDetectionResult.Detected.class);
        assertThat(((MrzFormatDetectionResult.Detected) result).format()).isEqualTo(expectedFormat);
    }
}
