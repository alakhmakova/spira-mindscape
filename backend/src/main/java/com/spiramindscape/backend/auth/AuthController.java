package com.spiramindscape.backend.auth;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.security.Principal;

/**
 * REST endpoints for authentication state.
 *
 * <ul>
 *   <li>{@code GET /api/auth/me} — returns the signed-in user's profile, or {@code 401} if anonymous.</li>
 *   <li>{@code POST /api/auth/logout} is handled entirely by Spring Security's logout filter
 *       (configured in {@link com.spiramindscape.backend.config.SecurityConfig}) and returns {@code 204}.</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final CurrentUserProvider currentUserProvider;

    public AuthController(CurrentUserProvider currentUserProvider) {
        this.currentUserProvider = currentUserProvider;
    }

    /**
     * Returns the current user's profile.
     * Returns {@code 401} when the session is anonymous (the SecurityConfig permits this
     * path, so the check is done here rather than by the filter chain).
     */
    @GetMapping("/me")
    public ResponseEntity<UserDto> me(Principal principal) {
        if (principal == null) {
            return ResponseEntity.status(401).build();
        }
        AppUser user = currentUserProvider.getCurrentUser();
        return ResponseEntity.ok(UserDto.from(user));
    }
}
