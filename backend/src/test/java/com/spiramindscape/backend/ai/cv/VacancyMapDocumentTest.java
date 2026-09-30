package com.spiramindscape.backend.ai.cv;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * How the vacancy map reads to the CV coach. This class is the one place the map's own document is
 * turned into words a model sees, so what it does and does not say is a product decision, not a
 * formatting detail: a requirement she cannot meet must be stated, a skill she has not ticked must
 * never be claimed, and the advert's own figures must not be handed to the fact gate as hers.
 *
 * <p>Pure: a document in, text out. Nothing here needs a Spring context.
 */
class VacancyMapDocumentTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private static JsonNode doc(String json) {
        try {
            return MAPPER.readTree(json);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    // ── The three marks a requirement can carry ─────────────────────────────

    @Test
    @DisplayName("a requirement she cannot meet says so, and is not also 'very important'")
    void statesTheMarks() {
        String view = VacancyMapDocument.promptView(doc("""
                {"requirements":[
                  {"text":"Five years of Java","important":true},
                  {"text":"A Swedish driving licence","unmet":true},
                  {"text":"Kotlin"}]}"""));

        assertThat(view).contains("VERY IMPORTANT · \"Five years of Java\"");
        assertThat(view).contains("SHE CANNOT MEET THIS · \"A Swedish driving licence\"");
        // An unmarked requirement carries neither word — the marks mean nothing if every line has one.
        assertThat(view).contains("\"Kotlin\"  (/requirements/2)");
        assertThat(view.lines().filter(l -> l.contains("Kotlin")).findFirst().orElseThrow())
                .doesNotContain("VERY IMPORTANT")
                .doesNotContain("CANNOT MEET");
    }

    @Test
    @DisplayName("the job title is read, and a map written before the field existed is not broken by it")
    void statesTheJobTitle() {
        assertThat(VacancyMapDocument.promptView(doc("""
                {"facts":{"jobTitle":"Backend developer"}}""")))
                .contains("job title: \"Backend developer\"  (/facts/jobTitle)");
        assertThat(VacancyMapDocument.promptView(doc("{\"facts\":{}}")))
                .contains("job title: (empty)");
    }

    // ── A comment can name the employer it is about ─────────────────────────

    @Test
    @DisplayName("a comment naming an employer is printed under that employer, so an example lands in the right job")
    void attributesACommentToItsEmployer() {
        String view = VacancyMapDocument.promptView(doc("""
                {"skills":[{"text":"Java 17","checked":true,"comments":[
                  {"text":"Wrote the payments service","company":"Advania"},
                  {"text":"Used it daily"}]}]}"""));

        assertThat(view).contains("at Advania: Wrote the payments service");
        // A comment with no employer is printed as it stands — never as "at : ...".
        assertThat(view).contains("Used it daily").doesNotContain("at : ");
    }

    // ── What may be written from, and what may not ──────────────────────────

    @Test
    @DisplayName("an unticked skill is never material, and is named so it cannot be claimed by accident")
    void keepsUntickedSkillsOut() {
        String material = VacancyMapDocument.material(doc("""
                {"skills":[
                  {"text":"Java 17","checked":true},
                  {"text":"Kubernetes","checked":false}]}"""));

        assertThat(material).contains("Skills she HAS (these go in the competence block): Java 17");
        assertThat(material).contains("Skills she has NOT ticked (never claim these anywhere): Kubernetes");
    }

    @Test
    @DisplayName("a requirement with no answer under any employer is named as missing, never filled in")
    void namesAnUnansweredRequirement() {
        String material = VacancyMapDocument.material(doc("""
                {"requirements":[
                  {"text":"Five years of Java","companies":[{"label":"Advania","text":"Four years on the platform team"}]},
                  {"text":"Team leadership","companies":[{"label":"Advania","text":""}]}]}"""));

        assertThat(material).contains("at Advania: Four years on the platform team");
        assertThat(material).contains("(no answer — leave it OUT of the documents");
    }

    @Test
    @DisplayName("an unnamed employer column is numbered by position, on both surfaces the same way")
    void numbersAnUnnamedEmployer() {
        assertThat(VacancyMapDocument.companyLabel(doc("{\"label\":\"  \"}"), 1)).isEqualTo("Company 2");
        assertThat(VacancyMapDocument.companyLabel(doc("{\"label\":\" Advania \"}"), 1)).isEqualTo("Advania");
    }

    // ── What the fact gate is allowed to believe ────────────────────────────

    @Test
    @DisplayName("the evidence is HER words only — the advert's lines and the company blurb are left out")
    void evidenceHoldsOnlyHerOwnWords() {
        String evidence = VacancyMapDocument.evidenceText(doc("""
                {"facts":{"experience":"The advert asks for five years","experienceNote":"I have four"},
                 "requirements":[{"text":"Five years of Java","companies":[{"label":"Advania","text":"Payments platform"}]}],
                 "company":{"about":"A Nordic IT company of 400 people","comments":[{"text":"The recruiter is Anna"}]}}"""));

        assertThat(evidence).contains("I have four");
        assertThat(evidence).contains("Payments platform").contains("Advania");
        assertThat(evidence).contains("The recruiter is Anna");
        // The employer's own sentence is not hers: "400 people" must not let a CV line claiming
        // 400 reports pass the fact gate, and the advert's "five years" must not either.
        assertThat(evidence).doesNotContain("400 people");
        assertThat(evidence).doesNotContain("The advert asks for five years");
    }

    // ── Counting, which is what the coach's state block reports ─────────────

    @Test
    @DisplayName("counts what is there, and how much of it is still unanswered")
    void counts() {
        VacancyMapDocument.Counts counts = VacancyMapDocument.counts(doc("""
                {"skills":[{"text":"Java","checked":true},{"text":"Go"}],
                 "qualities":[{"text":"Takes initiative","checked":true}],
                 "requirements":[
                   {"text":"Java","important":true,"companies":[{"text":"Payments platform"}]},
                   {"text":"Go","companies":[{"text":""}]}],
                 "additional":[{"text":"Notice period"}]}"""));

        assertThat(counts.skills()).isEqualTo(2);
        assertThat(counts.skillsTicked()).isEqualTo(1);
        assertThat(counts.qualities()).isEqualTo(1);
        assertThat(counts.requirements()).isEqualTo(2);
        assertThat(counts.important()).isEqualTo(1);
        assertThat(counts.requirementsAnswered()).isEqualTo(1);
        assertThat(counts.requirementsOpen()).isEqualTo(1);
        assertThat(counts.additional()).isEqualTo(1);
    }

    // ── The document is half-written most of the time ───────────────────────

    @Test
    @DisplayName("an empty or malformed branch reads as empty, never as an error")
    void survivesAHalfWrittenDocument() {
        JsonNode half = doc("{\"skills\":\"not an array\",\"requirements\":null}");

        assertThat(VacancyMapDocument.counts(half).skills()).isZero();
        assertThat(VacancyMapDocument.promptView(half)).contains("(none)");
        assertThat(VacancyMapDocument.material(half)).isEmpty();
        assertThat(VacancyMapDocument.evidenceText(half)).isEmpty();
    }

    @Test
    @DisplayName("one long field cannot fill the prompt by itself")
    void clipsALongField() {
        String material = VacancyMapDocument.material(doc("""
                {"facts":{"experience":"five years","experienceNote":"%s"}}"""
                .formatted("x".repeat(900))));

        assertThat(material).contains("…");
        assertThat(material.length()).isLessThan(700);
    }
}
