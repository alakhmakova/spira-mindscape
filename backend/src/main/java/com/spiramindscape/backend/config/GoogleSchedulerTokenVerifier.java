package com.spiramindscape.backend.config;

import com.google.api.client.googleapis.auth.oauth2.GoogleIdToken;
import com.google.api.client.googleapis.auth.oauth2.GoogleIdTokenVerifier;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.gson.GsonFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

/**
 * {@link SchedulerTokenVerifier} on Google's own verifier: signature against Google's keys,
 * issuer, expiry, and the audience Cloud Scheduler was configured with — then the account.
 *
 * <p>With either setting missing every token is refused: an unconfigured dispatcher is closed,
 * not open.
 */
@Component
public class GoogleSchedulerTokenVerifier implements SchedulerTokenVerifier {

    private static final Logger log = LoggerFactory.getLogger(GoogleSchedulerTokenVerifier.class);

    private final GoogleIdTokenVerifier verifier;
    private final String audience;
    private final String serviceAccount;

    public GoogleSchedulerTokenVerifier(
            @Value("${spira.reminders.dispatch.audience:}") String audience,
            @Value("${spira.reminders.dispatch.service-account:}") String serviceAccount) {
        this.audience = audience.trim();
        this.serviceAccount = serviceAccount.trim();
        this.verifier = new GoogleIdTokenVerifier.Builder(new NetHttpTransport(), GsonFactory.getDefaultInstance())
                .setAudience(List.of(this.audience))
                .build();
    }

    @Override
    public Optional<String> verify(String idToken) {
        if (audience.isEmpty() || serviceAccount.isEmpty()) {
            return Optional.empty();
        }
        try {
            GoogleIdToken token = verifier.verify(idToken);
            if (token == null) {
                return Optional.empty();
            }
            GoogleIdToken.Payload payload = token.getPayload();
            if (!Boolean.TRUE.equals(payload.getEmailVerified())
                    || !serviceAccount.equalsIgnoreCase(payload.getEmail())) {
                return Optional.empty();
            }
            return Optional.of(payload.getEmail());
        } catch (Exception e) {
            log.warn("scheduler_token_unverifiable", e);
            return Optional.empty();
        }
    }
}
