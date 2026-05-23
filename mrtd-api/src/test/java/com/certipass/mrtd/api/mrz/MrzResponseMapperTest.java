package com.certipass.mrtd.api.mrz;

import static org.assertj.core.api.Assertions.assertThat;

import com.certipass.mrtd.core.detection.MrzFormat;
import com.certipass.mrtd.core.model.ConfidenceScore;
import com.certipass.mrtd.core.model.DocumentType;
import com.certipass.mrtd.core.model.IdentityData;
import com.certipass.mrtd.core.model.MrzDate;
import com.certipass.mrtd.core.model.MrzRawData;
import com.certipass.mrtd.core.model.MrzSuccessResult;
import com.certipass.mrtd.core.model.ResultMetadata;
import com.certipass.mrtd.core.model.Verdict;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;

class MrzResponseMapperTest {
    @Test
    void includesRequestIdInSuccessMetadata() {
        MrzSuccessResult result = new MrzSuccessResult(
                Verdict.VALID,
                new IdentityData(
                        DocumentType.PASSPORT,
                        "P",
                        "UTO",
                        "ERIKSSON",
                        "ANNA MARIA",
                        "L898902C3",
                        "UTO",
                        new MrzDate("740812", LocalDate.parse("1974-08-12"), true),
                        "F",
                        new MrzDate("300415", LocalDate.parse("2030-04-15"), true),
                        "",
                        false),
                List.of(),
                new ConfidenceScore(0.97),
                new MrzRawData(MrzFormat.TD3, List.of(
                        "P<UTOERIKSSON<<ANNA<MARIA<<<<<<<<<<<<<<<<<<<",
                        "L898902C36UTO7408122F3004159ZE184226B<<<<<10")),
                new ResultMetadata(
                        1243,
                        0.97,
                        MrzFormat.TD3,
                        "1.0.0",
                        Instant.parse("2026-05-21T14:30:00Z")));

        assertThat(new MrzResponseMapper().toDto(result, "req-success-1").metadata().requestId())
                .isEqualTo("req-success-1");
        assertThat(new MrzResponseMapper().toDto(result, "req-success-1").rawMrz().ocrLines())
                .containsExactlyElementsOf(result.rawMrz().lines());
        assertThat(new MrzResponseMapper().toDto(result, "req-success-1").rawMrz().corrected())
                .isFalse();
    }
}
