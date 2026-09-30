package com.spiramindscape.backend.ai.cv;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The words the server says at the seams. They are a template on purpose: structure a template
 * carries cannot be skipped by a model having an off day, and the owner's process depends on the
 * user always knowing which of the four steps she is in and what is needed.
 */
class CvTransitionsTest {

    private static final String EM_DASH = "\u2014";

    private static CvTransitions.Facts facts(String intakeStatus) {
        return new CvTransitions.Facts("System Developer/Tester på Advania", "System Developer/Tester",
                "Advania", "Malmö", "Det som kännetecknar rollen är kombinationen av utveckling och test",
                intakeStatus, "Mina uppgifter", "Profil 2025", List.of("Links"),
                18, 1, 9, 5, List.of("Kräver svenskt medborgarskap"), 4,
                "Backend Developer — Advania", "CV — Advania", true,
                "/goals/7?resource=362");
    }

    private static boolean hasEmoji(String s) {
        return s.codePoints().anyMatch(cp -> cp >= 0x1F300 || (cp >= 0x2600 && cp <= 0x27BF));
    }

    @ParameterizedTest
    @ValueSource(strings = {"ru", "sv", "en"})
    @DisplayName("the opening names the role and employer, lays out four steps, and asks one question")
    void opening(String lang) {
        String text = CvTransitions.opening(facts("none"), lang);

        assertThat(text).contains("System Developer/Tester").contains("Advania");
        for (int n = 1; n <= 4; n++) assertThat(text).contains(n + ". **");
        assertThat(text).doesNotContain("5. **");
        assertThat(text.chars().filter(c -> c == '?').count()).isEqualTo(1);
        assertThat(text.strip()).endsWith("?");
        assertThat(hasEmoji(text)).isFalse();
    }

    @Test
    @DisplayName("step 1 says how the advert is read, and that nothing is needed from her yet")
    void analysisStep() {
        assertThat(CvTransitions.announce(CvStep.ANALYSIS, facts("none"), "ru"))
                .contains("Шаг 1 из 4")
                .contains("по предложениям")
                .contains("ничего не нужно");
    }

    @Test
    @DisplayName("the analysis is reported with its counts and the core message in the advert's words")
    void analysisSummary() {
        String ru = CvTransitions.analysisSummary(facts("none"), "ru");

        assertThat(ru).contains("18").contains("9").contains("5")
                .contains("kombinationen av utveckling och test")
                // A condition the advert sets is raised at once, not saved for the letter.
                .contains("Kräver svenskt medborgarskap");
    }

    @ParameterizedTest
    @ValueSource(strings = {"ru", "sv", "en"})
    @DisplayName("the link to the map belongs to the map step, so the summary does not repeat it")
    void theSummaryDoesNotLinkTheMap(String lang) {
        assertThat(CvTransitions.analysisSummary(facts("none"), lang)).doesNotContain("/goals/7?resource=362");
    }

    @ParameterizedTest
    @ValueSource(strings = {"ru", "sv", "en"})
    @DisplayName("a condition of the advert ENDS the summary, as a question — it is not a line in passing")
    void aFlagIsAskedAbout(String lang) {
        String text = CvTransitions.analysisSummary(facts("none"), lang);

        assertThat(text.strip()).endsWith("?");
        assertThat(text.indexOf("Kräver svenskt medborgarskap"))
                .isGreaterThan(text.indexOf("kombinationen av utveckling och test"));
        // The condition is quoted, not glued into a sentence of ours.
        assertThat(text).contains("\n> Kräver svenskt medborgarskap");
    }

    @Test
    @DisplayName("nothing is raised when the advert sets no conditions, so the step can follow at once")
    void noFlagNoQuestion() {
        CvTransitions.Facts noFlags = new CvTransitions.Facts("QA-testare", "QA-testare", "Nordfragt",
                "Aarhus", "kombination", "none", null, null, List.of(), 3, 0, 2, 1, List.of(), 3,
                null, null, true);

        assertThat(CvTransitions.flagQuestion(noFlags, "ru")).isEmpty();
        assertThat(CvTransitions.analysisSummary(noFlags, "ru").strip()).doesNotEndWith("?");
    }

    @ParameterizedTest
    @ValueSource(strings = {"ru", "sv", "en"})
    @DisplayName("the Russian never genders the writer — the work is stated, not the worker")
    void noGenderedSelfReference(String lang) {
        String text = CvTransitions.analysisSummary(facts("none"), lang)
                + CvTransitions.announce(CvStep.MAP, facts("exists"), lang);

        assertThat(text).doesNotContain("прочитал ").doesNotContain("сформулировал")
                .doesNotContain("разобрал ");
    }

    @ParameterizedTest
    @ValueSource(strings = {"ru", "sv", "en"})
    @DisplayName("the map step links the map as something to click, explains its parts, and asks herself-or-together")
    void mapStep(String lang) {
        String text = CvTransitions.announce(CvStep.MAP, facts("none"), lang);

        // The chat renders markdown, so this reaches her as a link rather than a title to go find.
        assertThat(text).contains("[Backend Developer — Advania](/goals/7?resource=362)");
        assertThat(text).contains("**Skills**").contains("Company 1, 2, 3");
        assertThat(text.strip()).endsWith(switch (lang) {
            case "ru" -> "с какой части начнём.";
            case "sv" -> "vilken del vi börjar med.";
            default -> "which part to start with.";
        });
        assertThat(text).contains("?");
    }

