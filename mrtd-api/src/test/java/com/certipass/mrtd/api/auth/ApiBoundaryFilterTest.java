package com.certipass.mrtd.api.auth;

import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.certipass.mrtd.api.error.ApiErrorWriter;
import com.certipass.mrtd.api.error.ErrorResponseMapper;
import com.certipass.mrtd.api.error.GlobalExceptionHandler;
import com.certipass.mrtd.api.mrz.MrzController;
import com.certipass.mrtd.api.mrz.MrzResponseMapper;
import com.certipass.mrtd.api.ratelimit.ApiRateLimiter;
import com.certipass.mrtd.api.ratelimit.RateLimitFilter;
import com.certipass.mrtd.api.ratelimit.RateLimitProperties;
import com.certipass.mrtd.api.web.PublicEndpointMatcher;
import com.certipass.mrtd.api.web.RequestIdFilter;
import com.certipass.mrtd.api.web.RequestIdProvider;
import com.certipass.mrtd.core.model.ErrorCode;
import com.certipass.mrtd.core.ocr.OcrExtractionResult;
import com.certipass.mrtd.core.orchestration.MrzReaderOrchestrator;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import java.time.Duration;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class ApiBoundaryFilterTest {
    private static final String PNG_1X1_BASE64 =
            "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+/p9sAAAAASUVORK5CYII=";

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        ApiKeyProperties apiKeyProperties = new ApiKeyProperties(
                true,
                "X-API-Key",
                List.of(new ApiKeyProperties.ApiKey("test-key", "secret", "default")),
                List.of("/actuator/health", "/actuator/health/**", "/swagger-ui.html", "/swagger-ui/**", "/v3/api-docs/**"));
        RateLimitProperties rateLimitProperties = new RateLimitProperties(
                true,
                Map.of("default", new RateLimitProperties.TierLimit(1, 1, Duration.ofHours(1))));
        ObjectMapper objectMapper = JsonMapper.builder().findAndAddModules().build();
        RequestIdProvider requestIdProvider = new RequestIdProvider();
        ApiErrorWriter errorWriter = new ApiErrorWriter(objectMapper, requestIdProvider);
        PublicEndpointMatcher publicEndpointMatcher = new PublicEndpointMatcher(apiKeyProperties);
        ApiKeyAuthFilter authFilter = new ApiKeyAuthFilter(
                apiKeyProperties,
                new ConfiguredApiKeyRepository(apiKeyProperties),
                errorWriter,
                publicEndpointMatcher);
        RateLimitFilter rateLimitFilter = new RateLimitFilter(
                rateLimitProperties,
                new ApiRateLimiter(rateLimitProperties),
                errorWriter,
                publicEndpointMatcher);

        MrzController controller = new MrzController(
                new MrzReaderOrchestrator(imageData -> new OcrExtractionResult.Failure(
                        ErrorCode.MRZ_NOT_DETECTED,
                        "Document found but MRZ zone could not be isolated")),
                new MrzResponseMapper(),
                new ErrorResponseMapper(),
                requestIdProvider);

        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler(requestIdProvider))
                .addFilters(new RequestIdFilter(), authFilter, rateLimitFilter)
                .build();
    }

    @Test
    void rejectsMissingApiKey() throws Exception {
        mockMvc.perform(multipart("/api/v1/mrz/read").file(pngFile()))
                .andExpect(status().isUnauthorized())
                .andExpect(header().exists("X-Request-ID"))
                .andExpect(jsonPath("$.error.code", is("UNAUTHORIZED")))
                .andExpect(jsonPath("$.error.message", is(
                        "Missing or invalid API key. Provide a valid key in the X-API-Key header.")))
                .andExpect(jsonPath("$.error.request_id").exists());
    }

    @Test
    void rejectsInvalidApiKey() throws Exception {
        mockMvc.perform(multipart("/api/v1/mrz/read")
                        .file(pngFile())
                        .header("X-API-Key", "wrong"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code", is("UNAUTHORIZED")));
    }

    @Test
    void acceptsValidApiKeyBeforeControllerHandling() throws Exception {
        mockMvc.perform(multipart("/api/v1/mrz/read")
                        .file(pngFile())
                        .header("X-API-Key", "secret")
                        .header("X-Request-ID", "req-client-1"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(header().string("X-Request-ID", "req-client-1"))
                .andExpect(jsonPath("$.error.code", is("MRZ_NOT_DETECTED")))
                .andExpect(jsonPath("$.error.request_id", is("req-client-1")));
    }

    @Test
    void rejectsRequestsThatExceedTheApiKeyRateLimit() throws Exception {
        mockMvc.perform(multipart("/api/v1/mrz/read")
                        .file(pngFile())
                        .header("X-API-Key", "secret"))
                .andExpect(status().isUnprocessableEntity());

        mockMvc.perform(multipart("/api/v1/mrz/read")
                        .file(pngFile())
                        .header("X-API-Key", "secret")
                        .header("X-Request-ID", "req-rate-2"))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().string("Retry-After", "3600"))
                .andExpect(header().string("X-Request-ID", "req-rate-2"))
                .andExpect(jsonPath("$.error.request_id", is("req-rate-2")))
                .andExpect(jsonPath("$.error.code", is("RATE_LIMIT_EXCEEDED")));
    }

    private MockMultipartFile pngFile() {
        return new MockMultipartFile(
                "file",
                "document.png",
                "image/png",
                Base64.getDecoder().decode(PNG_1X1_BASE64));
    }
}
