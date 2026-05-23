package com.certipass.mrtd.api.ratelimit;

import java.time.Duration;
import java.util.Map;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "api.rate-limit")
public record RateLimitProperties(boolean enabled, Map<String, TierLimit> tiers) {
    public RateLimitProperties {
        if (tiers == null || tiers.isEmpty()) {
            tiers = Map.of(
                    "default", new TierLimit(100, 100, Duration.ofMinutes(1)),
                    "premium", new TierLimit(1_000, 1_000, Duration.ofMinutes(1)));
        }
    }

    public TierLimit limitFor(String tier) {
        TierLimit configured = tiers.get(tier);
        if (configured != null) {
            return configured;
        }
        return tiers.getOrDefault("default", new TierLimit(100, 100, Duration.ofMinutes(1)));
    }

    public record TierLimit(int capacity, int refillTokens, Duration refillPeriod) {
        public TierLimit {
            if (capacity < 1) {
                throw new IllegalArgumentException("Rate limit capacity must be at least 1");
            }
            if (refillTokens < 1) {
                throw new IllegalArgumentException("Rate limit refill tokens must be at least 1");
            }
            if (refillPeriod == null || refillPeriod.isZero() || refillPeriod.isNegative()) {
                throw new IllegalArgumentException("Rate limit refill period must be positive");
            }
        }
    }
}
