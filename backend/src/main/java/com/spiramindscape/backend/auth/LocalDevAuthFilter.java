package com.spiramindscape.backend.auth;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.context.annotation.Profile;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.core.oidc.OidcIdToken;
import org.springframework.security.oauth2.core.oidc.user.DefaultOidcUser;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Instant;
import java.util.List;

/**
 * LOCAL-DEV-ONLY auto-login. Active only under the {@code local} Spring profile.
 *
 * <p>Local development is auth-gated and user-scoped just like production, but
 * running the real Google OAuth dance every time (and configuring a localhost
 * redirect URI) is friction for quick UI checks. Under the {@code local} profile
 * this filter silently authenticates every request as one fixed dev user, so you
 * can open the app at {@code localhost:5173} and land straight inside — no login.
 *
 * <p>It is the headerless sibling of {@link E2eTestAuthFilter}: the E2E suite must
 * opt in per request via {@code X-E2E-Auth}, whereas local dev just wants to always
 * be "logged in". The dev user is found-or-created, so any AI keys / data you save
 * persist across restarts.
 *
 * <p>Production never activates {@code local}, so this bean doesn't exist there and
 * real OAuth is fully enforced. (See {@code SecurityConfig}, which also disables CSRF
 * only under this profile.)
 */
@Component
@Profile("local")
public class LocalDevAuthFilter extends OncePerRequestFilter {

    private static final String DEV_SUB = "local-dev-user";
    private static final String DEV_EMAIL = "dev@local";

    private final AppUserRepository appUserRepository;

    public LocalDevAuthFilter(AppUserRepository appUserRepository) {
        this.appUserRepository = appUserRepository;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        // Spring Security's AnonymousAuthenticationFilter runs before this filter and
        // sets a non-null AnonymousAuthenticationToken, so a plain `== null` check never
        // fires. Treat anonymous / unauthenticated as "no user" and log in the dev user;
        // a real OAuth session (if one somehow exists) is left untouched.
        Authentication existing = SecurityContextHolder.getContext().getAuthentication();
        boolean unauthenticated = existing == null
                || existing instanceof AnonymousAuthenticationToken
                || !existing.isAuthenticated();
        if (unauthenticated) {
            AppUser user = appUserRepository.findByGoogleSub(DEV_SUB).orElseGet(() -> {
                AppUser u = new AppUser();
                u.setGoogleSub(DEV_SUB);
                u.setEmail(DEV_EMAIL);
                u.setName("Local Dev");
                u.setRole("USER");
                return appUserRepository.save(u);
            });
            SecurityContextHolder.getContext().setAuthentication(buildAuth(user));
        }
        filterChain.doFilter(request, response);
    }

    /**
     * Re-run on the ASYNC dispatch too. The AI chat endpoint streams via an SseEmitter
     * (async); this dev auth is per-request and not stored in a session, so without this
     * the async dispatch re-enters the filter chain with no authentication and Spring
     * Security's AuthorizationFilter denies it ("Access Denied"), tearing down the stream
     * before the proposal / done events flush. Re-authenticating on the dispatch lets the
     * SSE response complete cleanly.
     */
    @Override
    protected boolean shouldNotFilterAsyncDispatch() {
        return false;
    }

    private OAuth2AuthenticationToken buildAuth(AppUser user) {
        OidcIdToken token = OidcIdToken.withTokenValue("local-dev-token")
                .subject(user.getGoogleSub())
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(3600))
                .claim("email", user.getEmail())
                .build();
        DefaultOidcUser oidcUser = new DefaultOidcUser(
                List.of(new SimpleGrantedAuthority("ROLE_USER")), token);
        AppUserOidcUser principal = new AppUserOidcUser(oidcUser, user);
        return new OAuth2AuthenticationToken(principal, principal.getAuthorities(), "google");
    }
}
