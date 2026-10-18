package com.spiramindscape.backend.push.web;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Where the VAPID pair comes from: the environment wins, then the database, which is filled on
 * first use — from the old local key file if there is one, so local subscriptions survive, and
 * otherwise from a fresh pair. A database that cannot be reached leaves push off for that call,
 * never half on, and is tried again on the next.
 */
class WebPushPropertiesTest {

    private static final String RFC_PUBLIC =
            "BP4z9KsN6nGRTbVYI_c7VJSPQTBtkgcy27mlmlMoZIIgDll6e3vCYLocInmYWAmS6TlzAC8wEqKK6PBru3jl7A8";
    private static final String RFC_PRIVATE = "yfWPiYE-n46HLnH0KqZOF1fJJU3MYrct3AELtAQ-oRw";

    /** An in-memory stand-in for the one row; {@code down} makes it throw like a lost connection. */
    static class MemoryStore extends VapidKeyStore {
        String[] row;
        boolean down;
        int calls;

        MemoryStore() {
            super(null, null, null);
        }

        @Override
        public String[] loadOrCreate(Supplier<String[]> fresh) {
            calls++;
            if (down) {
                throw new IllegalStateException("database unreachable");
            }
            if (row == null) {
                row = fresh.get();
            }
            return row;
        }
    }

    private static WebPushProperties props(String pub, String priv, VapidKeyStore store, Path keyFile) {
        return new WebPushProperties(pub, priv, "mailto:x@y.z", store, keyFile.toString());
    }

    @Test
    @DisplayName("With nothing configured, a pair is created once, stored, and the same pair is read after")
    void createdOnceThenReused(@TempDir Path dir) throws Exception {
        MemoryStore store = new MemoryStore();
        WebPushProperties first = props("", "", store, dir.resolve("none.properties"));
        assertThat(first.enabled()).isTrue();
        assertThat(WebPushCrypto.decode(first.publicKey())).hasSize(65);
        first.signingKey(); // a usable private key

        WebPushProperties second = props("", "", store, dir.resolve("none.properties"));
        assertThat(second.publicKey()).isEqualTo(first.publicKey());
    }

    @Test
    @DisplayName("Keys from the environment win, and the database is not touched")
    void environmentWins(@TempDir Path dir) {
        MemoryStore store = new MemoryStore();
        WebPushProperties props = props(RFC_PUBLIC, RFC_PRIVATE, store, dir.resolve("none.properties"));
        assertThat(props.publicKey()).isEqualTo(RFC_PUBLIC);
        assertThat(store.calls).isZero();
    }

    @Test
    @DisplayName("The old local key file's pair is the one stored, so local subscriptions keep working")
    void adoptsTheLocalFile(@TempDir Path dir) throws Exception {
        Path file = dir.resolve("vapid-keys.properties");
        Files.writeString(file, "public-key=" + RFC_PUBLIC + "\nprivate-key=" + RFC_PRIVATE + "\n");
        MemoryStore store = new MemoryStore();
        assertThat(props("", "", store, file).publicKey()).isEqualTo(RFC_PUBLIC);
        assertThat(store.row[1]).isEqualTo(RFC_PRIVATE);
    }

    @Test
    @DisplayName("A database that is down means push is off for now, and it is asked again next time")
    void offWhileTheDatabaseIsDown(@TempDir Path dir) {
        MemoryStore store = new MemoryStore();
        store.down = true;
        WebPushProperties props = props("", "", store, dir.resolve("none.properties"));
        assertThat(props.enabled()).isFalse();
        assertThat(props.publicKey()).isEmpty();

        store.down = false;
        assertThat(props.enabled()).isTrue();
    }
}
