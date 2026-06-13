package com.spiramindscape.backend.ai.chat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * SSRF guard tests for {@link UrlReadService}. These assert the request is
 * REJECTED before any network call — no live fetching happens here.
 */
class UrlReadServiceTest {

    private final UrlReadService service = new UrlReadService();

    @ParameterizedTest
    @ValueSource(strings = {
            "http://localhost/admin",
            "http://127.0.0.1:8080/health",
            "http://169.254.169.254/latest/meta-data/",      // AWS metadata
            "http://metadata.google.internal/computeMetadata/v1/",  // GCP metadata
            "http://10.0.0.5/internal",                      // private range
            "http://192.168.1.1/router",                     // private range
            "http://[::1]/",                                 // IPv6 loopback
            "http://service.internal/secret",
    })
    @DisplayName("internal, loopback, private, and metadata addresses are blocked")
    void blocksInternalAddresses(String url) {
        assertThat(service.read(url)).contains("can't be fetched");
    }

    @Test
    @DisplayName("non-standard ports are blocked (SSRF to internal services)")
    void blocksNonStandardPorts() {
        assertThat(service.read("http://example.com:22/")).contains("standard web ports");
        assertThat(service.read("http://example.com:6379/")).contains("standard web ports");
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "ftp://example.com/file",
            "file:///etc/passwd",
            "gopher://example.com/",
            "not-a-url",
    })
    @DisplayName("non-http(s) schemes are rejected")
    void rejectsNonHttpSchemes(String url) {
        assertThat(service.read(url)).containsAnyOf("Only http", "valid URL");
    }

    @Test
    @DisplayName("blank/null input is handled without throwing")
    void handlesBlank() {
        assertThat(service.read(null)).isNotBlank();
        assertThat(service.read("   ")).isNotBlank();
    }
}
