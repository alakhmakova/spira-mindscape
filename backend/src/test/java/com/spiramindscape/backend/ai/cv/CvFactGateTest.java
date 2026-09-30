package com.spiramindscape.backend.ai.cv;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The gate has to bite on an invented figure and stay silent on a legitimate rewrite.
 *
 * <p>The second half is the harder half and the reason this is scoped to numbers: the
 * writer's job is to say the user's substance in better words, so a check that flagged
 * rewording would break the feature rather than protect it.
 */
class CvFactGateTest {

    /** What the user actually said, as the server holds it. */
    private static final String CORPUS = String.join("\n",
            "Squidler: led the test work towards the customer, 2019 to 2023",
            "cut the nightly batch from 4 hours to 40 minutes",
            "the team was 12 people",
            "saved about 250 000 SEK a year in licences");

    // ── It bites ────────────────────────────────────────────────────────────

    @Test
    @DisplayName("a percentage nobody gave is caught")
    void inventedPercentage() {
        String cv = "<p>Improved release quality, cutting defects by 40%.</p>";

        assertThat(CvFactGate.unsupportedFigures(cv, CORPUS)).containsExactly("40%");
    }

    @Test
    @DisplayName("an invented headcount or volume is caught")
    void inventedLargeNumber() {
        String cv = "<p>Ran the test suite across 1 400 devices.</p>";

        assertThat(CvFactGate.unsupportedFigures(cv, CORPUS)).containsExactly("1400");
    }

    @Test
    @DisplayName("an invented money figure is caught even when it is small")
    void inventedMoney() {
        String cv = "<p>Negotiated a 30 SEK unit price.</p>";

        assertThat(CvFactGate.unsupportedFigures(cv, CORPUS))
                .anySatisfy(f -> assertThat(f).startsWith("30"));
    }

    @Test
    @DisplayName("several invented figures are all reported, once each")
    void severalFigures() {
        String cv = "<p>Grew coverage by 80% across 2 500 tests, and again by 80%.</p>";

        assertThat(CvFactGate.unsupportedFigures(cv, CORPUS))
                .containsExactly("80%", "2500");
    }

    // ── It stays silent ─────────────────────────────────────────────────────

    @Test
    @DisplayName("a figure the user gave passes, however the writer reformats it")
    void reformattedFigurePasses() {
        // "250 000 SEK" in the corpus, written as "250,000" here: the same claim.
        String cv = "<p>Cut licence spend by 250,000 SEK.</p>"
                + "<p>Reduced a nightly batch of 4 hours to 40 minutes.</p>"
                + "<p>Squidler, 2019–2023.</p>";

        assertThat(CvFactGate.unsupportedFigures(cv, CORPUS)).isEmpty();
    }

    @Test
    @DisplayName("the user's own words rewritten entirely still pass — only numbers are checked")
    void rewordingIsNotFlagged() {
        // Nothing here matches the corpus verbatim, and that is the writer's whole job.
        String cv = "<p>Owned quality for a customer-facing platform and took a 12-strong "
                + "team through it.</p>";

        assertThat(CvFactGate.unsupportedFigures(cv, CORPUS)).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "<p>Java 17, Spring Boot 3, Node 20.</p>",   // version numbers
            "<p>Section 2 of the report.</p>",           // an incidental small number
            "<p>Worked in 2 teams.</p>",                 // a small count
            // A currency fragment inside an ordinary word must not promote a small number
            // to a money claim. Unanchored, every one of these was flagged — and the user
            // was told their own "12 EUR" was invented (found in review, 2026-09-10).
            "<p>Worked with 12 European clients.</p>",
            "<p>Ansvarade för 12 sektioner av testsviten.</p>",
            "<p>Uppfyllde 12 krav i kravspecifikationen.</p>",
            "<p>Byggde 12 sekunder snabbare svarstid.</p>",
            "<p>Testade 12 Nokia-enheter.</p>",
    })
    @DisplayName("one- and two-digit numbers are not checked — a stated limit, not an oversight")
    void smallNumbersAreNotChecked(String cv) {
        assertThat(CvFactGate.unsupportedFigures(cv, CORPUS)).isEmpty();
    }

    @Test
    @DisplayName("a real currency word still promotes a small figure to a money claim")
    void realMoneyIsStillChecked() {
        // The boundary anchoring must not cost the check it exists for.
        assertThat(CvFactGate.unsupportedFigures("<p>Negotiated a 30 SEK unit price.</p>", CORPUS))
                .isNotEmpty();
        assertThat(CvFactGate.unsupportedFigures("<p>Saved 30 kr per order.</p>", CORPUS))
                .isNotEmpty();
        assertThat(CvFactGate.unsupportedFigures("<p>A €30 saving.</p>", CORPUS))
                .isNotEmpty();
    }

    @Test
    @DisplayName("an empty document and an empty corpus are both handled")
    void emptyInputs() {
        assertThat(CvFactGate.unsupportedFigures("", CORPUS)).isEmpty();
        assertThat(CvFactGate.unsupportedFigures(null, CORPUS)).isEmpty();
        // No corpus means nothing is supported, which is correct — but the document must
        // still be scanned rather than blowing up.
        assertThat(CvFactGate.unsupportedFigures("<p>up 40%</p>", null))
                .containsExactly("40%");
    }

    @Test
    @DisplayName("numbers hidden in HTML attributes are not mistaken for claims")
    void tagsAreStripped() {
        String cv = "<h2 id=\"section-9999\"><span style=\"font-size:14px\">Erfarenhet</span></h2>";

        assertThat(CvFactGate.unsupportedFigures(cv, CORPUS)).isEmpty();
    }

    // ── What the user is told ───────────────────────────────────────────────

    @Test
    @DisplayName("the warning names the figures and asks the user, who is the one who knows")
    void theWarningIsAddressedToTheUser() {
        String message = CvFactGate.warning(java.util.List.of("40%", "1400"));

        assertThat(message)
                .contains("40%")
                .contains("1400")
                .contains("not in anything you have told me");
        // No figures, no message: an unremarkable document says nothing about itself.
        assertThat(CvFactGate.warning(java.util.List.of())).isEmpty();
    }
}
