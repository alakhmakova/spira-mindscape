package com.spiramindscape.backend.push.web;

import com.spiramindscape.backend.ai.crypto.EncryptionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The stored pair: one row, created once, its private key encrypted at rest. A second instance that
 * arrives with its own fresh pair after the first has stored one gets the first one back.
 */
@SpringBootTest
@ActiveProfiles("test")
class VapidKeyStoreIntegrationTest {

    @Autowired private VapidKeyStore store;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private EncryptionService encryption;

    @BeforeEach
    void empty() {
        jdbc.update("DELETE FROM web_push_vapid_key");
    }

    @Test
    @DisplayName("The first pair stored is the pair; a later one is ignored")
    void firstPairWins() {
        assertThat(store.loadOrCreate(() -> new String[] {"pub-1", "priv-1"})).containsExactly("pub-1", "priv-1");
        assertThat(store.loadOrCreate(() -> new String[] {"pub-2", "priv-2"})).containsExactly("pub-1", "priv-1");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM web_push_vapid_key", Integer.class)).isEqualTo(1);
    }

    @Test
    @DisplayName("The private key is never stored as it is")
    void privateKeyEncryptedAtRest() {
        store.loadOrCreate(() -> new String[] {"pub", "the-private-key"});
        String atRest = jdbc.queryForObject("SELECT private_key FROM web_push_vapid_key", String.class);
        assertThat(atRest).isNotEqualTo("the-private-key").doesNotContain("the-private-key");
        assertThat(encryption.decrypt(atRest)).isEqualTo("the-private-key");
    }

    @Test
    @DisplayName("An instance that loses the race to insert reads the winner's pair")
    void losingTheRace() {
        String[] got = store.loadOrCreate(() -> {
            // Another instance inserts between this one's read and its insert.
            jdbc.update("INSERT INTO web_push_vapid_key (id, public_key, private_key, created_at)"
                    + " VALUES (1, 'winner', ?, CURRENT_TIMESTAMP)", encryption.encrypt("w"));
            return new String[] {"loser", "l"};
        });
        assertThat(got).containsExactly("winner", "w");
    }
}
