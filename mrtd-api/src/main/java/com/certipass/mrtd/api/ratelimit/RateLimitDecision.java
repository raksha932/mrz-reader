package com.certipass.mrtd.api.ratelimit;

import java.time.Duration;

public record RateLimitDecision(boolean allowed, Duration retryAfter) {
    static RateLimitDecision allow() {
        return new RateLimitDecision(true, Duration.ZERO);
    }

    static RateLimitDecision reject(Duration retryAfter) {
        return new RateLimitDecision(false, retryAfter);
    }
}
