package com.spiramindscape.backend.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.intercept.AuthorizationFilter;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;
import java.util.Optional;

/**
 * The app's first caller that is not a user: Cloud Scheduler, calling
 * {@code POST /internal/reminders/dispatch} every minute.
 *
 * <p>Its own chain, ahead of the user chain, for {@code /internal/**} only. No session, no OAuth
 * login, and **CSRF off — but not authentication**: a request is admitted only with a Bearer OIDC
 * token that {@link SchedulerTokenVerifier} accepts. No token is 401; a token that fails (wrong
 * audience, wrong account, expired, forged) is 403. The dev profiles' auto-login filters belong to
 * the user chain and never reach this one.
 */
@Configuration
public class InternalSecurityConfig {

    static final String SCHEDULER = "SCHEDULER";

    @Bean
    @Order(1)
    public SecurityFilterChain internalSecurityFilterChain(HttpSecurity http, SchedulerTokenVerifier verifier)
            throws Exception {
        http.securityMatcher("/internal/**")
                .csrf(csrf -> csrf.disable())
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .requestCache(cache -> cache.disable())
                .addFilterBefore(new SchedulerAuthFilter(verifier), AuthorizationFilter.class)
                .authorizeHttpRequests(auth -> auth.anyRequest().hasAuthority(SCHEDULER))
                .exceptionHandling(ex -> ex
                        .authenticationEntryPoint(new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED)));
        return http.build();
    }

    /** Not a bean: a {@code @Component} filter would also be registered for every request. */
    static final class SchedulerAuthFilter extends OncePerRequestFilter {

        private static final Logger log = LoggerFactory.getLogger("security.internal");

        private final SchedulerTokenVerifier verifier;

        SchedulerAuthFilter(SchedulerTokenVerifier verifier) {
            this.verifier = verifier;
        }

        @Override
        protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
                throws ServletException, IOException {
            String header = request.getHeader("Authorization");
            if (header == null || !header.regionMatches(true, 0, "Bearer ", 0, 7)) {
                chain.doFilter(request, response); // anonymous → 401 from the entry point
                return;
            }
            Optional<String> account = verifier.verify(header.substring(7).trim());
            if (account.isEmpty()) {
                log.warn("internal_call_refused path={}", request.getRequestURI());
                response.setStatus(HttpServletResponse.SC_FORBIDDEN);
                return;
            }
            SecurityContextHolder.getContext().setAuthentication(UsernamePasswordAuthenticationToken.authenticated(
                    "scheduler", null, List.of(new SimpleGrantedAuthority(SCHEDULER))));
            try {
                chain.doFilter(request, response);
            } finally {
                SecurityContextHolder.clearContext();
            }
        }
    }
}