    @Test
    @DisplayName("with no map link yet, the map step points at the resources instead of a dead link")
    void mapStepWithoutALink() {
        CvTransitions.Facts noLink = new CvTransitions.Facts("QA-testare", "QA-testare", "Nordfragt",
                "Aarhus", null, "none", null, null, List.of(), 3, 0, 2, 1, List.of(), 3,
                null, null, true);

        assertThat(CvTransitions.announce(CvStep.MAP, noLink, "en"))
                .contains("in the goal's resources").doesNotContain("](");
    }

    @Test
    @DisplayName("her details are acknowledged with what is missing; a candidate note is offered, not assumed")
    void mapStepAndHerDetails() {
        assertThat(CvTransitions.announce(CvStep.MAP, facts("exists"), "ru"))
                .contains("«Mina uppgifter»").contains("не хватает: Links");
        assertThat(CvTransitions.announce(CvStep.MAP, facts("candidate"), "en"))
                .contains("Profil 2025").contains("Use as my details");
        assertThat(CvTransitions.announce(CvStep.MAP, facts("none"), "en"))
                .doesNotContain("Profil 2025").doesNotContain("Mina uppgifter");
    }

    @Test
    @DisplayName("step 3 promises one whole document from the map, not a paragraph per answer")
    void cvStep() {
        assertThat(CvTransitions.announce(CvStep.CV, facts("exists"), "ru"))
                .contains("Шаг 3 из 4").contains("целиком").contains("не по кусочкам");
    }

    @Test
    @DisplayName("step 4 asks whether a letter is wanted at all")
    void letterStep() {
        assertThat(CvTransitions.announce(CvStep.LETTER, facts("exists"), "en"))
                .contains("Step 4 of 4").contains("Is a cover letter wanted");
    }

    @ParameterizedTest
    @ValueSource(strings = {"ANALYSIS", "MAP", "CV", "LETTER"})
    @DisplayName("every step has an announcement and a waiting line in every language, with no emoji")
    void everyStep(String name) {
        CvStep step = CvStep.valueOf(name);
        for (String lang : List.of("ru", "sv", "en")) {
            String a = CvTransitions.announce(step, facts("exists"), lang);
            String w = CvTransitions.waiting(step, facts("exists"), lang);
            assertThat(a).isNotBlank().contains(String.valueOf(step.number()));
            assertThat(w).isNotBlank();
            assertThat(hasEmoji(a) || hasEmoji(w)).isFalse();
        }
    }

    @Test
    @DisplayName("the user's language is recognised from what she writes; control turns say nothing")
    void languageDetection() {
        assertThat(CvTransitions.detectLanguage("Да, начинаем с анализа")).isEqualTo("ru");
        assertThat(CvTransitions.detectLanguage("Ja, vi kör på det")).isEqualTo("sv");
        assertThat(CvTransitions.detectLanguage("Yes, let's start with the advert")).isEqualTo("en");
        assertThat(CvTransitions.detectLanguage("ok")).isNull();
        assertThat(CvTransitions.detectLanguage("[Continue from wherever]")).isNull();
        assertThat(CvTransitions.lang("sv-SE")).isEqualTo("sv");
        assertThat(CvTransitions.lang("de")).isEqualTo("en");
    }

    @Test
    @DisplayName("a provider outage is told as an outage, not as a bad answer")
    void busyIsNotFailure() {
        for (String lang : List.of("ru", "sv", "en")) {
            assertThat(CvTransitions.analysisBusy(lang)).isNotEqualTo(CvTransitions.analysisFailed(lang));
        }
        assertThat(CvTransitions.analysisBusy("ru")).contains("перегружен");
    }

    @Test
    @DisplayName("a map she made herself is named as hers, and nothing claims to have been created")
    void adoptedMapIsSaidToBeHers() {
        CvTransitions.Facts hers = new CvTransitions.Facts(
                "System Developer/Tester", "System Developer/Tester", "Advania", "Malm\u00f6", "core",
                "none", null, null, List.of(), 18, 1, 9, 5, List.of(), 4,
                "Backend Developer " + EM_DASH + " Advania", null, true, "/goals/7?resource=362", true);

        for (String lang : List.of("en", "ru", "sv")) {
            String text = CvTransitions.announce(CvStep.MAP, hers, lang);
            // It links her map, the same as always \u2026
            assertThat(text).contains("/goals/7?resource=362");
            // \u2026 and it says whose it is, because the writer used to make a second one beside it
            // and work only from that (owner, 2026-09-23).
            assertThat(text).containsAnyOf("your map", "\u0432\u0430\u0448\u0430 \u043a\u0430\u0440\u0442\u0430", "din karta");
        }
        // The "I made it" wording belongs to a map this application really did create.
        assertThat(CvTransitions.announce(CvStep.MAP, hers, "en")).doesNotContain("is ready:");
        assertThat(CvTransitions.announce(CvStep.MAP, facts("none"), "en")).contains("is ready:");
    }

    @Test
    @DisplayName("an advert with no requirements still gets a map, and a way to fill it")
    void noRequirements() {
        assertThat(CvTransitions.noRequirements("ru"))
                .contains("Карта вакансии").contains("похожим объявлениям").contains("по названию должности");
    }
}
