package com.spiramindscape.backend.push.web;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;

/** The server's one VAPID key pair (V43); {@link VapidKeyStore} is the only thing that touches it. */
@Entity
@Table(name = "web_push_vapid_key")
@Getter
@NoArgsConstructor
public class VapidKey {

    /** Always {@link #ONLY}: there is one pair. */
    public static final short ONLY = 1;

    @Id
    private Short id;

    @Column(name = "public_key", nullable = false, length = 128)
    private String publicKey;

    /** Encrypted with {@code EncryptionService}; never the raw key. */
    @Column(name = "private_key", nullable = false, length = 255)
    private String privateKey;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    VapidKey(String publicKey, String encryptedPrivateKey) {
        this.id = ONLY;
        this.publicKey = publicKey;
        this.privateKey = encryptedPrivateKey;
        this.createdAt = Instant.now();
    }
}
