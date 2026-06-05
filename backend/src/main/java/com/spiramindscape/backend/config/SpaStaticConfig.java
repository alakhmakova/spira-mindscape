package com.spiramindscape.backend.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import org.springframework.web.servlet.resource.PathResourceResolver;

/**
 * Serves the built single-page app (copied into {@code classpath:/static/} at image
 * build time) and falls back to {@code index.html} for client-side routes, so a hard
 * refresh on e.g. {@code /goals/123} still loads the SPA instead of 404-ing.
 *
 * <p>API paths are never hijacked: real static files are served as-is, and
 * {@code /api/**} and {@code /graphql} are left to their controllers (or a normal
 * 404), so only genuine SPA routes resolve to {@code index.html}.
 *
 * <p>This only matters in production (single-origin container). In local dev the
 * SPA is served by Vite and {@code classpath:/static/} is empty, so this is inert.
 */
@Configuration
public class SpaStaticConfig implements WebMvcConfigurer {

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        registry.addResourceHandler("/**")
                .addResourceLocations("classpath:/static/")
                .resourceChain(true)
                .addResolver(new PathResourceResolver() {
                    @Override
                    protected Resource getResource(String resourcePath, Resource location) {
                        Resource requested;
                        try {
                            requested = location.createRelative(resourcePath);
                        } catch (Exception e) {
                            requested = null;
                        }
                        if (requested != null && requested.exists() && requested.isReadable()) {
                            return requested; // a real static asset (js/css/svg/…)
                        }
                        // Don't turn API/GraphQL misses into the SPA shell.
                        if (resourcePath.startsWith("api/") || resourcePath.startsWith("graphql")) {
                            return null;
                        }
                        // Any other path is a client-side route → serve the SPA shell.
                        ClassPathResource index = new ClassPathResource("static/index.html");
                        return index.exists() ? index : null;
                    }
                });
    }
}
