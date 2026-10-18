package com.spiramindscape.backend.push.web;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Where the VAPID pair comes from. Locally it is created once and then reused — a browser
 * subscribed under one public key stops receiving pushes when it changes — and the environment
 * always wins. Without either, push is off (and the app says so), never half on.
 */
class WebPushPropertiesTest {

    private static final String RFC_PUBLIC =
            "BP4z9KsN6nGRTbVYI_c7VJSPQTBtkgcy27mlmlMoZIIgDll6e3vCYLocInmYWAmS6TlzAC8wEqKK6PBru3jl7A8";
    private static final String RFC_PRIVATE = "yfWPiYE-n46HLnH0KqZOF1fJJU3MYrct3AELtAQ-oRw";

    @Test
    @DisplayName("Locally, a pair is created on first start and the same pair is read back after")
    void createdOnceThenReused(@TempDir Path dir) throws Exception {
        Path file = dir.resolve(".spira/vapid-keys.properties");
        WebPushProperties first = new WebPushProperties("", "", "mailto:x@y.z", true, file.toString());
        assertThat(first.enabled()).isTrue();
        assertThat(Files.exists(file)).isTrue();
        assertThat(WebPushCrypto.decode(first.publicKey())).hasSize(65);
        first.signingKey(); // a usable private key

        WebPushProperties second = new WebPushProperties("", "", "mailto:x@y.z", true, file.toString());
        assertThat(second.publicKey()).isEqualTo(first.publicKey());
    }

    @Test
    @DisplayName("Keys from the environment win over the local file")
    void environmentWins(@TempDir Path dir) {
        WebPushProperties props = new WebPushProperties(RFC_PUBLIC, RFC_PRIVATE, "mailto:x@y.z", true,
                dir.resolve("keys.properties").toString());
        assertThat(props.publicKey()).isEqualTo(RFC_PUBLIC);
        assertThat(Files.exists(dir.resolve("keys.properties"))).isFalse();
    }

    @Test
    @DisplayName("Production-like: no keys and no auto-generation means push is off, and nothing is written")
    void offWithoutKeys(@TempDir Path dir) {
        WebPushProperties props = new WebPushProperties("", "", "mailto:x@y.z", false,
                dir.resolve("keys.properties").toString());
        assertThat(props.enabled()).isFalse();
        assertThat(Files.exists(dir.resolve("keys.properties"))).isFalse();
    }
}
