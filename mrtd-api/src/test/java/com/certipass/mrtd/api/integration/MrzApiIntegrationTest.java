package com.certipass.mrtd.api.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.util.TestPropertyValues;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(properties = {
        "api.auth.keys[0].id=success-key",
        "api.auth.keys[0].value=success-secret",
        "api.auth.keys[0].tier=default",
        "api.auth.keys[1].id=fatal-key",
        "api.auth.keys[1].value=fatal-secret",
        "api.auth.keys[1].tier=default",
        "api.auth.keys[2].id=rate-key",
        "api.auth.keys[2].value=rate-secret",
        "api.auth.keys[2].tier=strict",
        "api.auth.keys[3].id=upload-empty-key",
        "api.auth.keys[3].value=upload-empty-secret",
        "api.auth.keys[3].tier=default",
        "api.auth.keys[4].id=upload-type-key",
        "api.auth.keys[4].value=upload-type-secret",
        "api.auth.keys[4].tier=default",
        "api.auth.keys[5].id=sidecar-key",
        "api.auth.keys[5].value=sidecar-secret",
        "api.auth.keys[5].tier=default",
        "api.rate-limit.tiers.default.capacity=100",
        "api.rate-limit.tiers.default.refill-tokens=100",
        "api.rate-limit.tiers.default.refill-period=1m",
        "api.rate-limit.tiers.strict.capacity=1",
        "api.rate-limit.tiers.strict.refill-tokens=1",
        "api.rate-limit.tiers.strict.refill-period=1h",
        "management.endpoint.health.show-components=always"
})
@AutoConfigureMockMvc
@ContextConfiguration(initializers = MrzApiIntegrationTest.MockSidecarProperties.class)
class MrzApiIntegrationTest {
    private static final String PNG_1X1_BASE64 =
            "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+/p9sAAAAASUVORK5CYII=";

    private static final AtomicReference<SidecarResponse> sidecarResponse = new AtomicReference<>();
    private static final AtomicInteger extractRequests = new AtomicInteger();
    private static HttpServer server;

    @Autowired
    private MockMvc mockMvc;

    @BeforeAll
    static void startServer() throws IOException {
        ensureServerStarted();
    }

    @BeforeEach
    void resetSidecar() {
        extractRequests.set(0);
        sidecarResponse.set(SidecarResponse.success());
    }

    @AfterAll
    static void stopServer() {
        if (server != null) {
            server.stop(0);
        }
    }

    @Test
    void readsValidMrzThroughMockedOcrSidecar() throws Exception {
        mockMvc.perform(multipart("/api/v1/mrz/read")
                        .file(pngFile())
                        .header("X-API-Key", "success-secret")
                        .header("X-Request-ID", "req-integration-success"))
                .andExpect(status().isOk())
                .andExpect(header().string("X-Request-ID", "req-integration-success"))
                .andExpect(jsonPath("$.verdict", is("VALID")))
                .andExpect(jsonPath("$.identity_data.surname", is("OKONKWO")))
                .andExpect(jsonPath("$.identity_data.document_number", is("A12345678")))
                .andExpect(jsonPath("$.raw_mrz.format", is("TD3")))
                .andExpect(jsonPath("$.raw_mrz.corrected", is(false)))
                .andExpect(jsonPath("$.raw_mrz.ocr_lines[1]", is("A123456784NGA9008157M2908144<<<<<<<<<<<<<<06")))
                .andExpect(jsonPath("$.metadata.request_id", is("req-integration-success")));

        assertThat(extractRequests).hasValue(1);
    }

