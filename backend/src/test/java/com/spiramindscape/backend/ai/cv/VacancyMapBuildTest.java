package com.spiramindscape.backend.ai.cv;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.spiramindscape.backend.graphql.input.MapPatchInput;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The advert → map mapping, which is the contract in {@code specs/2026-09-17-vacancy-map/}.
 * Each test here is one of the owner's decisions of 2026-09-17.
 */
class VacancyMapBuildTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private static CvApplicationService.Item item(String category, String label, String demand,
                                                  String context, String extraUse,
                                                  List<CvApplicationService.Quote> quotes) {
        return new CvApplicationService.Item(category, label, label, demand, context,
                CvRequirement.Weight.MUST, extraUse, quotes);
    }

    private static CvApplicationService.Quote quote(String text) {
        return new CvApplicationService.Quote(text, 1);
    }

    private static Optional<JsonNode> at(List<MapPatchInput> patches, String path) {
        return patches.stream().filter(p -> p.path().equals(path)).findFirst().map(p -> {
            try {
                return MAPPER.readTree(p.value());
            } catch (Exception e) {
                throw new IllegalStateException(e);
            }
        });
    }

    private static List<MapPatchInput> build(List<CvApplicationService.Item> items) {
        return VacancyMapBuild.initialPatches(null, null, null, null, null, null, null, null, items);
    }

    @Test
    @DisplayName("the core message becomes a requirement marked important, not a section of its own")
    void coreBecomesAnImportantRequirement() {
        var patches = build(List.of(
                item("core", "Owning a service end to end",
                        "You own your service end to end, in production", null, null, List.of()),
                item("requirement", "Relational databases",
                        "Experience with relational databases", null, null, List.of(quote("x")))));

        JsonNode requirements = at(patches, "/requirements").orElseThrow();
        assertThat(requirements).hasSize(2);
        assertThat(requirements.get(0).get("important").asBoolean()).isTrue();
        assertThat(requirements.get(0).get("text").asText())
                .isEqualTo("You own your service end to end, in production");
        assertThat(requirements.get(1).get("important").asBoolean()).isFalse();
    }

    @Test
    @DisplayName("a requirement's text is the advert's own sentence, not the short label")
    void requirementTextIsTheAdvertsSentence() {
        var patches = build(List.of(item("requirement", "Databases",
                "Experience with relational databases and writing efficient queries",
                null, null, List.of(quote("Experience with relational databases")))));

        assertThat(at(patches, "/requirements").orElseThrow().get(0).get("text").asText())
                .isEqualTo("Experience with relational databases and writing efficient queries");
    }

    @Test
    @DisplayName("a requirement starts with three blank company slots, so the page numbers them")
    void requirementStartsWithBlankCompanySlots() {
        var patches = build(List.of(item("requirement", "Java", "Built services in Java",
                null, null, List.of(quote("Java")))));

        JsonNode companies = at(patches, "/requirements").orElseThrow().get(0).get("companies");
        assertThat(companies).hasSize(3);
        assertThat(companies).allSatisfy(company -> {
            // Blank label: the page renders "Company 1", "Company 2" … from the position.
            assertThat(company.get("label").asText()).isEmpty();
            assertThat(company.get("text").asText()).isEmpty();
            assertThat(company.get("id").asText()).isNotBlank();
        });
    }

    @Test
    @DisplayName("a competence's fuller wording becomes a comment on the skill")
    void competenceQuoteBecomesAComment() {
        var patches = build(List.of(item("competence", "Bash", null, null, null,
                List.of(quote("Bash eller annan scripting")))));

        JsonNode skill = at(patches, "/skills").orElseThrow().get(0);
        assertThat(skill.get("text").asText()).isEqualTo("Bash");
        assertThat(skill.get("checked").asBoolean()).isFalse();
        assertThat(skill.get("comments")).hasSize(1);
        assertThat(skill.get("comments").get(0).get("text").asText())
                .isEqualTo("Bash eller annan scripting");
    }

    @Test
    @DisplayName("a quote that only repeats the tag is not kept as a comment")
    void competenceQuoteEqualToItsTagIsDropped() {
        var patches = build(List.of(item("competence", "Java", null, null, null,
                List.of(quote("Java")))));

        assertThat(at(patches, "/skills").orElseThrow().get(0).get("comments")).isEmpty();
    }

    @Test
    @DisplayName("a quality keeps the advert's line that implies it, as its comment")
    void traitKeepsItsContext() {
        var patches = build(List.of(item("trait", "Takes initiative", null,
                "du styrer selv din dag og dine kunder", null, List.of())));

        JsonNode quality = at(patches, "/qualities").orElseThrow().get(0);
        assertThat(quality.get("text").asText()).isEqualTo("Takes initiative");
        assertThat(quality.get("comments").get(0).get("text").asText())
                .isEqualTo("du styrer selv din dag og dine kunder");
    }

    @Test
    @DisplayName("extra notes are tagged for the letter; a condition to raise is left untagged")
    void extraNotesCarryTheirUse() {
        var patches = build(List.of(
                item("extra", "A small team of 4-5 who share knowledge", null, null, "letter", List.of()),
                item("extra", "A Swedish work permit is required", null, null, "flag", List.of())));

        JsonNode additional = at(patches, "/additional").orElseThrow();
        assertThat(additional.get(0).get("tag").asText()).isEqualTo("cover_letter");
        // A condition the writer raises at once is not letter material.
        assertThat(additional.get(1).get("tag").asText()).isEqualTo("none");
    }

    @Test
    @DisplayName("nothing arrives ticked and no answer is invented")
    void nothingIsAnsweredForHer() {
        var patches = build(List.of(
                item("competence", "Java", null, null, null, List.of()),
                item("trait", "Structured", null, "context", null, List.of())));

        assertThat(at(patches, "/skills").orElseThrow().get(0).get("checked").asBoolean()).isFalse();
        assertThat(at(patches, "/qualities").orElseThrow().get(0).get("checked").asBoolean()).isFalse();
    }

    @Test
    @DisplayName("the advert's stated conditions are written as facts, and blanks are not written")
    void factsAreWrittenOnlyWhenTheAdvertSaysThem() {
        var patches = VacancyMapBuild.initialPatches("Backend Developer", "Stockholm",
                "https://example.com/job", "BSc in Computer Science", "3+ years", null, "Advania",
                "https://advania.se", List.of());

        assertThat(at(patches, "/facts/jobTitle").orElseThrow().asText())
                .isEqualTo("Backend Developer");
        assertThat(at(patches, "/facts/location").orElseThrow().asText()).isEqualTo("Stockholm");
        assertThat(at(patches, "/facts/link").orElseThrow().asText())
                .isEqualTo("https://example.com/job");
        assertThat(at(patches, "/facts/education").orElseThrow().asText())
                .isEqualTo("BSc in Computer Science");
        assertThat(at(patches, "/facts/experience").orElseThrow().asText()).isEqualTo("3+ years");
        assertThat(at(patches, "/company/name").orElseThrow().asText()).isEqualTo("Advania");
        // The employer's own site, which the advert carried and nothing used to keep.
        assertThat(at(patches, "/company/link").orElseThrow().asText())
                .isEqualTo("https://advania.se");
        // The advert said nothing about languages, so nothing is written there.
        assertThat(at(patches, "/facts/language")).isEmpty();
    }

    @Test
    @DisplayName("an empty category is not written at all")
    void emptyBranchesAreSkipped() {
        var patches = build(List.of(item("competence", "Java", null, null, null, List.of())));

        assertThat(at(patches, "/skills")).isPresent();
        assertThat(at(patches, "/qualities")).isEmpty();
        assertThat(at(patches, "/requirements")).isEmpty();
        assertThat(at(patches, "/additional")).isEmpty();
    }

    @Test
    @DisplayName("every item gets its own id, so the page can key on them")
    void itemsCarryDistinctIds() {
        var patches = build(List.of(
                item("competence", "Java", null, null, null, List.of()),
                item("competence", "Spring", null, null, null, List.of())));

        JsonNode skills = at(patches, "/skills").orElseThrow();
        assertThat(skills.get(0).get("id").asText())
                .isNotBlank()
                .isNotEqualTo(skills.get(1).get("id").asText());
    }
}
