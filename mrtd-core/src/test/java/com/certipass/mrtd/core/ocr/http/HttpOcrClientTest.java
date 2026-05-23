package com.certipass.mrtd.core.ocr.http;

import static org.assertj.core.api.Assertions.assertThat;

import com.certipass.mrtd.core.model.ErrorCode;
import com.certipass.mrtd.core.ocr.OcrExtractionResult;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;
import javax.imageio.ImageIO;
import java.util.concurrent.Executors;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class HttpOcrClientTest {
    private HttpServer server;
    private final AtomicInteger requestBodyLength = new AtomicInteger();

    @AfterEach
    void stopServer() {
        if (server != null) {
            server.stop(0);
        }
    }

    @Test
    void mapsSuccessfulSidecarResponse() throws IOException {
        startServer(200, """
                {
                  "status": "SUCCESS",
                  "mrz_lines": [
                    "P<UTOERIKSSON<<ANNA<MARIA<<<<<<<<<<<<<<<<<<<",
                    "L898902C36UTO7408122F1204159ZE184226B<<<<<10"
                  ],
                  "confidence": 0.97,
                  "image_quality_score": 0.85,
                  "zone_bbox": { "x": 50, "y": 420, "width": 600, "height": 80 },
                  "preprocessing_applied": ["grayscale", "adaptive_threshold"]
                }
                """);

        OcrExtractionResult result = client().extractMrz(new byte[] {1, 2, 3});

        assertThat(result).isInstanceOf(OcrExtractionResult.Success.class);
        OcrExtractionResult.Success success = (OcrExtractionResult.Success) result;
        assertThat(success.result().mrzLines()).hasSize(2);
        assertThat(success.result().confidence()).isEqualTo(0.97);
        assertThat(success.result().qualityScore()).isEqualTo(0.85);
        assertThat(success.result().zoneBoundingBox().width()).isEqualTo(600);
        assertThat(success.result().preprocessingApplied()).containsExactly("grayscale", "adaptive_threshold");
    }

    @Test
    void mapsKnownSidecarErrorStatus() throws IOException {
        startServer(422, """
                {
                  "status": "MRZ_NOT_DETECTED",
                  "message": "Document found but MRZ zone could not be isolated"
                }
                """);

        OcrExtractionResult result = client().extractMrz(new byte[] {1, 2, 3});

        assertThat(result).isInstanceOf(OcrExtractionResult.Failure.class);
        OcrExtractionResult.Failure failure = (OcrExtractionResult.Failure) result;
        assertThat(failure.errorCode()).isEqualTo(ErrorCode.MRZ_NOT_DETECTED);
        assertThat(failure.message()).contains("MRZ zone");
    }

    @Test
    void mapsMalformedResponseToServiceUnavailable() throws IOException {
        startServer(200, "not-json");

        OcrExtractionResult result = client().extractMrz(new byte[] {1, 2, 3});

        assertThat(result).isInstanceOf(OcrExtractionResult.Failure.class);
        assertThat(((OcrExtractionResult.Failure) result).errorCode())
                .isEqualTo(ErrorCode.OCR_SERVICE_UNAVAILABLE);
    }

    @Test
    void downsizesLargeImagesBeforeForwardingToSidecar() throws IOException {
        startServer(200, """
                {
                  "status": "SUCCESS",
                  "mrz_lines": [
                    "P<UTOERIKSSON<<ANNA<MARIA<<<<<<<<<<<<<<<<<<<",
                    "L898902C36UTO7408122F1204159ZE184226B<<<<<10"
                  ],
                  "confidence": 0.97,
                  "image_quality_score": 0.85,
                  "zone_bbox": { "x": 50, "y": 420, "width": 600, "height": 80 },
                  "preprocessing_applied": ["grayscale"]
                }
                """);
        byte[] largePng = largePng();

        OcrExtractionResult result = client().extractMrz(largePng);

        assertThat(result).isInstanceOf(OcrExtractionResult.Success.class);
        assertThat(requestBodyLength.get()).isLessThan(largePng.length);
    }

    private HttpOcrClient client() {
        return new HttpOcrClient(
                java.net.http.HttpClient.newHttpClient(),
                new com.fasterxml.jackson.databind.ObjectMapper(),
                serverUri(),
                Duration.ofSeconds(2));
    }

    private void startServer(int statusCode, String responseBody) throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/ocr/extract", exchange -> respond(exchange, statusCode, responseBody));
        server.setExecutor(Executors.newSingleThreadExecutor());
        server.start();
    }

    private void respond(HttpExchange exchange, int statusCode, String responseBody) throws IOException {
        assertThat(exchange.getRequestMethod()).isEqualTo("POST");
        assertThat(exchange.getRequestHeaders().getFirst("Content-Type")).contains("multipart/form-data");
        requestBodyLength.set(exchange.getRequestBody().readAllBytes().length);
        byte[] response = responseBody.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "application/json");
        exchange.sendResponseHeaders(statusCode, response.length);
        exchange.getResponseBody().write(response);
        exchange.close();
    }

    private byte[] largePng() throws IOException {
        BufferedImage image = new BufferedImage(2200, 1800, BufferedImage.TYPE_INT_RGB);
        for (int y = 0; y < image.getHeight(); y++) {
            for (int x = 0; x < image.getWidth(); x++) {
                int red = (x * 17 + y * 3) & 0xFF;
                int green = (x * 5 + y * 11) & 0xFF;
                int blue = (x * 13 + y * 7) & 0xFF;
                image.setRGB(x, y, (red << 16) | (green << 8) | blue);
            }
        }
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        ImageIO.write(image, "png", output);
        return output.toByteArray();
    }

    private java.net.URI serverUri() {
        return java.net.URI.create("http://127.0.0.1:" + server.getAddress().getPort() + "/ocr/extract");
    }
}