    @Test
    void mapsFatalOcrErrorToApiErrorEnvelope() throws Exception {
        sidecarResponse.set(new SidecarResponse(422, """
                {
                  "status": "MRZ_NOT_DETECTED",
                  "message": "Document found but MRZ zone could not be isolated",
                  "image_quality_score": 0.91
                }
                """));

        mockMvc.perform(multipart("/api/v1/mrz/read")
                        .file(pngFile())
                        .header("X-API-Key", "fatal-secret")
                        .header("X-Request-ID", "req-integration-fatal"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.error.code", is("MRZ_NOT_DETECTED")))
                .andExpect(jsonPath("$.error.message", is("Document found but MRZ zone could not be isolated")))
                .andExpect(jsonPath("$.error.request_id", is("req-integration-fatal")));

        assertThat(extractRequests).hasValue(1);
    }

    @Test
    void exposesOcrLinesWhenFormatDetectionFails() throws Exception {
        sidecarResponse.set(new SidecarResponse(200, """
                {
                  "status": "SUCCESS",
                  "mrz_lines": [
                    "P<UTOERIKSSON",
                    "L898902C36UTO"
                  ],
                  "confidence": 0.82,
                  "zone_bbox": { "x": 10, "y": 20, "width": 500, "height": 90 },
                  "image_quality_score": 0.95,
                  "preprocessing_applied": ["grayscale", "threshold"]
                }
                """));

        mockMvc.perform(multipart("/api/v1/mrz/read")
                        .file(pngFile())
                        .header("X-API-Key", "fatal-secret")
                        .header("X-Request-ID", "req-integration-incomplete"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.error.code", is("MRZ_INCOMPLETE")))
                .andExpect(jsonPath("$.raw_mrz.lines[0]", is("P<UTOERIKSSON")))
                .andExpect(jsonPath("$.raw_mrz.lines[1]", is("L898902C36UTO")))
                .andExpect(jsonPath("$.raw_mrz.ocr_lines[0]", is("P<UTOERIKSSON")))
                .andExpect(jsonPath("$.raw_mrz.corrected", is(false)))
                .andExpect(jsonPath("$.error.request_id", is("req-integration-incomplete")));

        assertThat(extractRequests).hasValue(1);
    }

    @Test
    void rejectsMissingApiKeyBeforeCallingOcrSidecar() throws Exception {
        mockMvc.perform(multipart("/api/v1/mrz/read").file(pngFile()))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code", is("UNAUTHORIZED")));

        assertThat(extractRequests).hasValue(0);
    }

    @Test
    void rejectsInvalidApiKeyBeforeCallingOcrSidecar() throws Exception {
        mockMvc.perform(multipart("/api/v1/mrz/read")
                        .file(pngFile())
                        .header("X-API-Key", "not-a-real-key"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code", is("UNAUTHORIZED")));

        assertThat(extractRequests).hasValue(0);
    }

    @Test
    void rateLimitsPerApiKeyBeforeCallingOcrSidecarAgain() throws Exception {
        mockMvc.perform(multipart("/api/v1/mrz/read")
                        .file(pngFile())
                        .header("X-API-Key", "rate-secret"))
                .andExpect(status().isOk());

        mockMvc.perform(multipart("/api/v1/mrz/read")
                        .file(pngFile())
                        .header("X-API-Key", "rate-secret"))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().string("Retry-After", "3600"))
                .andExpect(jsonPath("$.error.code", is("RATE_LIMIT_EXCEEDED")));

        assertThat(extractRequests).hasValue(1);
    }

    @Test
    void rejectsEmptyUploadBeforeCallingOcrSidecar() throws Exception {
        mockMvc.perform(multipart("/api/v1/mrz/read")
                        .file(new MockMultipartFile("file", "empty.png", "image/png", new byte[0]))
                        .header("X-API-Key", "upload-empty-secret"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code", is("INVALID_IMAGE")));

        assertThat(extractRequests).hasValue(0);
    }

    @Test
    void rejectsUnsupportedUploadContentTypeBeforeCallingOcrSidecar() throws Exception {
        mockMvc.perform(multipart("/api/v1/mrz/read")
                        .file(new MockMultipartFile("file", "document.txt", "text/plain", "hello".getBytes()))
                        .header("X-API-Key", "upload-type-secret"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code", is("INVALID_IMAGE")));

        assertThat(extractRequests).hasValue(0);
    }

    @Test
    void mapsSidecarServiceFailureToServiceUnavailable() throws Exception {
        sidecarResponse.set(new SidecarResponse(500, """
                {
                  "status": "OCR_SERVICE_UNAVAILABLE",
                  "message": "OCR sidecar failed"
                }
                """));

        mockMvc.perform(multipart("/api/v1/mrz/read")
                        .file(pngFile())
                        .header("X-API-Key", "sidecar-secret"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.error.code", is("OCR_SERVICE_UNAVAILABLE")))
                .andExpect(jsonPath("$.error.message", is("OCR sidecar failed")));

        assertThat(extractRequests).hasValue(1);
    }

    private static synchronized void ensureServerStarted() throws IOException {
        if (server != null) {
            return;
        }
        server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        server.createContext("/health", MrzApiIntegrationTest::handleHealth);
        server.createContext("/ocr/extract", MrzApiIntegrationTest::handleExtract);
        server.start();
    }

    private static void handleHealth(HttpExchange exchange) throws IOException {
        write(exchange, 200, "{\"status\":\"UP\"}");
    }

    private static void handleExtract(HttpExchange exchange) throws IOException {
        extractRequests.incrementAndGet();
        exchange.getRequestBody().readAllBytes();
        SidecarResponse response = sidecarResponse.get();
        write(exchange, response.statusCode(), response.body());
    }

    private static void write(HttpExchange exchange, int statusCode, String body) throws IOException {
        byte[] bodyBytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        exchange.sendResponseHeaders(statusCode, bodyBytes.length);
        exchange.getResponseBody().write(bodyBytes);
        exchange.close();
    }

    private MockMultipartFile pngFile() {
        return new MockMultipartFile(
                "file",
                "document.png",
                "image/png",
                Base64.getDecoder().decode(PNG_1X1_BASE64));
    }

    private record SidecarResponse(int statusCode, String body) {
        static SidecarResponse success() {
            return new SidecarResponse(200, """
                    {
                      "status": "SUCCESS",
                      "mrz_lines": [
                        "P<NGAOKONKWO<<CHUKWUEMEKA<JOHN<<<<<<<<<<<<<<",
                        "A123456784NGA9008157M2908144<<<<<<<<<<<<<<06"
                      ],
                      "confidence": 0.97,
                      "zone_bbox": { "x": 10, "y": 20, "width": 500, "height": 90 },
                      "image_quality_score": 0.95,
                      "preprocessing_applied": ["grayscale", "threshold"]
                    }
                    """);
        }
    }

    static class MockSidecarProperties implements ApplicationContextInitializer<ConfigurableApplicationContext> {
        @Override
        public void initialize(ConfigurableApplicationContext context) {
            try {
                ensureServerStarted();
            } catch (IOException exception) {
                throw new IllegalStateException("Failed to start mock OCR sidecar", exception);
            }
            int port = server.getAddress().getPort();
            TestPropertyValues.of(
                    "ocr.sidecar.extract-url=" + URI.create("http://localhost:" + port + "/ocr/extract"),
                    "ocr.sidecar.health-url=" + URI.create("http://localhost:" + port + "/health"))
                    .applyTo(context);
        }
    }
}
