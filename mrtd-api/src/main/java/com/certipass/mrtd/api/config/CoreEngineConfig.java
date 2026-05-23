package com.certipass.mrtd.api.config;

import com.certipass.mrtd.core.ocr.OcrClient;
import com.certipass.mrtd.core.ocr.http.HttpOcrClient;
import com.certipass.mrtd.core.orchestration.MrzReaderOrchestrator;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.http.HttpClient;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class CoreEngineConfig {
    @Bean
    OcrClient ocrClient(OcrSidecarProperties properties, ObjectMapper objectMapper) {
        return new HttpOcrClient(
                HttpClient.newBuilder()
                        .connectTimeout(properties.timeout())
                        .build(),
                objectMapper,
                properties.extractUrl(),
                properties.timeout());
    }

    @Bean
    MrzReaderOrchestrator mrzReaderOrchestrator(OcrClient ocrClient) {
        return new MrzReaderOrchestrator(ocrClient);
    }
}
