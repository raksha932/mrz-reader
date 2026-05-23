package com.certipass.mrtd.api.error;

import com.certipass.mrtd.core.model.ErrorCode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;

@Component
public class ApiErrorWriter {
    private final ObjectMapper objectMapper;
    private final com.certipass.mrtd.api.web.RequestIdProvider requestIdProvider;

    public ApiErrorWriter(
            ObjectMapper objectMapper,
            com.certipass.mrtd.api.web.RequestIdProvider requestIdProvider) {
        this.objectMapper = objectMapper;
        this.requestIdProvider = requestIdProvider;
    }

    public void write(
            HttpServletRequest request,
            HttpServletResponse response,
            int status,
            ErrorCode errorCode,
            String message) throws IOException {
        response.setStatus(status);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        objectMapper.writeValue(
                response.getWriter(),
                ApiErrorResponse.of(errorCode, message, requestIdProvider.currentRequestId(request)));
    }
}
