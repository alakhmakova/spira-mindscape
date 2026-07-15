package com.spiramindscape.backend.auth;

import com.google.api.client.googleapis.auth.oauth2.GoogleIdToken;
import com.google.api.client.googleapis.auth.oauth2.GoogleIdTokenVerifier;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.gson.GsonFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Real {@link MobileTokenVerifier} backed by Google's {@link GoogleIdTokenVerifier}.
 *
 * <p>The Android app signs in with Google and requests an ID token whose audience is the
 * project's <em>Web</em> OAuth client ID (the same one the website uses). This verifier
 * checks the token's signature against Google's public keys and that its audience is one we
 * accept — by default the web client ID from the {@code GOOGLE_CLIENT_ID} env var (the same
 * one the web OAuth login uses), plus any extras in {@code app.auth.mobile.extra-audiences}
 * (comma-separated).
 */
@Component
public class GoogleMobileTokenVerifier implements MobileTokenVerifier {

    private static final Logger log = LoggerFactory.getLogger(GoogleMobileTokenVerifier.class);

    private final GoogleIdTokenVerifier verifier;

    public GoogleMobileTokenVerifier(
            @Value("${GOOGLE_CLIENT_ID:}") String webClientId,
            @Value("${app.auth.mobile.extra-audiences:}") String extraAudiences) {

        List<String> audiences = new ArrayList<>();
        if (StringUtils.hasText(webClientId)) {
            audiences.add(webClientId.trim());
        }
        for (String extra : extraAudiences.split(",")) {
            if (StringUtils.hasText(extra)) {
                audiences.add(extra.trim());
            }
        }

        this.verifier = new GoogleIdTokenVerifier.Builder(new NetHttpTransport(), GsonFactory.getDefaultInstance())
                .setAudience(audiences)
                .build();

        if (audiences.isEmpty()) {
            // No client ID configured (e.g. the local dev profile): mobile sign-in cannot
            // work until GOOGLE_CLIENT_ID is set, and every token will be rejected. Log once
            // so this is diagnosable rather than a silent 401.
            log.warn("No Google audience configured for mobile sign-in; "
                    + "POST /api/auth/google/mobile will reject all tokens until "
                    + "GOOGLE_CLIENT_ID (web client ID) is set.");
        }
    }

    @Override
    public Optional<VerifiedGoogleUser> verify(String idToken) {
        try {
            GoogleIdToken token = verifier.verify(idToken);
            if (token == null) {
                return Optional.empty(); // bad signature, expired, or wrong audience
            }
            GoogleIdToken.Payload payload = token.getPayload();
            // Only accept verified emails: an unverified email could be spoofed on a
            // provider that doesn't confirm ownership.
            if (payload.getEmailVerified() == null || !payload.getEmailVerified()) {
                return Optional.empty();
            }
            return Optional.of(new VerifiedGoogleUser(
                    payload.getSubject(),
                    payload.getEmail(),
                    (String) payload.get("name"),
                    (String) payload.get("picture")));
        } catch (Exception e) {
            // Malformed token, network/cert issue, etc. — treat as unauthenticated.
            log.warn("Mobile Google ID token verification failed: {}", e.getMessage());
            return Optional.empty();
        }
    }
}
