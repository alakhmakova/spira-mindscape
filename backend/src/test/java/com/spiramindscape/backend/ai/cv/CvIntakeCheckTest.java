package com.spiramindscape.backend.ai.cv;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The intake form is the dry skeleton a CV needs. This decides what the writer still has to ask
 * for — so it asks for what is missing and nothing else, which is the whole point of step 2 being
 * short (owner, 2026-09-16).
 */
class CvIntakeCheckTest {

    @Test
    @DisplayName("what is missing is named, in whatever language the headings are written")
    void missingParts() {
        var result = CvIntakeCheck.check("<p>anna@example.se +46 70 123 45 67</p>"
                + "<h2>Опыт работы</h2><p>Zocom</p><h2>Utbildning</h2><p>Malmö</p>");

        // Contact is recognised without a heading; courses, languages and links are not there.
        assertThat(result.missing()).containsExactly("Courses", "Languages", "Links");
        assertThat(result.hasLinks()).isFalse();
        assertThat(result.complete()).isFalse();
    }

    @Test
    @DisplayName("a complete form passes, and a link counts as links wherever it sits")
    void completeForm() {
        var result = CvIntakeCheck.check("<h2>Contact</h2><p>anna@example.se, +46 70 123 45 67</p>"
                + "<h2>Links</h2><p><a href=\"https://github.com/alakhmakova\">github.com/alakhmakova</a></p>"
                + "<h2>Employers</h2><h3>Zocom</h3><h2>Education</h2><h2>Courses and certificates</h2>"
                + "<h2>Languages</h2><p>Swedish - fluent</p>");

        assertThat(result.complete()).isTrue();
        assertThat(result.hasLinks()).isTrue();
        assertThat(result.chars()).isGreaterThan(0);
    }

    @Test
    @DisplayName("an empty form is missing everything and does not throw")
    void empty() {
        assertThat(CvIntakeCheck.check(null).missing())
                .containsExactly("Contact", "Employers", "Education", "Courses", "Languages", "Links");
    }

    @Test
    @DisplayName("languages are part of the skeleton, and a bare line with a level counts as the section")
    void languagesAreSkeleton() {
        // The advert being applied for made "Goda kunskaper i svenska och engelska" a REQUIRED
        // competence, and her own CV carries the line in its header — so a form taken without it
        // throws away the answer to a must-have, and step 4 cannot recover it: there is no
        // sensible "tell me about a time you were fluent in Swedish" (live run, 2026-09-16).
        assertThat(CvIntakeCheck.check("<h2>Språk</h2><p>Svenska, engelska</p>").missing())
                .doesNotContain("Languages");
        // People write it beside the phone number as often as under a heading of its own.
        assertThat(CvIntakeCheck.check("<p>Svenska – flytande · Ryska – modersmål</p>").missing())
                .doesNotContain("Languages");
        assertThat(CvIntakeCheck.check("<h2>Utbildning</h2><p>JENSEN</p>").missing())
                .contains("Languages");
    }

    @Test
    @DisplayName("an e-mail alone is not contact details: a CV needs a way to phone her too")
    void contactNeedsMoreThanAnEmail() {
        assertThat(CvIntakeCheck.check("<p>anna@example.se</p>").missing()).contains("Contact");
    }
}
