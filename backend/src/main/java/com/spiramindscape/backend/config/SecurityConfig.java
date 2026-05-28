package com.spiramindscape.backend.config;

import com.spiramindscape.backend.auth.AppUserOidcUserService;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfTokenRequestAttributeHandler;

/**
 * Spring Security configuration for Spira.
 *
 * <ul>
 *   <li>All data endpoints ({@code /graphql}, {@code /api/**}) require authentication.</li>
 *   <li>OAuth2/OIDC login endpoints, the health check, and the static frontend are public.</li>
 *   <li>Unauthenticated API requests get {@code 401} (not a redirect to Google) so the SPA
 *       can detect "session expired" without following a redirect.</li>
 *   <li>CSRF protection uses the double-submit cookie pattern: a readable {@code XSRF-TOKEN}
 *       cookie is issued; mutations must echo it in the {@code X-XSRF-TOKEN} header.</li>
 *   <li>Logout ({@code POST /api/auth/logout}) invalidates the session, clears the cookie,
 *       and returns {@code 204 No Content} so the SPA handles the redirect itself.</li>
 * </ul>
 */
@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final AppUserOidcUserService appUserOidcUserService;

    @Value("${app.frontend.url}")
    private String frontendUrl;

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {

        // --- CSRF ---
        // CookieCsrfTokenRepository makes the token readable by the SPA (HttpOnly=false).
        // CsrfTokenRequestAttributeHandler defers token loading so it doesn't fail on reads.
        CookieCsrfTokenRepository csrfRepo = CookieCsrfTokenRepository.withHttpOnlyFalse();
        CsrfTokenRequestAttributeHandler requestHandler = new CsrfTokenRequestAttributeHandler();
        // Set _csrf attribute name to null so it uses the default header approach
        requestHandler.setCsrfRequestAttributeName(null);

        http
            // ----- Authorization -----
            .authorizeHttpRequests(auth -> auth
                // Public: OAuth2 / OIDC dance
                .requestMatchers("/oauth2/**", "/login/**").permitAll()
                // Public: health check
                .requestMatchers("/api/health").permitAll()
                // Public: /api/auth/me returns 401 itself when anonymous (not a security rule)
                .requestMatchers("/api/auth/me").permitAll()
                // Everything else (especially /graphql) requires authentication
                .anyRequest().authenticated()
            )

            // ----- CSRF -----
            .csrf(csrf -> csrf
                .csrfTokenRepository(csrfRepo)
                .csrfTokenRequestHandler(requestHandler)
                // Health check and OAuth2 endpoints don't need CSRF
                .ignoringRequestMatchers("/api/health", "/oauth2/**", "/login/**")
            )

            // ----- OAuth2 login -----
            .oauth2Login(oauth2 -> oauth2
                .userInfoEndpoint(userInfo -> userInfo
                    .oidcUserService(appUserOidcUserService)
                )
                // After successful login, redirect to the SPA
                .defaultSuccessUrl(frontendUrl, true)
                // On failure, redirect to the SPA login page with an error flag
                .failureUrl(frontendUrl + "/login?error")
            )

            // ----- Logout -----
            .logout(logout -> logout
                .logoutUrl("/api/auth/logout")
                .invalidateHttpSession(true)
                .deleteCookies("JSESSIONID")
                .logoutSuccessHandler((request, response, authentication) -> {
                    response.setStatus(HttpServletResponse.SC_NO_CONTENT); // 204
                })
            )

            // ----- Unauthenticated API calls get 401, not a redirect to Google -----
            .exceptionHandling(ex -> ex
                .defaultAuthenticationEntryPointFor(
                    new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED),
                    request -> {
                        String path = request.getRequestURI();
                        return path.startsWith("/graphql") || path.startsWith("/api/");
                    }
                )
            );

        return http.build();
    }
}
