package com.spiramindscape.backend.push.web;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.security.GeneralSecurityException;
import java.security.KeyPair;
import java.security.interfaces.ECPrivateKey;
import java.security.interfaces.ECPublicKey;
import java.util.Properties;

/**
 * The VAPID key pair and contact.
 *
 * <p><b>Production</b>: both keys come from the environment (the private one from Secret Manager)
 * and are never in the repo; with none configured, Web Push is off and the app says so.
 *
 * <p><b>Locally</b> ({@code spira.webpush.auto-generate-keys=true}, set by the {@code local}
 * profile): with no keys in the environment, a pair is generated on first start and kept in
 * {@code ~/.spira/vapid-keys.properties}, outside the repository, readable by the owner only.
 * The same pair is reused on every later start, because a browser subscribed under one public key
 * stops receiving pushes the moment it changes. Push therefore works on a local run with no setup
 * at all — it used to be silently off there, which is how a test on a phone got nothing.
 * Never in production: Cloud Run's disk is per instance and short-lived, so the key would change
 * under every subscription.
 */
@Component
public class WebPushProperties {

    private static final Logger log = LoggerFactory.getLogger(WebPushProperties.class);

    private final String publicKey;
    private final String privateKey;
    private final String subject;

    public WebPushProperties(
            @Value("${spira.webpush.public-key:}") String publicKey,
            @Value("${spira.webpush.private-key:}") String privateKey,
            @Value("${spira.webpush.subject:mailto:noreply@spira.app}") String subject,
            @Value("${spira.webpush.auto-generate-keys:false}") boolean autoGenerate,
            @Value("${spira.webpush.key-file:${user.home}/.spira/vapid-keys.properties}") String keyFile) {
        String pub = publicKey.trim();
        String priv = privateKey.trim();
        if ((pub.isEmpty() || priv.isEmpty()) && autoGenerate) {
            String[] pair = localPair(Path.of(keyFile));
            if (pair != null) {
                pub = pair[0];
                priv = pair[1];
            }
        }
        this.publicKey = pub;
        this.privateKey = priv;
        this.subject = subject.trim();
    }

    /** The stored local pair, or a new one written there. Null if neither works (push stays off). */
    static String[] localPair(Path file) {
        try {
            if (Files.isReadable(file)) {
                Properties stored = new Properties();
                try (Reader in = Files.newBufferedReader(file)) {
                    stored.load(in);
                }
                String pub = stored.getProperty("public-key", "").trim();
                String priv = stored.getProperty("private-key", "").trim();
                WebPushCrypto.publicKey(WebPushCrypto.decode(pub));
                WebPushCrypto.privateKey(WebPushCrypto.decode(priv));
                return new String[] {pub, priv};
            }
            KeyPair pair = WebPushCrypto.newKeyPair();
            String pub = WebPushCrypto.encode(WebPushCrypto.uncompressed((ECPublicKey) pair.getPublic()));
            String priv = WebPushCrypto.encode(scalar((ECPrivateKey) pair.getPrivate()));
            Files.createDirectories(file.getParent());
            Properties out = new Properties();
            out.setProperty("public-key", pub);
            out.setProperty("private-key", priv);
            try (Writer w = Files.newBufferedWriter(file)) {
                out.store(w, "Spira local Web Push (VAPID) keys - local development only, never commit");
            }
            try {
                Files.setPosixFilePermissions(file, PosixFilePermissions.fromString("rw-------"));
            } catch (UnsupportedOperationException | IOException ignored) {
                // Windows: the file sits in the user's own profile folder, which is private already.
            }
            log.info("web_push_local_keys_created path={}", file);
            return new String[] {pub, priv};
        } catch (IOException | GeneralSecurityException | IllegalArgumentException e) {
            log.warn("web_push_local_keys_unavailable path={}", file, e);
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
