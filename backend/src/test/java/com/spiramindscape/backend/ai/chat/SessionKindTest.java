package com.spiramindscape.backend.ai.chat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link SessionKind} decides which assistant speaks, from a field the client
 * supplies. The only behaviour worth pinning is what happens when that field is
 * not one of the three: it must be the least-privileged assistant, never a crash
 * and never a session.
 */
class SessionKindTest {

    @Test
    @DisplayName("the three assistants parse from their wire values")
    void parsesKnownValues() {
        assertThat(SessionKind.from("chat")).isEqualTo(SessionKind.CHAT);
        assertThat(SessionKind.from("grow")).isEqualTo(SessionKind.GROW);
        assertThat(SessionKind.from("cv")).isEqualTo(SessionKind.CV);
    }

    @ParameterizedTest
    @ValueSource(strings = {"GROW", "Grow", "  grow  "})
    @DisplayName("case and surrounding space do not change which assistant is chosen")
    void parsingIsLenientAboutShape(String raw) {
        assertThat(SessionKind.from(raw)).isEqualTo(SessionKind.GROW);
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "   ", "coach", "cv-writer", "GROW_SESSION", "1"})
    @DisplayName("anything unrecognised falls back to plain chat, never to a session")
    void unknownValuesFallBackToChat(String raw) {
        assertThat(SessionKind.from(raw)).isEqualTo(SessionKind.CHAT);
    }

    @Test
    @DisplayName("a missing sessionType is plain chat — every client that predates an assistant omits it")
    void nullIsChat() {
        assertThat(SessionKind.from(null)).isEqualTo(SessionKind.CHAT);
    }

    @Test
    @DisplayName("failing closed matters: the fallback is the kind with no session tools")
    void theFallbackIsNotASession() {
        assertThat(SessionKind.from("something new the server has not shipped yet").isSession())
                .isFalse();
        assertThat(SessionKind.GROW.isSession()).isTrue();
        assertThat(SessionKind.CV.isSession()).isTrue();
    }

    @Test
    @DisplayName("wire values round-trip")
    void wireValuesRoundTrip() {
        for (SessionKind kind : SessionKind.values()) {
            assertThat(SessionKind.from(kind.wireValue())).isEqualTo(kind);
        }
    }
}
