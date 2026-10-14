package com.spiramindscape.backend.push.web;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.security.GeneralSecurityException;
import java.security.interfaces.ECPrivateKey;

/**
 * The VAPID key pair and contact. The private key comes from Secret Manager in production and is
 * never in the repo; with no keys configured Web Push is simply off and the app falls back to the
 * in-app "decide now" page. Generate a pair with {@code npx web-push generate-vapid-keys}: both
 * halves are base64url, the public one 65 bytes, the private one 32.
 */
@Component
public class WebPushProperties {

    private final String publicKey;
    private final String privateKey;
    private final String subject;

    public WebPushProperties(
            @Value("${spira.webpush.public-key:}") String publicKey,
            @Value("${spira.webpush.private-key:}") String privateKey,
            @Value("${spira.webpush.subject:mailto:noreply@spira.app}") String subject) {
        this.publicKey = publicKey.trim();
        this.privateKey = privateKey.trim();
        this.subject = subject.trim();
    }

    public boolean enabled() {
        return !publicKey.isEmpty() && !privateKey.isEmpty();
    }

    public String publicKey() {
        return publicKey;
    }

    public String subject() {
        return subject;
    }

    public ECPrivateKey signingKey() throws GeneralSecurityException {
        return WebPushCrypto.privateKey(WebPushCrypto.decode(privateKey));
    }
}
