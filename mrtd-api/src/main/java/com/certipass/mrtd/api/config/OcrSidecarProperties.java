package com.certipass.mrtd.api.config;

import java.net.URI;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "ocr.sidecar")
public record OcrSidecarProperties(
        URI extractUrl,
        URI healthUrl,
        Duration timeout
) {
    public OcrSidecarProperties {
        if (extractUrl == null) {
            extractUrl = URI.create("http://localhost:8001/ocr/extract");
        }
        if (healthUrl == null) {
            healthUrl = URI.create("http://localhost:8001/health");
        }
        if (timeout == null) {
            timeout = Duration.ofSeconds(5);
        }
    }
}
