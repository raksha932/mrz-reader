package com.certipass.mrtd.core.ocr.http;

import com.certipass.mrtd.core.model.ErrorCode;
import com.certipass.mrtd.core.ocr.OcrBoundingBox;
import com.certipass.mrtd.core.ocr.OcrClient;
import com.certipass.mrtd.core.ocr.OcrExtractionResult;
import com.certipass.mrtd.core.ocr.OcrResult;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import javax.imageio.ImageIO;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Default OCR client that calls the Python FastAPI sidecar over HTTP.
 */
public final class HttpOcrClient implements OcrClient {
    private static final Duration DEFAULT_TIMEOUT = Duration.ofSeconds(5);
    private static final int MAX_SIDECAR_IMAGE_DIMENSION = 1600;

    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final URI extractUri;
    private final Duration timeout;

    public HttpOcrClient(String extractUrl) {
        this(URI.create(extractUrl));
    }

    public HttpOcrClient(URI extractUri) {
        this(HttpClient.newHttpClient(), new ObjectMapper(), extractUri, DEFAULT_TIMEOUT);
    }

    public HttpOcrClient(HttpClient httpClient, ObjectMapper objectMapper, URI extractUri, Duration timeout) {
        this.httpClient = Objects.requireNonNull(httpClient, "httpClient");
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper");
        this.extractUri = Objects.requireNonNull(extractUri, "extractUri");
        this.timeout = Objects.requireNonNull(timeout, "timeout");
    }

    @Override
    public OcrExtractionResult extractMrz(byte[] imageData) {
        String boundary = "mrtd-" + UUID.randomUUID();
        byte[] sidecarImageData = prepareImageForSidecar(imageData);
        HttpRequest request = HttpRequest.newBuilder(extractUri)
                .version(HttpClient.Version.HTTP_1_1)
                .timeout(timeout)
                .header("Content-Type", "multipart/form-data; boundary=" + boundary)
                .POST(multipartBody(boundary, sidecarImageData))
                .build();

        try {
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            return mapResponse(response);
        } catch (IOException exception) {
            return serviceUnavailable("OCR sidecar request failed: " + exception.getMessage());
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            return serviceUnavailable("OCR sidecar request was interrupted");
        } catch (RuntimeException exception) {
            return serviceUnavailable("OCR sidecar response could not be processed: " + exception.getMessage());
        }
    }

    private byte[] prepareImageForSidecar(byte[] imageData) {
        if (imageData == null || imageData.length == 0) {
            return imageData;
        }
        try {
            BufferedImage decoded = ImageIO.read(new ByteArrayInputStream(imageData));
            if (decoded == null) {
                return imageData;
            }
            int longestSide = Math.max(decoded.getWidth(), decoded.getHeight());
            if (longestSide <= MAX_SIDECAR_IMAGE_DIMENSION) {
                return imageData;
            }

            double scale = (double) MAX_SIDECAR_IMAGE_DIMENSION / longestSide;
            int targetWidth = Math.max(1, (int) Math.round(decoded.getWidth() * scale));
            int targetHeight = Math.max(1, (int) Math.round(decoded.getHeight() * scale));
            BufferedImage resized = new BufferedImage(targetWidth, targetHeight, BufferedImage.TYPE_INT_RGB);
            Graphics2D graphics = resized.createGraphics();
            try {
                graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
                graphics.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
                graphics.drawImage(decoded, 0, 0, targetWidth, targetHeight, null);
            } finally {
                graphics.dispose();
            }

            ByteArrayOutputStream output = new ByteArrayOutputStream();
            if (ImageIO.write(resized, "jpg", output)) {
                byte[] resizedBytes = output.toByteArray();
                return resizedBytes.length < imageData.length ? resizedBytes : imageData;
            }
        } catch (IOException ignored) {
            return imageData;
        }
        return imageData;
    }

    private HttpRequest.BodyPublisher multipartBody(String boundary, byte[] imageData) {
        List<byte[]> parts = new ArrayList<>();
        parts.add(("--" + boundary + "\r\n"
                + "Content-Disposition: form-data; name=\"image\"; filename=\"document\"\r\n"
                + "Content-Type: application/octet-stream\r\n\r\n").getBytes(StandardCharsets.UTF_8));
        parts.add(imageData == null ? new byte[0] : imageData.clone());
        parts.add(("\r\n--" + boundary + "--\r\n").getBytes(StandardCharsets.UTF_8));
        return HttpRequest.BodyPublishers.ofByteArrays(parts);
    }

    private OcrExtractionResult mapResponse(HttpResponse<String> response) throws IOException {
        SidecarResponse body = objectMapper.readValue(response.body(), SidecarResponse.class);
        if (response.statusCode() >= 200 && response.statusCode() < 300 && "SUCCESS".equals(body.status())) {
            return new OcrExtractionResult.Success(new OcrResult(
                    body.mrzLines(),
                    body.confidence(),
                    body.imageQualityScore(),
                    body.zoneBbox() == null
                            ? null
                            : new OcrBoundingBox(
                                    body.zoneBbox().x(),
                                    body.zoneBbox().y(),
                                    body.zoneBbox().width(),
                                    body.zoneBbox().height()),
                    body.preprocessingApplied()));
        }

        ErrorCode code = mapStatus(body.status(), response.statusCode());
        String message = body.message() == null || body.message().isBlank()
                ? "OCR sidecar returned status " + body.status()
                : body.message();
        return new OcrExtractionResult.Failure(code, message);
    }

    private ErrorCode mapStatus(String status, int httpStatus) {
        if ("NO_DOCUMENT_FOUND".equals(status)) {
            return ErrorCode.NO_DOCUMENT_FOUND;
        }
        if ("IMAGE_QUALITY_LOW".equals(status)) {
            return ErrorCode.IMAGE_QUALITY_LOW;
        }
        if ("MRZ_NOT_DETECTED".equals(status)) {
            return ErrorCode.MRZ_NOT_DETECTED;
        }
        if (httpStatus >= 500) {
            return ErrorCode.OCR_SERVICE_UNAVAILABLE;
        }
        return ErrorCode.OCR_SERVICE_UNAVAILABLE;
    }

    private OcrExtractionResult.Failure serviceUnavailable(String message) {
        return new OcrExtractionResult.Failure(ErrorCode.OCR_SERVICE_UNAVAILABLE, message);
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record SidecarResponse(
            String status,
            @JsonProperty("mrz_lines") List<String> mrzLines,
            double confidence,
            @JsonProperty("image_quality_score") double imageQualityScore,
            @JsonProperty("zone_bbox") SidecarBoundingBox zoneBbox,
            @JsonProperty("preprocessing_applied") List<String> preprocessingApplied,
            String message
    ) {
        private SidecarResponse {
            mrzLines = mrzLines == null ? List.of() : List.copyOf(mrzLines);
            preprocessingApplied = preprocessingApplied == null ? List.of() : List.copyOf(preprocessingApplied);
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record SidecarBoundingBox(int x, int y, int width, int height) {
    }
}
