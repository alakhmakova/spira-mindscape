package com.spiramindscape.backend.ai.cv;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.spiramindscape.backend.graphql.input.CreateResourceInput;
import com.spiramindscape.backend.graphql.input.MapPatchInput;
import com.spiramindscape.backend.resource.Resource;
import com.spiramindscape.backend.resource.ResourceService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link CvMapService}: which map is created, what an old application's answers become, and — the
 * rule it exists for — that a field she filled is never changed without her say-so.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class CvMapServiceTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final Long APP_ID = 1L;
    private static final Long GOAL_ID = 7L;
    private static final Long MAP_ID = 362L;

    @Mock private CvApplicationService apps;
    @Mock private ResourceService resourceService;

    private CvMapService service;
    private CvApplication app;

    @BeforeEach
    void setUp() {
        service = new CvMapService(apps, resourceService);
        app = new CvApplication();
        app.setId(APP_ID);
        app.setGoalId(GOAL_ID);
        app.setTitle("Backend Developer — Advania");
        app.setVacancyUrl("https://example.com/jobs/backend");
        app.setPhase(CvPhase.MAP);
        when(apps.get(APP_ID)).thenReturn(app);
        when(apps.pruned(APP_ID)).thenReturn(app);
        when(apps.mapOnGoal(any(), any())).thenReturn(Optional.empty());
        when(apps.unclaimedMapOnGoal(any())).thenReturn(Optional.empty());
        when(apps.legacyRows(APP_ID)).thenReturn(List.of());
        when(resourceService.create(eq(GOAL_ID), any(CreateResourceInput.class))).thenReturn(resource(MAP_ID, null));
    }

    private static Resource resource(Long id, String mapData) {
        Resource r = new Resource();
        r.setId(id);
        r.setType("vacancy");
        r.setMapData(mapData);
        return r;
    }

    private static JsonNode json(String s) throws Exception {
        return MAPPER.readTree(s);
    }

    @Test
    @DisplayName("a map she made herself is adopted, not duplicated, and nothing is written into it")
    void adoptsHerOwnFilledMap() {
        Resource hers = resource(999L, """
                {"skills":[{"id":"a","text":"Java","checked":true,"comments":[]}],
                 "requirements":[{"id":"r1","text":"Own a service end to end","important":true,
                    "companies":[{"id":"k1","label":"Advania","text":"Owned the billing API"}]}]}
                """);
        when(apps.unclaimedMapOnGoal(app)).thenReturn(Optional.of(hers));

        Resource used = service.createFromAnalysis(APP_ID, GOAL_ID, null, List.of());

        // Hers is the map this application works on — no second one is created \u2026
        assertThat(used.getId()).isEqualTo(999L);
        verify(resourceService, never()).create(anyLong(), any(CreateResourceInput.class));
        verify(apps).setMapResource(APP_ID, 999L);
        // \u2026 and her answers are left exactly as they are.
        verify(resourceService, never()).patchMap(anyLong(), any());
    }

    @Test
    @DisplayName("an empty map of hers is adopted AND filled from the advert")
    void fillsHerEmptyMap() {
        when(apps.unclaimedMapOnGoal(app)).thenReturn(Optional.of(resource(999L, "{}")));

        Resource used = service.createFromAnalysis(APP_ID, GOAL_ID, null, List.of());

        // Nothing of hers is at stake in an empty map, so the advert goes into it rather than
        // into a second map beside it.
        assertThat(used.getId()).isEqualTo(999L);
        verify(resourceService, never()).create(anyLong(), any(CreateResourceInput.class));
        verify(apps).setMapResource(APP_ID, 999L);
        verify(resourceService).patchMap(eq(999L), any());
    }

    /** A map she has already worked on: an answer, a tick, an empty slot, and one unticked skill. */
    private void existingMap() {
        app.setMapResourceId(MAP_ID);
        when(apps.mapOnGoal(app, MAP_ID)).thenReturn(Optional.of(resource(MAP_ID, """
                {"facts":{"location":"Stockholm","educationNote":""},
                 "skills":[{"id":"a","text":"Java","checked":true,"comments":[]},
                           {"id":"b","text":"Kubernetes","checked":false,"comments":[]}],
                 "requirements":[{"id":"r1","text":"APIs","important":false,
                    "companies":[{"id":"k1","label":"Advania","text":"Owned the billing API"},
                                 {"id":"k2","label":"","text":""}]}]}
                """)));
    }

    @SuppressWarnings("unchecked")
    private List<MapPatchInput> patchesSent() {
        ArgumentCaptor<List<MapPatchInput>> patches = ArgumentCaptor.forClass(List.class);
        verify(resourceService).patchMap(eq(MAP_ID), patches.capture());
        return patches.getValue();
    }

    // ── Creating it ─────────────────────────────────────────────────────────

    @Test
    @DisplayName("the map is a vacancy resource named after the JOB, filled by patches, and recorded")
    void createsTheMap() {
        service.createFromAnalysis(APP_ID, GOAL_ID,
                new CvApplicationService.AnalysisMeta("Backend", List.of(), "Advania", "Stockholm",
                        "sv", "yes", null, "core", "BSc", "3+ years", "Swedish"),
                List.of(new CvApplicationService.Item("competence", "java", "Java", null, null,
                        CvRequirement.Weight.MUST, null, List.of())));

        ArgumentCaptor<CreateResourceInput> input = ArgumentCaptor.forClass(CreateResourceInput.class);
        verify(resourceService).create(eq(GOAL_ID), input.capture());
        assertThat(input.getValue().type()).isEqualTo("vacancy");
        assertThat(input.getValue().title()).isEqualTo("Backend Developer — Advania");
        assertThat(input.getValue().mapData()).isNull();
        assertThat(patchesSent()).extracting(MapPatchInput::path)
                .contains("/facts/location", "/facts/link", "/facts/education", "/skills");
        verify(apps).setMapResource(APP_ID, MAP_ID);
    }

    @Test
    @DisplayName("a map that already exists is never rebuilt over")
    void doesNotRebuildAnExistingMap() {
        existingMap();

        service.createFromAnalysis(APP_ID, GOAL_ID, null, List.of());

        verify(resourceService, never()).create(anyLong(), any());
        verify(resourceService, never()).patchMap(anyLong(), any());
    }

    @Test
    @DisplayName("an application started under the old process keeps its answers when it gets a map")
    void convertsAnOldApplication() throws Exception {
        CvRequirement java = new CvRequirement();
        java.setCluster("java");
        java.setCategory("competence");
        java.setTopic("Java");
        java.setText("Praktisk erfarenhet av Java");
        java.setUserVerdict("partial");
        java.setUserAnswer("only in courses");
        CvRequirement api = new CvRequirement();
        api.setCluster("api");
        api.setCategory("requirement");
        api.setTopic("APIs");
        api.setDemand("Arbeta med API:er");
        api.setText("Arbeta med API:er");
        api.setUserAnswer("GraphQL in my own project");
        when(apps.legacyRows(APP_ID)).thenReturn(List.of(java, api));

        service.ensure(APP_ID, GOAL_ID);

        List<MapPatchInput> patches = patchesSent();
        JsonNode skills = json(patches.stream().filter(p -> p.path().equals("/skills")).findFirst().orElseThrow().value());
        assertThat(skills.get(0).get("checked").asBoolean()).isTrue();
        assertThat(skills.get(0).get("comments").toString()).contains("Partly: only in courses");
        JsonNode requirements = json(patches.stream().filter(p -> p.path().equals("/requirements"))
                .findFirst().orElseThrow().value());
        assertThat(requirements.get(0).get("companies").get(0).get("text").asText())
                .isEqualTo("GraphQL in my own project");
    }

    @Test
    @DisplayName("an application still in the analysis gets no map from `ensure` — the analysis builds it")
    void noMapBeforeTheAnalysis() {
        app.setPhase(CvPhase.ANALYSIS);

        assertThat(service.ensure(APP_ID, GOAL_ID)).isEmpty();
        verify(resourceService, never()).create(anyLong(), any());
    }

    // ── Writing it: never over her work ─────────────────────────────────────

    @Test
    @DisplayName("an empty field is filled, an unticked box ticked, an item appended")
    void fillsWhatIsEmpty() throws Exception {
        existingMap();

        var result = service.write(APP_ID, GOAL_ID, List.of(
                new CvMapService.Write("/requirements/0/companies/1/text", json("\"Built the CI\""), false),
                new CvMapService.Write("/skills/1/checked", json("true"), false),
                new CvMapService.Write("/facts/educationNote", json("\"BSc 2019\""), false),
                new CvMapService.Write("/skills/-", json("{\"text\":\"Docker\"}"), false)));

        assertThat(result.refused()).isEmpty();
        assertThat(result.written()).hasSize(4);
        assertThat(patchesSent()).hasSize(4);
    }

    @Test
    @DisplayName("her answer is NOT replaced without her say-so — and the refusal says what she wrote")
    void refusesToOverwriteHerAnswer() throws Exception {
        existingMap();

        var result = service.write(APP_ID, GOAL_ID, List.of(
                new CvMapService.Write("/requirements/0/companies/0/text", json("\"Something else\""), false)));

        assertThat(result.written()).isEmpty();
        assertThat(result.refused()).singleElement().asString()
                .contains("/requirements/0/companies/0/text")
                .contains("Owned the billing API")
                .contains("replace=true");
        verify(resourceService, never()).patchMap(anyLong(), any());
    }

    @Test
    @DisplayName("her tick is not taken off, and her item not deleted, without her say-so")
    void refusesToUntickOrDelete() throws Exception {
        existingMap();

        var result = service.write(APP_ID, GOAL_ID, List.of(
                new CvMapService.Write("/skills/0/checked", json("false"), false),
                new CvMapService.Write("/requirements/0", null, false),
                new CvMapService.Write("/facts/location", json("\"Malmö\""), false)));

        assertThat(result.written()).isEmpty();
        assertThat(result.refused()).hasSize(3);
    }

    @Test
    @DisplayName("with her agreement (replace=true) her field is changed")
    void replacesWithHerAgreement() throws Exception {
        existingMap();

        var result = service.write(APP_ID, GOAL_ID, List.of(
                new CvMapService.Write("/facts/location", json("\"Malmö\""), true)));

        assertThat(result.written()).containsExactly("/facts/location");
        assertThat(patchesSent()).singleElement()
                .satisfies(p -> assertThat(p.value()).isEqualTo("\"Malmö\""));
    }

    @Test
    @DisplayName("writing the same value she already has is not an overwrite")
    void theSameValueIsFine() throws Exception {
        existingMap();

        var result = service.write(APP_ID, GOAL_ID, List.of(
                new CvMapService.Write("/facts/location", json("\"Stockholm\""), false)));

        assertThat(result.refused()).isEmpty();
    }

    @Test
    @DisplayName("only the map's parts can be written — not the root, not the version")
    void onlyTheMapsParts() throws Exception {
        existingMap();

        var result = service.write(APP_ID, GOAL_ID, List.of(
                new CvMapService.Write("/v", json("2"), true),
                new CvMapService.Write("facts/location", json("\"x\""), true)));

        assertThat(result.written()).isEmpty();
        assertThat(result.refused()).hasSize(2);
    }

    @Test
    @DisplayName("an appended item gets an id and the shape the page expects")
    void appendedItemsAreNormalised() throws Exception {
        existingMap();

        service.write(APP_ID, GOAL_ID, List.of(
                new CvMapService.Write("/requirements/-", json("{\"text\":\"Kubernetes in production\"}"), false)));

        JsonNode added = json(patchesSent().get(0).value());
        assertThat(added.get("id").asText()).isNotBlank();
        assertThat(added.get("important").asBoolean()).isFalse();
        assertThat(added.get("companies")).hasSize(3);
    }

    @Test
    @DisplayName("an id and a timestamp are bookkeeping, not her work")
    void bookkeepingIsNotHerWork() throws Exception {
        assertThat(CvMapService.holdsHerWork(json("{\"id\":\"a\",\"text\":\"\",\"at\":\"x\"}"))).isFalse();
        assertThat(CvMapService.holdsHerWork(json("{\"id\":\"a\",\"checked\":false}"))).isFalse();
        assertThat(CvMapService.holdsHerWork(json("{\"id\":\"a\",\"checked\":true}"))).isTrue();
        assertThat(CvMapService.holdsHerWork(json("[{\"text\":\"\"},{\"text\":\"x\"}]"))).isTrue();
    }

    @Test
    @DisplayName("the kind of an item is read off its path, not its top-level part")
    void kindOfPath() {
        assertThat(CvMapService.kindOf("/skills/-")).isEqualTo("check");
        assertThat(CvMapService.kindOf("/skills/0/comments/-")).isEqualTo("comment");
        assertThat(CvMapService.kindOf("/requirements/-")).isEqualTo("requirement");
        assertThat(CvMapService.kindOf("/requirements/0/companies/-")).isEqualTo("company");
        assertThat(CvMapService.kindOf("/company/comments/-")).isEqualTo("note");
        assertThat(CvMapService.kindOf("/facts/location")).isEmpty();
    }
}
