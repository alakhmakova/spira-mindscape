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
        registry.addMapping("/**")
                .allowedOrigins(allowedOrigins)
                .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS")
                .allowedHeaders("*")
                .allowCredentials(true);   // required for session cookies + CSRF header
    }
}
