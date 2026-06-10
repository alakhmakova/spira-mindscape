package com.spiramindscape.backend.auth;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.context.annotation.Profile;
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
 * CI-ONLY test authentication. Active only under the {@code e2e} Spring profile,
 * which is never used in production.
 *
 * <p>The black-box Python E2E suite (`tests-e2e/`) drives the real running jar over
 * HTTP, but the app requires a Google login and is fully user-scoped. Real OAuth
 * can't run headlessly in CI, so under the {@code e2e} profile this filter trusts an
 * {@code X-E2E-Auth: <email>} request header and authenticates it as a single seeded
 * test user — giving the E2E tests an authenticated, user-scoped session without OAuth.
 *
 * <p>Production never activates {@code e2e}, so this bean doesn't exist there and the
 * header is ignored. (See {@code SecurityConfig}, which also disables CSRF only under
 * this profile.)
 */
@Component
@Profile("e2e")
public class E2eTestAuthFilter extends OncePerRequestFilter {

    public static final String HEADER = "X-E2E-Auth";
    private static final String TEST_SUB = "e2e-test-user";

    private final AppUserRepository appUserRepository;

    public E2eTestAuthFilter(AppUserRepository appUserRepository) {
        this.appUserRepository = appUserRepository;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String email = request.getHeader(HEADER);
        if (email != null && !email.isBlank()) {
            AppUser user = appUserRepository.findByGoogleSub(TEST_SUB).orElseGet(() -> {
                AppUser u = new AppUser();
                u.setGoogleSub(TEST_SUB);
                u.setEmail(email);
                u.setName("E2E Test User");
                u.setRole("USER");
                return appUserRepository.save(u);
            });
            SecurityContextHolder.getContext().setAuthentication(buildAuth(user));
        }
        filterChain.doFilter(request, response);
    }

    /** Re-run on the async dispatch too, so streaming (SSE) endpoints don't get denied on
     *  completion — this per-request auth isn't stored in a session. */
    @Override
    protected boolean shouldNotFilterAsyncDispatch() {
        return false;
    }

    private OAuth2AuthenticationToken buildAuth(AppUser user) {
        OidcIdToken token = OidcIdToken.withTokenValue("e2e-token")
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
