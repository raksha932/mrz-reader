package com.certipass.mrtd.core.orchestration;

import static org.assertj.core.api.Assertions.assertThat;

import com.certipass.mrtd.core.fixtures.ImageFixtures;
import com.certipass.mrtd.core.fixtures.MrzTestFixtures;
import com.certipass.mrtd.core.model.MrzErrorResult;
import com.certipass.mrtd.core.model.MrzReadResult;
import com.certipass.mrtd.core.model.MrzSuccessResult;
import com.certipass.mrtd.core.model.Verdict;
import com.certipass.mrtd.core.ocr.OcrBoundingBox;
import com.certipass.mrtd.core.ocr.OcrClient;
import com.certipass.mrtd.core.ocr.OcrExtractionResult;
import com.certipass.mrtd.core.ocr.OcrResult;
import java.util.List;
import org.junit.jupiter.api.Test;

class MrzReaderOrchestratorTest {
    @Test
    void runsEndToEndWithMockOcr() {
        OcrClient ocrClient = imageData -> new OcrExtractionResult.Success(new OcrResult(
                MrzTestFixtures.VALID_TD3.get(1),
                0.97,
                0.95,
                new OcrBoundingBox(0, 0, 10, 10),
                List.of("test_fixture")));

        MrzReadResult result = new MrzReaderOrchestrator(ocrClient).readMrz(ImageFixtures.png1x1());

        assertThat(result).isInstanceOf(MrzSuccessResult.class);
        MrzSuccessResult success = (MrzSuccessResult) result;
        assertThat(success.identityData().surname()).isEqualTo("OKONKWO");
        assertThat(success.verdict()).isEqualTo(Verdict.VALID);
    }

    @Test
    void failsFastOnInvalidImage() {
        OcrClient ocrClient = imageData -> {
            throw new AssertionError("OCR must not be called for invalid images");
        };

        MrzReadResult result = new MrzReaderOrchestrator(ocrClient).readMrz(new byte[0]);

        assertThat(result).isInstanceOf(MrzErrorResult.class);
    }

    @Test
    void exposesOcrLinesWhenFailureOccursAfterOcrExtraction() {
        List<String> incompleteLines = List.of("P<UTOERIKSSON", "L898902C36UTO");
        OcrClient ocrClient = imageData -> new OcrExtractionResult.Success(new OcrResult(
                incompleteLines,
                0.72,
                0.80,
                new OcrBoundingBox(0, 0, 10, 10),
                List.of("test_fixture")));

        MrzReadResult result = new MrzReaderOrchestrator(ocrClient).readMrz(ImageFixtures.png1x1());

        assertThat(result).isInstanceOf(MrzErrorResult.class);
        MrzErrorResult error = (MrzErrorResult) result;
        assertThat(error.ocrLines()).containsExactlyElementsOf(incompleteLines);
    }
}
