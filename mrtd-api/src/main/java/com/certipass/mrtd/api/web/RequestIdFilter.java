package com.certipass.mrtd.api.web;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.UUID;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class RequestIdFilter extends OncePerRequestFilter {
    public static final String HEADER_NAME = "X-Request-ID";
    public static final String REQUEST_ATTRIBUTE = RequestIdFilter.class.getName() + ".requestId";
    private static final ThreadLocal<String> CURRENT_REQUEST_ID = new ThreadLocal<>();

    static String currentRequestId() {
        return CURRENT_REQUEST_ID.get();
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {
        String requestId = requestId(request.getHeader(HEADER_NAME));
        request.setAttribute(REQUEST_ATTRIBUTE, requestId);
        response.setHeader(HEADER_NAME, requestId);
        CURRENT_REQUEST_ID.set(requestId);
        MDC.put("request_id", requestId);
        try {
            filterChain.doFilter(request, response);
        } finally {
            MDC.remove("request_id");
            CURRENT_REQUEST_ID.remove();
        }
    }

    private String requestId(String headerValue) {
        if (headerValue != null && isValid(headerValue)) {
            return headerValue.trim();
        }
        return "req_" + UUID.randomUUID().toString().replace("-", "");
    }

    private boolean isValid(String value) {
        String trimmed = value.trim();
        return !trimmed.isEmpty() && trimmed.length() <= 128 && trimmed.chars().allMatch(this::isHeaderSafe);
    }

    private boolean isHeaderSafe(int character) {
        return character >= 33 && character <= 126;
    }
}
