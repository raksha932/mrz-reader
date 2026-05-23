package com.certipass.mrtd.api.auth;

public record ApiKeyPrincipal(String id, String tier) {
    public static final String REQUEST_ATTRIBUTE = ApiKeyPrincipal.class.getName();
}
