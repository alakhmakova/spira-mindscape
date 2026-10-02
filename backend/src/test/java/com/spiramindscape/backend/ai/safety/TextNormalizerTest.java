package com.spiramindscape.backend.ai.safety;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The normalizer's job is to defeat obfuscation WITHOUT destroying word boundaries.
 *
 * <p>It used to destroy them: the separator between any two letters was deleted, so a
 * pasted job advert became one unbroken run of letters and {@link SafetyService} then
 * found terms inside it that nobody had written. The boundary cases below are the ones
 * that were reported from real use.
 */
class TextNormalizerTest {

    // ── Word boundaries survive ordinary prose ──────────────────────────────

    @Test
    @DisplayName("Swedish 'till olika' keeps its space — it used to manufacture 'loli'")
    void swedishKeepsItsBoundaries() {
        String advert = "Du kommer att anpassa lösningar till olika kunder.";
        String n = TextNormalizer.normalize(advert);

        assertThat(n).contains("till olika");
        assertThat(n).doesNotContain("loli");
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "we discussed topics among the whole team",   // used to contain "csam"
            "anpassa lösningar till olika kunder",        // used to contain "loli"
            "the physics amount to a hard requirement",   // used to contain "csam"
    })
    @DisplayName("no disallowed term is manufactured out of innocent prose")
    void noTermIsInvented(String prose) {
        String n = TextNormalizer.normalize(prose);

        assertThat(n).doesNotContain("csam").doesNotContain("loli");
    }

    @Test
    @DisplayName("a long paste keeps its spaces — every word boundary, not just some")
    void longPasteKeepsSpaces() {
        String advert = "Vi soker en utvecklare som kan arbeta med olika team.";

        // Ten words in, ten words out: the old regex returned a single long word.
        assertThat(TextNormalizer.normalize(advert).split(" ")).hasSize(10);
    }

    // ── Obfuscation is still folded ─────────────────────────────────────────

    @ParameterizedTest
    @CsvSource({
            "'b o m b', bomb",
            "'b.o.m.b', bomb",
            "'b-o-m-b', bomb",
            "'b*o*m*b', bomb",
            "'B O M B', bomb",
    })
    @DisplayName("letters spaced out one at a time are still joined")
    void spacedLettersAreJoined(String input, String expected) {
        assertThat(TextNormalizer.normalize(input)).isEqualTo(expected);
    }

    @Test
    @DisplayName("a spaced-out run next to real words joins only the run")
    void onlyTheRunIsJoined() {
        // "a" is itself a single letter, so it joins the run — which is why the
        // separator-free form exists for matching (see SafetyService).
        assertThat(TextNormalizer.normalize("how to make a b o m b"))
                .isEqualTo("how to make abomb");
    }

    @ParameterizedTest
    @CsvSource({
            "b0mb, bomb",           // leetspeak
            "'bömb', bomb",         // accents
            "'ｂｏｍｂ', bomb",        // fullwidth
            "'вomb', bomb",         // Cyrillic homoglyph (ve, which looks like a b)
    })
    @DisplayName("folding still handles leetspeak, accents, fullwidth and homoglyphs")
    void foldingStillWorks(String input, String expected) {
        assertThat(TextNormalizer.normalize(input)).isEqualTo(expected);
    }

    @Test
    @DisplayName("pure numbers are left alone by the leetspeak pass")
    void numbersSurvive() {
        assertThat(TextNormalizer.normalize("I have 10 years and 3 roles"))
                .isEqualTo("i have 10 years and 3 roles");
    }

    @Test
    void nullAndEmptyAreEmpty() {
        assertThat(TextNormalizer.normalize(null)).isEmpty();
        assertThat(TextNormalizer.normalize("")).isEmpty();
    }
}
