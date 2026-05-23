package com.certipass.mrtd.api.web;

import com.certipass.mrtd.api.auth.ApiKeyProperties;
import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class PublicEndpointMatcher {
    private final List<String> publicPaths;

    public PublicEndpointMatcher(ApiKeyProperties properties) {
        this.publicPaths = properties.publicPaths();
    }

    public boolean isPublic(HttpServletRequest request) {
        String contextPath = request.getContextPath();
        String path = request.getRequestURI();
        if (!contextPath.isBlank() && path.startsWith(contextPath)) {
            path = path.substring(contextPath.length());
        }
        String requestPath = path;
        return publicPaths.stream().anyMatch(pattern -> matches(pattern, requestPath));
    }

    private boolean matches(String pattern, String path) {
        if (pattern == null || pattern.isBlank()) {
            return false;
        }
        if (pattern.endsWith("/**")) {
            String base = pattern.substring(0, pattern.length() - 3);
            return path.equals(base) || path.startsWith(base + "/");
        }
        if (pattern.endsWith("*")) {
            return path.startsWith(pattern.substring(0, pattern.length() - 1));
        }
        return path.equals(pattern);
    }
}
