package com.certipass.mrtd.api.ratelimit;

import com.certipass.mrtd.api.auth.ApiKeyPrincipal;
import com.certipass.mrtd.api.error.ApiErrorWriter;
import com.certipass.mrtd.api.web.PublicEndpointMatcher;
import com.certipass.mrtd.core.model.ErrorCode;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.Duration;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 20)
public class RateLimitFilter extends OncePerRequestFilter {
    private final RateLimitProperties properties;
    private final ApiRateLimiter rateLimiter;
    private final ApiErrorWriter errorWriter;
    private final PublicEndpointMatcher publicEndpointMatcher;

    public RateLimitFilter(
            RateLimitProperties properties,
            ApiRateLimiter rateLimiter,
            ApiErrorWriter errorWriter,
            PublicEndpointMatcher publicEndpointMatcher) {
        this.properties = properties;
        this.rateLimiter = rateLimiter;
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
        Object principal = request.getAttribute(ApiKeyPrincipal.REQUEST_ATTRIBUTE);
        if (!(principal instanceof ApiKeyPrincipal apiKeyPrincipal)) {
            filterChain.doFilter(request, response);
            return;
        }

        RateLimitDecision decision = rateLimiter.consume(apiKeyPrincipal);
        if (!decision.allowed()) {
            response.setHeader("Retry-After", Long.toString(retryAfterSeconds(decision.retryAfter())));
            errorWriter.write(
                    request,
                    response,
                    429,
                    ErrorCode.RATE_LIMIT_EXCEEDED,
                    "Rate limit exceeded. You are allowed "
                            + properties.limitFor(apiKeyPrincipal.tier()).capacity()
                            + " requests per configured refill period.");
            return;
        }

        filterChain.doFilter(request, response);
    }

    private long retryAfterSeconds(Duration retryAfter) {
        return Math.max(1L, (long) Math.ceil(retryAfter.toNanos() / 1_000_000_000.0d));
    }
}
