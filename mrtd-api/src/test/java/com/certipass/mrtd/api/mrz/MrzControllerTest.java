package com.certipass.mrtd.api.mrz;

import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.certipass.mrtd.api.error.ErrorResponseMapper;
import com.certipass.mrtd.api.error.GlobalExceptionHandler;
import com.certipass.mrtd.api.web.RequestIdProvider;
import com.certipass.mrtd.core.model.ErrorCode;
import com.certipass.mrtd.core.ocr.OcrExtractionResult;
import com.certipass.mrtd.core.orchestration.MrzReaderOrchestrator;
import java.util.Base64;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class MrzControllerTest {
    private static final String PNG_1X1_BASE64 =
            "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+/p9sAAAAASUVORK5CYII=";

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        MrzController controller = new MrzController(
                new MrzReaderOrchestrator(imageData -> new OcrExtractionResult.Failure(
                        ErrorCode.MRZ_NOT_DETECTED,
                        "Document found but MRZ zone could not be isolated")),
                new MrzResponseMapper(),
                new ErrorResponseMapper(),
                new RequestIdProvider());

        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler(new RequestIdProvider()))
                .build();
    }

    @Test
    void mapsFatalCoreErrorToHttpStatus() throws Exception {
        mockMvc.perform(multipart("/api/v1/mrz/read")
                        .file(pngFile()))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.error.code", is("MRZ_NOT_DETECTED")))
                .andExpect(jsonPath("$.error.message", is("Document found but MRZ zone could not be isolated")));
    }

    @Test
    void rejectsEmptyUpload() throws Exception {
        mockMvc.perform(multipart("/api/v1/mrz/read")
                        .file(new MockMultipartFile("file", "empty.png", "image/png", new byte[0])))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code", is("INVALID_IMAGE")))
                .andExpect(jsonPath("$.error.message", is("Uploaded file is required and must not be empty.")));
    }

    @Test
    void rejectsUnsupportedContentType() throws Exception {
        mockMvc.perform(multipart("/api/v1/mrz/read")
                        .file(new MockMultipartFile("file", "document.txt", "text/plain", "hello".getBytes())))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code", is("INVALID_IMAGE")))
                .andExpect(jsonPath("$.error.message", is(
                        "Unsupported file content type. Only image/jpeg and image/png are accepted.")));
    }

    @Test
    void rejectsMissingFilePart() throws Exception {
        mockMvc.perform(multipart("/api/v1/mrz/read"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code", is("INVALID_IMAGE")))
                .andExpect(jsonPath("$.error.message", is(
                        "A multipart form-data request with a file part named 'file' is required.")));
    }

    private MockMultipartFile pngFile() {
        return new MockMultipartFile(
                "file",
                "document.png",
                "image/png",
                Base64.getDecoder().decode(PNG_1X1_BASE64));
    }
}
