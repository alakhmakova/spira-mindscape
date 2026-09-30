package com.spiramindscape.backend.ai.cv;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.lang.reflect.RecordComponent;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The words on the CV panel's own controls.
 *
 * <p>They are pinned here rather than in the client's spec because this is the side that owns
 * them: the cards used to carry English literals while every other line of the process was
 * localised, so a Russian session announced step 3 in Russian and asked its question in English
 * (owner's live run, 2026-09-16).
 */
class CvCardTextTest {

    @ParameterizedTest
    @ValueSource(strings = {"ru", "sv", "en", "de", "sv-SE"})
    @DisplayName("every card has every word, in every language we answer in")
    void nothingIsBlank(String lang) throws Exception {
        CvCardText text = CvCardText.of(lang);

        for (RecordComponent component : CvCardText.class.getRecordComponents()) {
            Object value = component.getAccessor().invoke(text);
            assertThat(value).as(component.getName()).isNotNull();
            if (value instanceof String s) {
                assertThat(s).as(component.getName()).isNotBlank();
            } else if (value instanceof List<?> lines) {
                assertThat(lines).as(component.getName()).isNotEmpty();
                assertThat(lines).allSatisfy(l -> assertThat((String) l).isNotBlank());
            }
        }
    }

    @Test
    @DisplayName("an unknown language is answered in English, never left empty")
    void unknownLanguageFallsBackToEnglish() {
        assertThat(CvCardText.of("de")).isEqualTo(CvCardText.of("en"));
        assertThat(CvCardText.of(null)).isEqualTo(CvCardText.of("en"));
    }

    @Test
    @DisplayName("the placeholders the client substitutes are present, or a card renders a literal brace")
    void placeholders() {
        for (String lang : List.of("ru", "sv", "en")) {
            CvCardText text = CvCardText.of(lang);
            assertThat(text.useAsDetails()).as(lang).contains("{title}");
        }
    }


    @ParameterizedTest
    @ValueSource(strings = {"ru", "sv", "en"})
    @DisplayName("no emoji, on a card as anywhere else")
    void noEmoji(String lang) throws Exception {
        for (RecordComponent component : CvCardText.class.getRecordComponents()) {
            Object value = component.getAccessor().invoke(CvCardText.of(lang));
            String text = value instanceof List<?> lines
                    ? lines.stream().map(String::valueOf).reduce("", (a, b) -> a + " " + b)
                    : String.valueOf(value);
            assertThat(text.codePoints().anyMatch(cp -> cp >= 0x1F300 || (cp >= 0x2600 && cp <= 0x27BF)))
                    .as(component.getName()).isFalse();
        }
    }
}
