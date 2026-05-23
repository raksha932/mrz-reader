package com.certipass.mrtd.api.health;

import com.certipass.mrtd.api.config.OcrSidecarProperties;
import java.io.IOException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.health.contributor.AbstractHealthIndicator;
import org.springframework.boot.health.contributor.Health;
import org.springframework.stereotype.Component;

@Component
public class OcrSidecarHealthIndicator extends AbstractHealthIndicator {
    private final HttpClient httpClient;
    private final OcrSidecarProperties properties;

    @Autowired
    public OcrSidecarHealthIndicator(OcrSidecarProperties properties) {
        this(HttpClient.newBuilder()
                .connectTimeout(properties.timeout())
                .build(), properties);
    }

    OcrSidecarHealthIndicator(HttpClient httpClient, OcrSidecarProperties properties) {
        this.httpClient = httpClient;
        this.properties = properties;
    }

    @Override
    protected void doHealthCheck(Health.Builder builder) throws Exception {
        long startedAt = System.nanoTime();
        try {
            HttpResponse<String> response = httpClient.send(healthRequest(), HttpResponse.BodyHandlers.ofString());
            long responseTimeMs = elapsedMillis(startedAt);
            if (response.statusCode() >= 200 && response.statusCode() < 300) {
                builder.up()
                        .withDetail("url", properties.healthUrl().toString())
                        .withDetail("statusCode", response.statusCode())
                        .withDetail("responseTimeMs", responseTimeMs);
            } else {
                builder.down()
                        .withDetail("url", properties.healthUrl().toString())
                        .withDetail("statusCode", response.statusCode())
                        .withDetail("responseTimeMs", responseTimeMs);
            }
        } catch (IOException | InterruptedException exception) {
            if (exception instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            builder.down(exception)
                    .withDetail("url", properties.healthUrl().toString())
                    .withDetail("responseTimeMs", elapsedMillis(startedAt));
        }
    }

    private HttpRequest healthRequest() {
        Duration timeout = properties.timeout();
        return HttpRequest.newBuilder(properties.healthUrl())
                .timeout(timeout)
                .GET()
                .build();
    }

    private long elapsedMillis(long startedAt) {
        return Duration.ofNanos(System.nanoTime() - startedAt).toMillis();
    }
}
