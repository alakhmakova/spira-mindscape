package com.spiramindscape.backend.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.util.Arrays;

/**
 * CORS configuration.
 *
 * <p>In development, a Vite proxy makes the browser see a single origin
 * ({@code http://localhost:5173}), so SPA requests are same-origin and CORS is not
 * involved. This config covers any direct-to-backend calls or fallback scenarios.
 *
 * <p>In production, the SPA and backend share the same domain, so CORS is irrelevant —
 * but the config is kept harmless ({@code allowCredentials} + specific origin, never
 * wildcard {@code *} with credentials).
 */
@Configuration
public class CorsConfig implements WebMvcConfigurer {

    private final String[] allowedOrigins;

    public CorsConfig(
            @Value("${app.cors.allowed-origins}") String allowedOriginsProperty,
            @Value("${server.servlet.session.cookie.secure:false}") boolean cookieSecure) {
        this.allowedOrigins = Arrays.stream(allowedOriginsProperty.split(","))
                .map(String::trim)
                .filter(origin -> !origin.isEmpty())
                .toArray(String[]::new);
        assertSafeForProd(cookieSecure, this.allowedOrigins);
    }

    /**
     * Fail-fast (OWASP A02): the dev LAN/wildcard origin patterns must never
     * ship to production. {@code COOKIE_SECURE=true} marks prod; if any allowed
     * origin contains a wildcard or a private/loopback host while we're flagging
     * cookies Secure, refuse to start rather than expose a credentialed
     * cross-origin hole.
     */
    static void assertSafeForProd(boolean cookieSecure, String[] origins) {
        if (!cookieSecure) return; // dev: LAN patterns are intentional
        for (String o : origins) {
            String lower = o.toLowerCase();
            boolean unsafe = o.contains("*")
                    || lower.contains("localhost")
                    || lower.contains("127.0.0.1")
                    || lower.contains("://10.")
                    || lower.contains("://192.168.")
                    || lower.contains("://172.16.") || lower.contains("://172.17.")
                    || lower.contains("://172.18.");
            if (unsafe) {
                throw new IllegalStateException(
                        "Refusing to start: CORS origin '" + o + "' is a wildcard/private-range "
                        + "pattern but COOKIE_SECURE=true (production). Set CORS_ALLOWED_ORIGINS "
                        + "to exact production origins.");
            }
        }
    }

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        // allowedOriginPatterns (not allowedOrigins) so values may contain
        // wildcards — e.g. http://192.168.*:* lets a phone on the LAN reach the
        // Vite dev proxy, which forwards the device's Origin header to us.
        // allowCredentials(true) is required for the OAuth session cookie + CSRF
        // header, and is only legal alongside patterns (never with a bare "*").
        registry.addMapping("/graphql")
                .allowedOriginPatterns(allowedOrigins)
                .allowedMethods("GET", "POST", "OPTIONS")
                .allowedHeaders("*")
                .allowCredentials(true);

        registry.addMapping("/api/**")
                .allowedOriginPatterns(allowedOrigins)
                .allowedMethods("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS")
                .allowedHeaders("*")
                .allowCredentials(true);

        // OAuth2 login + redirect endpoints (Authorization Code flow).
        registry.addMapping("/oauth2/**")
                .allowedOriginPatterns(allowedOrigins)
                .allowedMethods("GET", "POST", "OPTIONS")
                .allowedHeaders("*")
                .allowCredentials(true);

        registry.addMapping("/login/**")
                .allowedOriginPatterns(allowedOrigins)
                .allowedMethods("GET", "POST", "OPTIONS")
                .allowedHeaders("*")
                .allowCredentials(true);
    }
}
