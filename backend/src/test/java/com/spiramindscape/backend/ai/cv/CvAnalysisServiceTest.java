package com.spiramindscape.backend.ai.cv;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The pure rules of reading an advert. The model's answer is only ever an input here: what counts
 * as a real quote, what is dropped, and what is merged is decided by these.
 */
class CvAnalysisServiceTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    /** The Advania advert the owner analysed by hand, cut to the lines that matter. */
    private static final String ADVERT = """
            System Developer/Tester på Advania
            Det innebär att du arbetar med att:
            Utveckla och vidareutveckla lösningar i Java
            Arbeta praktiskt med testning som en del av teamets leveranser
            Arbeta med API:er och integrationer
            Vi söker dig som har:
            Praktisk erfarenhet av Java
            Kunskap om Linux
            Det är meriterande om du har erfarenhet av:
            Gradle, Azure DevOps, Postman, Insomnia eller Grafana
            Som person tycker du om att förstå hur saker fungerar och tar ansvar för att få dina uppgifter framåt.
            """;

    private static JsonNode json(String s) throws Exception {
        return MAPPER.readTree(s);
    }

    private static CvAnalysisService.AdvertIndex index() {
        return new CvAnalysisService.AdvertIndex(ADVERT);
    }

    @Test
    @DisplayName("a quote the advert does not contain is dropped; one it does keeps its line number")
    void quotesMustBeInTheAdvert() {
        var index = index();

        assertThat(index.locate("Utveckla och vidareutveckla lösningar i Java")).isNotNull()
                .extracting(CvApplicationService.Quote::line).isEqualTo(3);
        assertThat(index.locate("Erfarenhet av Kubernetes i produktion")).isNull();
        // Punctuation, case and a bullet are not a different line.
        assertThat(index.locate("• praktisk erfarenhet av java.")).isNotNull();
    }

    @Test
    @DisplayName("a competence is a tag and may stand without a quote; a requirement may not")
    void quotesDecideWhatSurvives() throws Exception {
        Set<String> keys = new LinkedHashSet<>();
        var competences = CvAnalysisService.parse(json("""
                {"items":[{"tag":"Java","weight":"must","quotes":["Praktisk erfarenhet av Java"]},
                          {"tag":"CI/CD","weight":"nice","quotes":[]}]}""").path("items"),
                "competence", index(), keys, new LinkedHashSet<>(), "tag");
        assertThat(competences).extracting(CvApplicationService.Item::label).containsExactly("Java", "CI/CD");

        var requirements = CvAnalysisService.parse(json("""
                {"items":[{"demand":"Arbeta med API:er och integrationer",
                           "quotes":["Arbeta med API:er och integrationer"]},
                          {"demand":"Leda ett team på tio personer","quotes":["Leda ett team"]}]}""")
                .path("items"), "requirement", index(), keys, new LinkedHashSet<>(), "demand");
        assertThat(requirements).extracting(CvApplicationService.Item::label)
                .containsExactly("Arbeta med API:er och integrationer");
        assertThat(requirements.get(0).demand()).isEqualTo("Arbeta med API:er och integrationer");
        assertThat(requirements.get(0).quotes()).hasSize(1);
    }

    @Test
    @DisplayName("two items quoting the same line are one item — repetition is emphasis, not a duplicate")
    void repeatsAreMerged() throws Exception {
        Set<String> seen = new LinkedHashSet<>();
        var items = CvAnalysisService.parse(json("""
                {"items":[{"demand":"Utveckla och vidareutveckla lösningar i Java",
                           "quotes":["Utveckla och vidareutveckla lösningar i Java"]},
                          {"demand":"Java-utveckling","quotes":["Utveckla och vidareutveckla lösningar i Java"]}]}""")
                .path("items"), "requirement", index(), new LinkedHashSet<>(), seen, "demand");

        assertThat(items).hasSize(1);
        assertThat(items.get(0).label()).isEqualTo("Utveckla och vidareutveckla lösningar i Java");
    }

    @Test
    @DisplayName("a personal quality keeps the advert's line as its context, so the question carries it")
    void traitsCarryTheirContext() throws Exception {
        var traits = CvAnalysisService.parse(json("""
                {"items":[{"trait":"ansvarstagande",
                           "context":"tar ansvar för att få dina uppgifter framåt",
                           "quotes":["tar ansvar för att få dina uppgifter framåt"]}]}""").path("items"),
                "trait", index(), new LinkedHashSet<>(), new LinkedHashSet<>(), "trait");

        assertThat(traits).singleElement().satisfies(t -> {
            assertThat(t.label()).isEqualTo("ansvarstagande");
            assertThat(t.context()).contains("tar ansvar");
            assertThat(t.category()).isEqualTo("trait");
        });
    }

    @Test
    @DisplayName("an extra note says whether it is letter material or something to raise now")
    void extraNotesAreClassified() throws Exception {
        var extra = CvAnalysisService.parse(json("""
                {"items":[{"note":"Ett mindre team på 5-6 kollegor","use":"letter","quotes":[]},
                          {"note":"Kräver svenskt medborgarskap","use":"flag","quotes":[]}]}""").path("items"),
                "extra", index(), new LinkedHashSet<>(), new LinkedHashSet<>(), "note");

        assertThat(extra).extracting(CvApplicationService.Item::extraUse).containsExactly("letter", "flag");
    }

    @Test
    @DisplayName("keys are unique, so two items with the same label stay two items")
    void keysAreUnique() throws Exception {
        var items = CvAnalysisService.parse(json("""
                {"items":[{"tag":"Java","quotes":["Praktisk erfarenhet av Java"]},
                          {"tag":"Java","quotes":["Kunskap om Linux"]}]}""").path("items"),
                "competence", index(), new LinkedHashSet<>(), new LinkedHashSet<>(), "tag");

        assertThat(items).extracting(CvApplicationService.Item::key).containsExactly("java", "java-2");
    }

    @Test
    @DisplayName("the lines under 'Vi söker dig som har:' read as requirements, to catch what was missed")
    void requirementLines() {
        assertThat(index().requirementLines())
                .contains("Praktisk erfarenhet av Java", "Kunskap om Linux",
                        "Gradle, Azure DevOps, Postman, Insomnia eller Grafana")
                .doesNotContain("System Developer/Tester på Advania");
    }

    @Test
    @DisplayName("JSON is found inside prose or a code fence; anything else is no answer")
    void parseJson() {
        assertThat(CvAnalysisService.parseJson("```json\\n{\"requirements\":[]}\\n```")).isNotNull();
        assertThat(CvAnalysisService.parseJson("Here you go: {\"requirements\": []} hope it helps")).isNotNull();
        assertThat(CvAnalysisService.parseJson("no json")).isNull();
    }

    @Test
    @DisplayName("a line copied from the advert is recognised, so it never becomes a CV line")
    void copiesAdvert() {
        assertThat(CvAnalysisService.copiesAdvert(
                "Arbeta praktiskt med testning som en del av teamets leveranser varje dag", ADVERT)).isTrue();
        assertThat(CvAnalysisService.copiesAdvert("Byggde och testade API:er i Java", ADVERT)).isFalse();
    }

    @Test
    @DisplayName("repositories are found in text and links, and an account link leads to the named repo")
    void repositories() {
        var repos = new LinkedHashSet<CvAnalysisService.RepoRef>();
        CvAnalysisService.findRepos("See https://github.com/anna/spira-mindscape.git and github.com/orgs/x/y", repos);
        assertThat(repos).containsExactly(new CvAnalysisService.RepoRef("anna", "spira-mindscape"));

        String profile = "<a href=\"https://github.com/alakhmakova\">github.com/alakhmakova</a> Spira Mindscape";
        assertThat(CvAnalysisService.accounts(profile)).containsExactly("alakhmakova");
        String list = """
                [{"name":"dotfiles","fork":false,"owner":{"login":"alakhmakova"}},
                 {"name":"spira-mindscape","fork":false,"owner":{"login":"alakhmakova"}},
                 {"name":"spring-petclinic","fork":true,"owner":{"login":"alakhmakova"}}]""";
        assertThat(CvAnalysisService.pickRepos("alakhmakova", list, profile, 3))
                .containsExactly(new CvAnalysisService.RepoRef("alakhmakova", "spira-mindscape"));
        assertThat(CvAnalysisService.pickRepos("alakhmakova", list, "nothing named", 3))
                .containsExactly(new CvAnalysisService.RepoRef("alakhmakova", "dotfiles"),
                        new CvAnalysisService.RepoRef("alakhmakova", "spira-mindscape"));
    }

    @Test
    @DisplayName("dependencies are read from Maven, npm and Gradle build files")
    void dependencies() {
        assertThat(CvAnalysisService.dependenciesOf("backend/pom.xml",
                "<artifactId>spring-boot-starter-web</artifactId><artifactId>jsoup</artifactId>"))
                .contains("spring-boot-starter-web", "jsoup");
        assertThat(CvAnalysisService.dependenciesOf("package.json",
                "{\"dependencies\":{\"react\":\"18\"},\"devDependencies\":{\"vitest\":\"4\"}}"))
                .containsExactly("react", "vitest");
        assertThat(CvAnalysisService.dependenciesOf("android/app/build.gradle.kts",
                "implementation(\"com.apollographql.apollo:apollo-runtime:4.1.0\")"))
                .contains("apollo-runtime");
    }

    @Test
    @DisplayName("repository facts count tests, CI and docs from the file tree")
    void repoFacts() {
        List<String> paths = new ArrayList<>(List.of("backend/pom.xml", "backend/src/test/java/a/FooTest.java",
                "src/lib/x.test.ts", ".github/workflows/ci.yml", "docs/a.md", "specs/b.md"));
        String facts = CvAnalysisService.repoFacts(new CvAnalysisService.RepoRef("o", "spira"), paths,
                List.of("backend/pom.xml"), new LinkedHashSet<>(List.of("spring-boot-starter-web")));

        assertThat(facts).contains("Test files: 2").contains(".github/workflows/ci.yml")
                .contains("docs/: 1 files").contains("spring-boot-starter-web");
    }
}
