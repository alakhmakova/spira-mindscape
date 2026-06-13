package com.spiramindscape.backend.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.assertThatCode;

class CorsConfigTest {

    @Test
    @DisplayName("dev (COOKIE_SECURE=false) allows LAN/wildcard patterns")
    void devAllowsWildcards() {
        assertThatCode(() -> CorsConfig.assertSafeForProd(false, new String[]{
                "http://localhost:5173", "http://192.168.*:*"
        })).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("prod (COOKIE_SECURE=true) refuses to start with a wildcard origin")
    void prodRejectsWildcard() {
        assertThatThrownBy(() -> CorsConfig.assertSafeForProd(true, new String[]{
                "https://spira.example.com", "http://192.168.*:*"
        })).isInstanceOf(IllegalStateException.class).hasMessageContaining("Refusing to start");
    }

    @Test
    @DisplayName("prod refuses localhost / private-range origins")
    void prodRejectsPrivate() {
        assertThatThrownBy(() -> CorsConfig.assertSafeForProd(true, new String[]{"http://localhost:5173"}))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> CorsConfig.assertSafeForProd(true, new String[]{"http://10.0.0.5"}))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("prod accepts exact public origins")
    void prodAcceptsExactOrigins() {
        assertThatCode(() -> CorsConfig.assertSafeForProd(true, new String[]{
                "https://spira.example.com", "https://app.spira.com"
        })).doesNotThrowAnyException();
    }
}
