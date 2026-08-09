package com.spiramindscape.backend.auth;

import com.spiramindscape.backend.logging.AuthAuditLogger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link MobileAuthController} — the native mobile sign-in endpoint.
 *
 * <p>The Google token verification is stubbed (see {@link MobileTokenVerifier}); these tests
 * cover the endpoint's own logic: rejecting bad input/tokens, reusing find-or-create, and
 * establishing an {@link AppUserOidcUser} session so the rest of the app treats a mobile
 * login exactly like a web login.
 */
@ExtendWith(MockitoExtension.class)
class MobileAuthControllerTest {

    @Mock
    private MobileTokenVerifier tokenVerifier;

    @Mock
    private AppUserService appUserService;

    /** Real, not mocked: it only writes log lines, and a real one keeps the test honest. */
    private final AuthAuditLogger authAuditLogger = new AuthAuditLogger();

    private MobileAuthController controller() {
        return new MobileAuthController(tokenVerifier, appUserService, authAuditLogger);
    }

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("Missing/blank idToken → 400 and no verification attempted")
    void blankTokenIsBadRequest() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();

        ResponseEntity<UserDto> result = controller().mobileLogin(
                new MobileAuthController.MobileLoginRequest("  "), request, response);

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        verify(tokenVerifier, never()).verify(any());
        assertThat(request.getSession(false)).isNull();
    }

    @Test
    @DisplayName("Invalid/unverifiable token → 401 and no user created")
    void invalidTokenIsUnauthorized() {
        when(tokenVerifier.verify("bad-token")).thenReturn(Optional.empty());
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();

        ResponseEntity<UserDto> result = controller().mobileLogin(
                new MobileAuthController.MobileLoginRequest("bad-token"), request, response);

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        verify(appUserService, never()).findOrCreateFromGoogle(any(), any(), any(), any());
        assertThat(request.getSession(false)).isNull();
    }

    @Test
    @DisplayName("Valid token → 200 with user, find-or-create called, and an AppUserOidcUser session is established")
    void validTokenLogsInAndStartsSession() {
        VerifiedGoogleUser google =
                new VerifiedGoogleUser("sub-123", "alice@example.com", "Alice", "https://pic/alice");
        when(tokenVerifier.verify("good-token")).thenReturn(Optional.of(google));

        AppUser user = new AppUser();
        user.setId(7L);
        user.setGoogleSub("sub-123");
        user.setEmail("alice@example.com");
        user.setName("Alice");
        user.setPictureUrl("https://pic/alice");
        when(appUserService.findOrCreateFromGoogle("sub-123", "alice@example.com", "Alice", "https://pic/alice"))
                .thenReturn(user);

        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();

        ResponseEntity<UserDto> result = controller().mobileLogin(
                new MobileAuthController.MobileLoginRequest("good-token"), request, response);

        // 200 with the user DTO (same shape /api/auth/me returns)
        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(result.getBody()).isNotNull();
        assertThat(result.getBody().id()).isEqualTo(7L);
        assertThat(result.getBody().email()).isEqualTo("alice@example.com");

        verify(appUserService).findOrCreateFromGoogle("sub-123", "alice@example.com", "Alice", "https://pic/alice");

        // A session was created and holds a SecurityContext whose principal is our AppUser,
        // wrapped as AppUserOidcUser — the exact principal CurrentUserProvider expects.
        assertThat(request.getSession(false)).isNotNull();
        SecurityContext stored = (SecurityContext) request.getSession(false)
                .getAttribute(HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY);
        assertThat(stored).isNotNull();
        assertThat(stored.getAuthentication()).isNotNull();
        assertThat(stored.getAuthentication().getPrincipal()).isInstanceOf(AppUserOidcUser.class);
        AppUserOidcUser principal = (AppUserOidcUser) stored.getAuthentication().getPrincipal();
        assertThat(principal.getAppUser()).isSameAs(user);
        assertThat(principal.getAppUser().getEmail()).isEqualTo("alice@example.com");
    }

    @Test
    @DisplayName("Session fixation: a pre-existing session id is rotated on login")
    void existingSessionIdIsRotatedOnLogin() {
        VerifiedGoogleUser google = new VerifiedGoogleUser("sub-rot", "r@example.com", "R", null);
        when(tokenVerifier.verify("good-token")).thenReturn(Optional.of(google));
        AppUser user = new AppUser();
        user.setId(1L);
        user.setEmail("r@example.com");
        when(appUserService.findOrCreateFromGoogle("sub-rot", "r@example.com", "R", null)).thenReturn(user);

        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();
        // The caller presents an existing session (the fixation vector).
        String originalId = request.getSession(true).getId();

        controller().mobileLogin(new MobileAuthController.MobileLoginRequest("good-token"), request, response);

        assertThat(request.getSession(false)).isNotNull();
        assertThat(request.getSession(false).getId()).isNotEqualTo(originalId);
    }

    @Test
    @DisplayName("Concurrent first login (unique violation) recovers by loading the row the other request created → 200")
    void concurrentInsertRecoversTo200() {
        VerifiedGoogleUser google = new VerifiedGoogleUser("sub-race", "race@example.com", "Race", null);
        when(tokenVerifier.verify("good-token")).thenReturn(Optional.of(google));
        when(appUserService.findOrCreateFromGoogle("sub-race", "race@example.com", "Race", null))
                .thenThrow(new DataIntegrityViolationException("duplicate google_sub"));
        AppUser existing = new AppUser();
        existing.setId(5L);
        existing.setEmail("race@example.com");
        when(appUserService.findByGoogleSub("sub-race")).thenReturn(Optional.of(existing));

        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();

        ResponseEntity<UserDto> result = controller().mobileLogin(
                new MobileAuthController.MobileLoginRequest("good-token"), request, response);

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(result.getBody()).isNotNull();
        assertThat(result.getBody().email()).isEqualTo("race@example.com");
        // A session is still established for the recovered user.
        assertThat(request.getSession(false)).isNotNull();
    }

    @Test
    @DisplayName("Email owned by a different Google account (unique violation, no matching sub) → 409 and no session")
    void emailConflictReturns409() {
        VerifiedGoogleUser google = new VerifiedGoogleUser("sub-new", "taken@example.com", "New", null);
        when(tokenVerifier.verify("good-token")).thenReturn(Optional.of(google));
        when(appUserService.findOrCreateFromGoogle("sub-new", "taken@example.com", "New", null))
                .thenThrow(new DataIntegrityViolationException("duplicate email"));
        when(appUserService.findByGoogleSub("sub-new")).thenReturn(Optional.empty());

        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();

        ResponseEntity<UserDto> result = controller().mobileLogin(
                new MobileAuthController.MobileLoginRequest("good-token"), request, response);

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(request.getSession(false)).isNull();
    }
}
