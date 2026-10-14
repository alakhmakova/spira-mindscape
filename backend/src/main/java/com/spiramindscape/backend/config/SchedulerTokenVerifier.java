package com.spiramindscape.backend.config;

import java.util.Optional;

/**
 * Verifies the OIDC token Cloud Scheduler sends with each call. Returns the service account's
 * email when the token is genuine, unexpired, for our audience and from the expected account.
 */
public interface SchedulerTokenVerifier {
    Optional<String> verify(String idToken);
}
