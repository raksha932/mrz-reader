package com.certipass.mrtd.api.auth;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import org.springframework.stereotype.Component;

@Component
public class ConfiguredApiKeyRepository implements ApiKeyRepository {
    private final Map<String, ApiKeyPrincipal> keysByValue;

    public ConfiguredApiKeyRepository(ApiKeyProperties properties) {
        Map<String, ApiKeyPrincipal> configuredKeys = new HashMap<>();
        for (ApiKeyProperties.ApiKey apiKey : properties.keys()) {
            if (isConfigured(apiKey)) {
                configuredKeys.put(apiKey.value(), new ApiKeyPrincipal(apiKey.id(), apiKey.tier()));
            }
        }
        this.keysByValue = Map.copyOf(configuredKeys);
    }

    @Override
    public Optional<ApiKeyPrincipal> findByValue(String apiKey) {
        if (apiKey == null || apiKey.isBlank()) {
            return Optional.empty();
        }
        return Optional.ofNullable(keysByValue.get(apiKey));
    }

    private boolean isConfigured(ApiKeyProperties.ApiKey apiKey) {
        return apiKey != null
                && apiKey.id() != null
                && !apiKey.id().isBlank()
                && apiKey.value() != null
                && !apiKey.value().isBlank();
    }
}
