package com.certipass.mrtd.core.orchestration;

import static org.assertj.core.api.Assertions.assertThat;

import com.certipass.mrtd.core.checksum.CheckDigitValidator;
import com.certipass.mrtd.core.detection.MrzFormat;
import com.certipass.mrtd.core.fieldlogic.FieldLogicChecker;
import com.certipass.mrtd.core.fixtures.MrzTestFixtures;
import com.certipass.mrtd.core.model.IdentityData;
import com.certipass.mrtd.core.model.MrzSuccessResult;
import com.certipass.mrtd.core.model.ValidationSeverity;
import com.certipass.mrtd.core.model.Verdict;
import com.certipass.mrtd.core.ocr.OcrBoundingBox;
import com.certipass.mrtd.core.ocr.OcrResult;
import com.certipass.mrtd.core.parsing.Td3MrzParser;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.Test;

class ResultBuilderTest {
    @Test
    void lowConfidenceProducesValidWithWarnings() {
        List<String> lines = MrzTestFixtures.VALID_TD3.get(1);
        PipelineContext context = context(lines, 0.85);

        MrzSuccessResult result = new ResultBuilder(0.90).build(context);

        assertThat(result.verdict()).isEqualTo(Verdict.VALID_WITH_WARNINGS);
        assertThat(result.validations()).anySatisfy(validation -> {
            assertThat(validation.checkName()).isEqualTo("ocr_confidence_threshold");
            assertThat(validation.severity()).isEqualTo(ValidationSeverity.WARNING);
        });
    }

    @Test
    void expiredDocumentProducesValidWithWarningsWhenOtherChecksPass() {
        List<String> lines = MrzTestFixtures.VALID_TD3.getFirst();
        PipelineContext context = context(lines, 0.97);

        MrzSuccessResult result = new ResultBuilder(0.90).build(context);

        assertThat(result.verdict()).isEqualTo(Verdict.VALID_WITH_WARNINGS);
        assertThat(result.identityData().expired()).isTrue();
    }

    private PipelineContext context(List<String> lines, double confidence) {
        PipelineContext context = new PipelineContext(new byte[] {1, 2, 3});
        IdentityData identityData = new Td3MrzParser().parse(lines);
        FieldLogicChecker fieldLogicChecker = new FieldLogicChecker(
                Clock.fixed(Instant.parse("2026-05-21T00:00:00Z"), ZoneOffset.UTC),
                new com.certipass.mrtd.core.fieldlogic.StateCodeRegistry());

        context.ocrResult(new OcrResult(lines, confidence, 0.95, new OcrBoundingBox(0, 0, 10, 10), List.of()));
        context.mrzFormat(MrzFormat.TD3);
        context.identityData(identityData);
        context.checkDigitResults(new CheckDigitValidator().validateAllCheckDigits(MrzFormat.TD3, lines));
        context.fieldLogicResults(fieldLogicChecker.check(identityData));
        context.processingTimeMs(12);
        return context;
    }
}
