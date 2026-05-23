package com.certipass.mrtd.api.auth;

import com.certipass.mrtd.api.error.ApiErrorWriter;
import com.certipass.mrtd.api.web.PublicEndpointMatcher;
import com.certipass.mrtd.core.model.ErrorCode;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
public class ApiKeyAuthFilter extends OncePerRequestFilter {
    private static final String UNAUTHORIZED_MESSAGE =
            "Missing or invalid API key. Provide a valid key in the X-API-Key header.";

    private final ApiKeyProperties properties;
    private final ApiKeyRepository repository;
    private final ApiErrorWriter errorWriter;
    private final PublicEndpointMatcher publicEndpointMatcher;

    public ApiKeyAuthFilter(
            ApiKeyProperties properties,
            ApiKeyRepository repository,
            ApiErrorWriter errorWriter,
            PublicEndpointMatcher publicEndpointMatcher) {
        this.properties = properties;
        this.repository = repository;
        this.errorWriter = errorWriter;
        this.publicEndpointMatcher = publicEndpointMatcher;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !properties.enabled() || publicEndpointMatcher.isPublic(request);
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {
        String apiKey = request.getHeader(properties.headerName());
        ApiKeyPrincipal principal = repository.findByValue(apiKey).orElse(null);
        if (principal == null) {
            errorWriter.write(
                    request,
                    response,
                    HttpServletResponse.SC_UNAUTHORIZED,
                    ErrorCode.UNAUTHORIZED,
                    UNAUTHORIZED_MESSAGE);
            return;
        }

        request.setAttribute(ApiKeyPrincipal.REQUEST_ATTRIBUTE, principal);
        filterChain.doFilter(request, response);
    }
}
