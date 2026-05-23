package com.certipass.mrtd.api.auth;

import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "api.auth")
public record ApiKeyProperties(
        boolean enabled,
        String headerName,
        List<ApiKey> keys,
        List<String> publicPaths) {
    public ApiKeyProperties {
        if (headerName == null || headerName.isBlank()) {
            headerName = "X-API-Key";
        }
        if (keys == null) {
            keys = List.of();
        }
        if (publicPaths == null || publicPaths.isEmpty()) {
            publicPaths = List.of(
                    "/actuator/health",
                    "/actuator/health/**",
                    "/swagger-ui.html",
                    "/swagger-ui/**",
                    "/v3/api-docs/**");
        }
    }

    public record ApiKey(String id, String value, String tier) {
        public ApiKey {
            if (tier == null || tier.isBlank()) {
                tier = "default";
            }
        }
    }
}
