package com.spiramindscape.backend.logging;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Privacy-safe audit of sign-in lifecycle events (OWASP A09 — security logging).
 *
 * <p>Before this, nothing recorded a successful login, a logout, or a rejected mobile
 * token, so "was this account signed into, and from where" had no answer at all. It
 * follows the same contract as {@link com.spiramindscape.backend.ai.safety.AbuseAuditLogger}:
 * a dedicated logger name, a {@code snake_case_event} first token and {@code key=value}
 * pairs so the lines are greppable in Logs Explorer — and the numeric user id, <b>never</b>
 * the email, the Google subject, or any token value.
 *
 * <p>Deliberately small. These are lifecycle facts worth an alert or an investigation,
 * not usage telemetry.
 */
@Component
public class AuthAuditLogger {

    private static final Logger log = LoggerFactory.getLogger("security.auth");

    /** How the user signed in — {@code web} is the browser OAuth redirect, {@code mobile} the Android app. */
    public enum Method { WEB, MOBILE }

    public void signInSucceeded(Method method, Long userId) {
        log.info("auth_signin_success method={} userId={}", name(method), userId == null ? "?" : userId);
    }

    /**
     * A sign-in that was refused. {@code reason} must be a fixed code
     * ({@code token_invalid}, {@code missing_token}, {@code account_conflict}) — never a
     * message built from user input. WARN, because a burst of these is worth alerting on.
     */
    public void signInRejected(Method method, String reason) {
        log.warn("auth_signin_rejected method={} reason={}", name(method), reason);
    }

    public void signedOut(Long userId) {
        log.info("auth_logout userId={}", userId == null ? "?" : userId);
    }

    private static String name(Method method) {
        return method == null ? "?" : method.name().toLowerCase(java.util.Locale.ROOT);
    }
}
