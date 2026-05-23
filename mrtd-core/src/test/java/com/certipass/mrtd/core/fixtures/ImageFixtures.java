package com.certipass.mrtd.core.fixtures;

import java.util.Base64;

/**
 * Small image byte fixtures for image validation and orchestration tests.
 */
public final class ImageFixtures {
    private static final String PNG_1X1_BASE64 =
            "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+/p9sAAAAASUVORK5CYII=";

    public static byte[] png1x1() {
        return Base64.getDecoder().decode(PNG_1X1_BASE64);
    }

    private ImageFixtures() {
    }
}
