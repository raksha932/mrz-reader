package com.certipass.mrtd.api.auth;

import java.util.Optional;

public interface ApiKeyRepository {
    Optional<ApiKeyPrincipal> findByValue(String apiKey);
}
