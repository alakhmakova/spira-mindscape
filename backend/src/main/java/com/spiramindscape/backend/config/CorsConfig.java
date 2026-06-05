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

    public CorsConfig(@Value("${app.cors.allowed-origins}") String allowedOriginsProperty) {
        this.allowedOrigins = Arrays.stream(allowedOriginsProperty.split(","))
                .map(String::trim)
                .filter(origin -> !origin.isEmpty())
                .toArray(String[]::new);
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
