package com.spiramindscape.backend.push.web;

import com.spiramindscape.backend.ai.crypto.EncryptionService;
import jakarta.persistence.EntityManager;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.function.Supplier;

/**
 * The one stored VAPID pair ({@code web_push_vapid_key}, V43). Two instances starting at once may
 * both try to create it; the primary key lets exactly one insert win and both read that one back.
 * {@code persist}, never {@code merge}: a merge would quietly overwrite the winner's pair.
 *
 * <p>The private key is stored encrypted with {@link EncryptionService} — the AI provider keys'
 * key — so the database alone does not give it away.
 */
@Repository
public class VapidKeyStore {

    private final EntityManager em;
    private final TransactionTemplate tx;
    private final EncryptionService encryption;

    public VapidKeyStore(EntityManager em, PlatformTransactionManager transactions, EncryptionService encryption) {
        this.em = em;
        this.encryption = encryption;
        this.tx = transactions == null ? null : new TransactionTemplate(transactions);
        if (tx != null) {
            tx.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        }
    }

    /** The stored pair as {public, private}; when there is none, {@code fresh}'s pair is stored first. */
    public String[] loadOrCreate(Supplier<String[]> fresh) {
        String[] stored = load();
        if (stored != null) {
            return stored;
        }
        String[] pair = fresh.get();
        try {
            tx.executeWithoutResult(s -> {
                em.persist(new VapidKey(pair[0], encryption.encrypt(pair[1])));
                em.flush();
            });
        } catch (RuntimeException insertFailed) {
            // Most likely another instance stored its pair first, and that one is the pair. The
            // exception arrives untranslated here (Hibernate's, or Spring's from the commit), so
            // the stored row is the test rather than the exception's type.
            String[] winner = load();
            if (winner == null) {
                throw insertFailed;
            }
            return winner;
        }
        return load();
    }

    private String[] load() {
        return tx.execute(s -> {
            VapidKey key = em.find(VapidKey.class, VapidKey.ONLY);
            return key == null ? null : new String[] {key.getPublicKey(), encryption.decrypt(key.getPrivateKey())};
        });
    }
}
