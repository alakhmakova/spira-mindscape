package com.spiramindscape.backend.auth;

import java.util.Optional;

/**
 * Verifies a Google ID token presented by the native mobile app.
 *
 * <p>Abstracted behind an interface so the {@code POST /api/auth/google/mobile} endpoint
 * can be tested with a stub, instead of calling Google's token-info service in unit tests.
 */
public interface MobileTokenVerifier {

    /**
     * @return the verified identity if the token is valid (signature, expiry, and audience
     *         all check out); {@link Optional#empty()} otherwise. Never throws.
     */
    Optional<VerifiedGoogleUser> verify(String idToken);
}
