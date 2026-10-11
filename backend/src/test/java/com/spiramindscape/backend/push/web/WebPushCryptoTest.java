package com.spiramindscape.backend.push.web;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.Signature;
import java.security.interfaces.ECPrivateKey;
import java.security.interfaces.ECPublicKey;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * {@link WebPushCrypto} against the standards' own worked examples — the only way to know a
 * browser will be able to read what we send without a browser in the test.
 */
class WebPushCryptoTest {

    /** RFC 8291, Appendix A. */
    private static final String PLAINTEXT = "When I grow up, I want to be a watermelon";
    private static final String AS_PRIVATE = "yfWPiYE-n46HLnH0KqZOF1fJJU3MYrct3AELtAQ-oRw";
    private static final String AS_PUBLIC =
            "BP4z9KsN6nGRTbVYI_c7VJSPQTBtkgcy27mlmlMoZIIgDll6e3vCYLocInmYWAmS6TlzAC8wEqKK6PBru3jl7A8";
    private static final String UA_PUBLIC =
            "BCVxsr7N_eNgVRqvHtD0zTZsEc6-VV-JvLexhqUzORcxaOzi6-AYWXvTBHm4bjyPjs7Vd8pZGH6SRpkNtoIAiw4";
    private static final String SALT = "DGv6ra1nlYgDCS1FRnbzlw";
    private static final String AUTH_SECRET = "BTBZMqHH6r4Tts7J_aSIgg";
    private static final String BODY =
            "DGv6ra1nlYgDCS1FRnbzlwAAEABBBP4z9KsN6nGRTbVYI_c7VJSPQTBtkgcy27mlmlMoZIIgDll6e3vCYLocInmYWAmS6TlzAC8wEqKK6PBru3jl7A_yl95bQpu6cVPTpK4Mqgkf1CXztLVBSt2Ks3oZwbuwXPXLWyouBWLVWGNWQexSgSxsj_Qulcy4a-fN";

    @Test
    @DisplayName("Encrypts the RFC 8291 example to exactly the RFC's bytes")
    void rfc8291Vector() throws Exception {
        ECPrivateKey asPrivate = WebPushCrypto.privateKey(WebPushCrypto.decode(AS_PRIVATE));
        ECPublicKey asPublic = WebPushCrypto.publicKey(WebPushCrypto.decode(AS_PUBLIC));
        byte[] body = WebPushCrypto.encrypt(
                PLAINTEXT.getBytes(StandardCharsets.UTF_8),
                WebPushCrypto.decode(UA_PUBLIC),
                WebPushCrypto.decode(AUTH_SECRET),
                new KeyPair(asPublic, asPrivate),
                WebPushCrypto.decode(SALT));

        assertThat(WebPushCrypto.encode(body)).isEqualTo(BODY);
    }

    @Test
    @DisplayName("A key survives the round trip through its 65-byte form")
    void keyRoundTrip() throws Exception {
        ECPublicKey key = WebPushCrypto.publicKey(WebPushCrypto.decode(AS_PUBLIC));
        assertThat(WebPushCrypto.encode(WebPushCrypto.uncompressed(key))).isEqualTo(AS_PUBLIC);
    }

    @Test
    @DisplayName("A VAPID token is an ES256 JWT that verifies against the public key")
    void vapidJwt() throws Exception {
        KeyPair pair = WebPushCrypto.newKeyPair();
        String jwt = WebPushCrypto.vapidJwt("https://fcm.googleapis.com", "mailto:owner@example.com",
                1_900_000_000L, (ECPrivateKey) pair.getPrivate());

        String[] parts = jwt.split("\\.");
        assertThat(parts).hasSize(3);
        assertThat(new String(WebPushCrypto.decode(parts[0]), StandardCharsets.UTF_8))
                .isEqualTo("{\"typ\":\"JWT\",\"alg\":\"ES256\"}");
        assertThat(new String(WebPushCrypto.decode(parts[1]), StandardCharsets.UTF_8))
                .isEqualTo("{\"aud\":\"https://fcm.googleapis.com\",\"exp\":1900000000,"
                        + "\"sub\":\"mailto:owner@example.com\"}");

        Signature verifier = Signature.getInstance("SHA256withECDSAinP1363Format");
        verifier.initVerify(pair.getPublic());
        verifier.update((parts[0] + "." + parts[1]).getBytes(StandardCharsets.US_ASCII));
        assertThat(WebPushCrypto.decode(parts[2])).hasSize(64);
        assertThat(verifier.verify(WebPushCrypto.decode(parts[2]))).isTrue();
    }

    @Test
    @DisplayName("Anything but an uncompressed P-256 point is refused")
    void refusesBadKeys() {
        assertThatThrownBy(() -> WebPushCrypto.publicKey(new byte[64]))
                .hasMessageContaining("uncompressed");
        assertThatThrownBy(() -> WebPushCrypto.privateKey(new byte[31]))
                .hasMessageContaining("32-byte");
    }
}
