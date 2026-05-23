package com.certipass.mrtd.api.web;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

@Component
public class RequestIdProvider {
    public String currentRequestId() {
        String requestId = RequestIdFilter.currentRequestId();
        if (requestId != null) {
            return requestId;
        }
        if (RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attributes) {
            return currentRequestId(attributes.getRequest());
        }
        return null;
    }

    public String currentRequestId(HttpServletRequest request) {
        String threadRequestId = RequestIdFilter.currentRequestId();
        if (threadRequestId != null) {
            return threadRequestId;
        }
        Object value = request.getAttribute(RequestIdFilter.REQUEST_ATTRIBUTE);
        if (value instanceof String requestId) {
            return requestId;
        }
        String headerValue = request.getHeader(RequestIdFilter.HEADER_NAME);
        return headerValue == null || headerValue.isBlank() ? null : headerValue.trim();
    }
}
