package com.spiramindscape.backend.push.web;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.GeneralSecurityException;
import java.security.KeyPair;
import java.security.interfaces.ECPrivateKey;
import java.security.interfaces.ECPublicKey;
import java.util.Properties;

/**
 * The VAPID key pair and contact.
 *
 * <p>Where the pair comes from, first match wins:
 * <ol>
 *   <li><b>The environment</b> ({@code SPIRA_WEBPUSH_PUBLIC_KEY} / {@code _PRIVATE_KEY}) — for a
 *       deployment that keeps its pair in Secret Manager.</li>
 *   <li><b>The database</b> ({@code web_push_vapid_key}, V43) — created on first use and reused
 *       by every instance and every later start. This is what makes push work in production with
 *       no setup: it used to be off until the owner generated keys and put them in Secret Manager
 *       by hand, and the disk is no place for them there, since Cloud Run's is per instance and
 *       short-lived and the key would change under every subscription.</li>
 * </ol>
 *
 * <p>When the database has no pair yet and a pair from the old local key file
 * ({@code ~/.spira/vapid-keys.properties}) exists, that pair is the one stored, so browsers
 * already subscribed on a local run keep receiving pushes.
 *
 * <p>Read lazily, not in the constructor: a database that is briefly unreachable leaves push off
 * for that call and is tried again on the next, instead of failing the start.
 */
@Component
public class WebPushProperties {

    private static final Logger log = LoggerFactory.getLogger(WebPushProperties.class);

    private final String envPublicKey;
    private final String envPrivateKey;
    private final String subject;
    private final VapidKeyStore store;
    private final Path legacyKeyFile;

    private volatile String[] pair;

    public WebPushProperties(
            @Value("${spira.webpush.public-key:}") String publicKey,
            @Value("${spira.webpush.private-key:}") String privateKey,
            @Value("${spira.webpush.subject:mailto:noreply@spira.app}") String subject,
            VapidKeyStore store,
            @Value("${spira.webpush.key-file:${user.home}/.spira/vapid-keys.properties}") String keyFile) {
        this.envPublicKey = publicKey.trim();
        this.envPrivateKey = privateKey.trim();
        this.subject = subject.trim();
        this.store = store;
        this.legacyKeyFile = Path.of(keyFile);
    }

    /** {public, private}, or null when no pair can be had right now (push stays off). */
    private String[] pair() {
        String[] current = pair;
        if (current != null) {
            return current;
        }
        synchronized (this) {
            if (pair == null) {
                pair = resolve();
            }
            return pair;
        }
    }

    private String[] resolve() {
        if (!envPublicKey.isEmpty() && !envPrivateKey.isEmpty()) {
            return new String[] {envPublicKey, envPrivateKey};
        }
        try {
            String[] stored = store.loadOrCreate(this::freshPair);
            return stored != null && !stored[0].isBlank() && !stored[1].isBlank() ? stored : null;
        } catch (RuntimeException e) {
            log.warn("web_push_keys_unavailable reason=database", e);
            return null;
        }
    }

    private String[] freshPair() {
        String[] legacy = legacyPair(legacyKeyFile);
        if (legacy != null) {
            log.info("web_push_keys_created source=local_file");
            return legacy;
        }
        try {
            KeyPair kp = WebPushCrypto.newKeyPair();
            log.info("web_push_keys_created source=generated");
            return new String[] {
                    WebPushCrypto.encode(WebPushCrypto.uncompressed((ECPublicKey) kp.getPublic())),
                    WebPushCrypto.encode(scalar((ECPrivateKey) kp.getPrivate())),
            };
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("cannot generate a VAPID key pair", e);
        }
    }

    /** The pair from the pre-V43 local key file, or null if there is none or it is unusable. */
    static String[] legacyPair(Path file) {
        if (!Files.isReadable(file)) {
            return null;
        }
        try {
            Properties stored = new Properties();
            try (Reader in = Files.newBufferedReader(file)) {
                stored.load(in);
            }
            String pub = stored.getProperty("public-key", "").trim();
            String priv = stored.getProperty("private-key", "").trim();
            WebPushCrypto.publicKey(WebPushCrypto.decode(pub));
            WebPushCrypto.privateKey(WebPushCrypto.decode(priv));
            return new String[] {pub, priv};
        } catch (IOException | GeneralSecurityException | IllegalArgumentException e) {
            log.warn("web_push_local_keys_unusable path={}", file, e);
            return null;
        }
    }

    private static byte[] scalar(ECPrivateKey key) {
        byte[] raw = key.getS().toByteArray();
        byte[] out = new byte[32];
        int copy = Math.min(raw.length, 32);
        System.arraycopy(raw, raw.length - copy, out, 32 - copy, copy);
        return out;
    }

    public boolean enabled() {
        return pair() != null;
    }

    public String publicKey() {
        String[] p = pair();
        return p == null ? "" : p[0];
    }

    public String subject() {
        return subject;
    }

    public ECPrivateKey signingKey() throws GeneralSecurityException {
        String[] p = pair();
        if (p == null) {
            throw new GeneralSecurityException("no VAPID key pair");
        }
        return WebPushCrypto.privateKey(WebPushCrypto.decode(p[1]));
    }
}
