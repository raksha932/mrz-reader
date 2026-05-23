package com.certipass.mrtd.api.ratelimit;

import com.certipass.mrtd.api.auth.ApiKeyPrincipal;
import java.time.Duration;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import org.springframework.stereotype.Component;

@Component
public class ApiRateLimiter {
    private final RateLimitProperties properties;
    private final ConcurrentMap<String, TokenBucket> buckets = new ConcurrentHashMap<>();

    public ApiRateLimiter(RateLimitProperties properties) {
        this.properties = properties;
    }

    public RateLimitDecision consume(ApiKeyPrincipal principal) {
        RateLimitProperties.TierLimit limit = properties.limitFor(principal.tier());
        TokenBucket bucket = buckets.computeIfAbsent(principal.id(), ignored -> TokenBucket.create(limit));
        return bucket.tryConsume(limit);
    }

    private static final class TokenBucket {
        private final int capacity;
        private double tokens;
        private long lastRefillNanos;

        private TokenBucket(int capacity, double tokens, long lastRefillNanos) {
            this.capacity = capacity;
            this.tokens = tokens;
            this.lastRefillNanos = lastRefillNanos;
        }

        static TokenBucket create(RateLimitProperties.TierLimit limit) {
            return new TokenBucket(limit.capacity(), limit.capacity(), System.nanoTime());
        }

        synchronized RateLimitDecision tryConsume(RateLimitProperties.TierLimit limit) {
            refill(limit);
            if (tokens >= 1.0d) {
                tokens -= 1.0d;
                return RateLimitDecision.allow();
            }

            double missingTokens = 1.0d - tokens;
            double nanosPerToken = (double) limit.refillPeriod().toNanos() / limit.refillTokens();
            long retryAfterNanos = Math.max(1L, (long) Math.ceil(missingTokens * nanosPerToken));
            return RateLimitDecision.reject(Duration.ofNanos(retryAfterNanos));
        }

        private void refill(RateLimitProperties.TierLimit limit) {
            long now = System.nanoTime();
            long elapsedNanos = Math.max(0L, now - lastRefillNanos);
            if (elapsedNanos == 0L) {
                return;
            }

            double refillTokens = ((double) elapsedNanos / limit.refillPeriod().toNanos()) * limit.refillTokens();
            tokens = Math.min(capacity, tokens + refillTokens);
            lastRefillNanos = now;
        }
    }
}
