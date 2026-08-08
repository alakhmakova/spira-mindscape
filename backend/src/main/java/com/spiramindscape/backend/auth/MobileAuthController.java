package com.spiramindscape.backend.auth;

import com.spiramindscape.backend.logging.AuthAuditLogger;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.core.oidc.OidcIdToken;
import org.springframework.security.oauth2.core.oidc.OidcUserInfo;
import org.springframework.security.oauth2.core.oidc.user.DefaultOidcUser;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Native mobile sign-in.
 *
 * <p>The browser OAuth redirect flow doesn't fit a native app, so the Android app signs in
 * with Google on-device, gets a Google <em>ID token</em>, and posts it here. We verify the
 * token, find-or-create the {@link AppUser} (the same logic the web login uses), and then
 * establish the <em>same</em> server-side session — storing an {@link AppUserOidcUser}
 * principal so {@code CurrentUserProvider}, {@code /graphql}, and {@code /api/auth/me} all
 * work identically to a web login. The app keeps the {@code SESSION} cookie (via its cookie
 * jar) and reuses it for every request, exactly like the browser.
 *
 * <p>This endpoint is {@code permitAll} and CSRF-exempt (the caller has no session/CSRF token
 * yet) — see {@code SecurityConfig}. Everything else stays unchanged.
 */
@RestController
@RequestMapping("/api/auth")
public class MobileAuthController {

    private final MobileTokenVerifier tokenVerifier;
    private final AppUserService appUserService;
    private final AuthAuditLogger authAuditLogger;

    /**
     * Writes the authenticated context into the (PostgreSQL-backed) HTTP session, which
     * emits the {@code SESSION} cookie. This is the standard programmatic-login repository;
     * on later requests Spring Security reads the principal back from the same session.
     */
    private final SecurityContextRepository securityContextRepository =
            new HttpSessionSecurityContextRepository();

    public MobileAuthController(MobileTokenVerifier tokenVerifier, AppUserService appUserService,
                                AuthAuditLogger authAuditLogger) {
        this.tokenVerifier = tokenVerifier;
        this.appUserService = appUserService;
        this.authAuditLogger = authAuditLogger;
    }

    /** Request body: the Google ID token obtained on the device. */
    public record MobileLoginRequest(String idToken) {}

    @PostMapping("/google/mobile")
    public ResponseEntity<UserDto> mobileLogin(@RequestBody(required = false) MobileLoginRequest body,
                                               HttpServletRequest request,
                                               HttpServletResponse response) {
        if (body == null || body.idToken() == null || body.idToken().isBlank()) {
            authAuditLogger.signInRejected(AuthAuditLogger.Method.MOBILE, "missing_token");
            return ResponseEntity.badRequest().build();
        }

        Optional<VerifiedGoogleUser> verified = tokenVerifier.verify(body.idToken());
        if (verified.isEmpty()) {
            authAuditLogger.signInRejected(AuthAuditLogger.Method.MOBILE, "token_invalid");
            return ResponseEntity.status(401).build();
        }

        VerifiedGoogleUser google = verified.get();
        AppUser user;
        try {
            user = appUserService.findOrCreateFromGoogle(
                    google.sub(), google.email(), google.name(), google.pictureUrl());
        } catch (DataIntegrityViolationException e) {
            // Two things can trip the unique(google_sub)/unique(email) constraints:
            //  1) A concurrent first-time login for the SAME account — the other request
            //     won the insert; recover by loading the row it created.
            //  2) The email now belongs to a DIFFERENT Google account (email was transferred)
            //     — a genuine conflict we can't resolve, so return 409 instead of a 500.
            user = appUserService.findByGoogleSub(google.sub()).orElse(null);
            if (user == null) {
                authAuditLogger.signInRejected(AuthAuditLogger.Method.MOBILE, "account_conflict");
                return ResponseEntity.status(HttpStatus.CONFLICT).build();
            }
        }

        establishSession(google, user, request, response);
        authAuditLogger.signInSucceeded(AuthAuditLogger.Method.MOBILE, user.getId());
        return ResponseEntity.ok(UserDto.from(user));
    }

    /**
     * Builds the same principal type the web OAuth login stores ({@link AppUserOidcUser}
     * wrapping an OIDC user) and saves it to the session, so downstream code that expects an
     * {@code AppUserOidcUser} principal (e.g. {@code CurrentUserProvider}) is unaffected.
     */
    private void establishSession(VerifiedGoogleUser google, AppUser user,
                                  HttpServletRequest request, HttpServletResponse response) {
        Map<String, Object> claims = new HashMap<>();
        claims.put("sub", google.sub());
        claims.put("email", google.email());
        if (google.name() != null) {
            claims.put("name", google.name());
        }
        if (google.pictureUrl() != null) {
            claims.put("picture", google.pictureUrl());
        }

        // NOTE: this OidcIdToken is synthetic — its value ("mobile-<sub>") is NOT a real
        // Google token. We only build it because AppUserOidcUser (and CurrentUserProvider)
        // expect an OidcUser principal; nothing reads the token value back (Drive/token
        // refresh use the separately-stored encRefreshToken). Do not treat it as a real token.
        OidcIdToken idToken = new OidcIdToken(
                "mobile-" + google.sub(), Instant.now(), Instant.now().plusSeconds(3600), claims);
        DefaultOidcUser oidcUser = new DefaultOidcUser(
                List.of(new SimpleGrantedAuthority("ROLE_USER")), idToken, new OidcUserInfo(claims));
        AppUserOidcUser principal = new AppUserOidcUser(oidcUser, user);

        OAuth2AuthenticationToken authentication =
                new OAuth2AuthenticationToken(principal, principal.getAuthorities(), "google");

        // Session-fixation protection: if the caller already carried a session, rotate its id
        // so a pre-existing (possibly attacker-known) session can't be promoted to an
        // authenticated one. The browser OAuth login gets this automatically via Spring's
        // session-authentication strategy; a programmatic login must do it explicitly.
        if (request.getSession(false) != null) {
            request.changeSessionId();
        }

        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(authentication);
        SecurityContextHolder.setContext(context);
        securityContextRepository.saveContext(context, request, response);
    }
}
