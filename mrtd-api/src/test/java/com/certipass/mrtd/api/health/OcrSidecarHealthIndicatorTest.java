package com.certipass.mrtd.api.health;

import static org.assertj.core.api.Assertions.assertThat;

import com.certipass.mrtd.api.config.OcrSidecarProperties;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.time.Duration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.Status;

class OcrSidecarHealthIndicatorTest {
    private HttpServer server;

    @AfterEach
    void stopServer() {
        if (server != null) {
            server.stop(0);
        }
    }

    @Test
    void reportsUpWhenSidecarHealthEndpointReturnsSuccess() throws Exception {
        URI healthUrl = startServer(200);

        Health health = new OcrSidecarHealthIndicator(properties(healthUrl)).health();

        assertThat(health.getStatus()).isEqualTo(Status.UP);
        assertThat(health.getDetails()).containsEntry("statusCode", 200);
        assertThat(health.getDetails()).containsEntry("url", healthUrl.toString());
    }

    @Test
    void reportsDownWhenSidecarHealthEndpointReturnsFailure() throws Exception {
        URI healthUrl = startServer(503);

        Health health = new OcrSidecarHealthIndicator(properties(healthUrl)).health();

        assertThat(health.getStatus()).isEqualTo(Status.DOWN);
        assertThat(health.getDetails()).containsEntry("statusCode", 503);
    }

    private URI startServer(int statusCode) throws IOException {
        server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        server.createContext("/health", exchange -> {
            byte[] body = "{\"status\":\"UP\"}".getBytes();
            exchange.sendResponseHeaders(statusCode, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        server.start();
        return URI.create("http://localhost:" + server.getAddress().getPort() + "/health");
    }

    private OcrSidecarProperties properties(URI healthUrl) {
        return new OcrSidecarProperties(
                URI.create("http://localhost:8001/ocr/extract"),
                healthUrl,
                Duration.ofSeconds(2));
    }
}
