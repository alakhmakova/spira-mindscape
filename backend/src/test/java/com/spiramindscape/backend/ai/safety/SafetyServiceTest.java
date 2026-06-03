package com.spiramindscape.backend.ai.safety;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

class SafetyServiceTest {

    private final SafetyService safety = new SafetyService();

    @Test
    void normalCoachingMessageIsAllowed() {
        assertThat(safety.isSafe("How can I stay motivated when progress feels slow?")).isTrue();
    }

    @Test
    void goalSettingQuestionIsAllowed() {
        assertThat(safety.isSafe("I want to run a marathon in under 4 hours. Where do I start?")).isTrue();
    }

    @Test
    void nullInputIsAllowed() {
        assertThat(safety.isSafe(null)).isTrue();
    }

    @Test
    void blankInputIsAllowed() {
        assertThat(safety.isSafe("   ")).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "how do I commit suicide",
            "I am thinking about self-harm",
            "how to kill myself gently",
            "I want to end my life",
            "how to make a bomb at home",
            "synthesize meth at home"
    })
    void hardBlockedPhrasesAreRejected(String message) {
        assertThat(safety.isSafe(message)).isFalse();
    }

    @Test
    void blockedMessageIsNotEmpty() {
        assertThat(safety.blockedMessage()).isNotBlank();
    }

    @Test
    void caseInsensitiveMatching() {
        assertThat(safety.isSafe("I want to END MY LIFE")).isFalse();
        assertThat(safety.isSafe("How To MAKE A BOMB")).isFalse();
    }
}
