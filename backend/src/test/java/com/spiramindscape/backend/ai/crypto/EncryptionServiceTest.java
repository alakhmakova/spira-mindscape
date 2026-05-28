package com.spiramindscape.backend.ai.crypto;

import org.junit.jupiter.api.Test;

import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class EncryptionServiceTest {

    // 32-byte test key (same value as application-test.properties)
    private static final String TEST_KEY = "c3BpcmEtdGVzdC1lbmNyeXB0aW9uLWtleS0zMmJ5dCE=";

    private final EncryptionService service = new EncryptionService(TEST_KEY);

    @Test
    void encryptThenDecryptReturnsOriginalPlaintext() {
        String original = "sk-ant-api03-super-secret-key-1234";
        String encrypted = service.encrypt(original);
        String decrypted = service.decrypt(encrypted);
        assertThat(decrypted).isEqualTo(original);
    }

    @Test
    void sameInputProducesDifferentCiphertextsEachTime() {
        String plaintext = "my-api-key";
        String first = service.encrypt(plaintext);
        String second = service.encrypt(plaintext);
        // Different random IVs → different ciphertexts
        assertThat(first).isNotEqualTo(second);
        // But both decrypt to the same value
        assertThat(service.decrypt(first)).isEqualTo(plaintext);
        assertThat(service.decrypt(second)).isEqualTo(plaintext);
    }

    @Test
    void encryptedValueIsBase64() {
        String encrypted = service.encrypt("some-key");
        // Should not throw
        byte[] bytes = Base64.getDecoder().decode(encrypted);
        // IV (12 bytes) + ciphertext + GCM tag (16 bytes) ≥ 28 bytes
        assertThat(bytes.length).isGreaterThanOrEqualTo(28);
    }

    @Test
    void decryptTamperedCiphertextThrows() {
        String encrypted = service.encrypt("my-api-key");
        // Corrupt the last byte of the Base64-encoded value
        String tampered = encrypted.substring(0, encrypted.length() - 2) + "ZZ";
        assertThatThrownBy(() -> service.decrypt(tampered))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Decryption failed");
    }

    @Test
    void wrongKeySizeThrows() {
        // 16-byte key (AES-128) is not accepted — we enforce AES-256 (32 bytes)
        String shortKey = Base64.getEncoder().encodeToString("only-sixteen-byt".getBytes());
        assertThatThrownBy(() -> new EncryptionService(shortKey))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("32 bytes");
    }
}
