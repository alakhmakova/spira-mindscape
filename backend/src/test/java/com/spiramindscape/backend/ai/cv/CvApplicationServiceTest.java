package com.spiramindscape.backend.ai.cv;

import com.spiramindscape.backend.ai.chat.UrlReadService;
import com.spiramindscape.backend.ai.cv.dto.CvApplicationDto;
import com.spiramindscape.backend.auth.AppUser;
import com.spiramindscape.backend.auth.CurrentUserProvider;
import com.spiramindscape.backend.goal.Goal;
import com.spiramindscape.backend.goal.GoalRepository;
import com.spiramindscape.backend.resource.Resource;
import com.spiramindscape.backend.resource.ResourceRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

/**
 * {@link CvApplicationService} owns an application's state and reads its vacancy map
 * (specs/2026-09-17-vacancy-map/). These tests pin what would silently ruin the process: the
 * analysis recorded in the wrong phase, the work landing anywhere but the map, progress read from
 * anything but the map, and a step that can be skipped.
 */
@ExtendWith(MockitoExtension.class)
class CvApplicationServiceTest {

    private static final Long USER_ID = 42L;
    private static final Long GOAL_ID = 7L;
    private static final Long APP_ID = 1L;
    private static final Long MAP_ID = 362L;

    @Mock private GoalRepository goalRepository;
    @Mock private CurrentUserProvider currentUserProvider;
    @Mock private CvApplicationRepository applications;
    @Mock private CvRequirementRepository requirements;
    @Mock private UrlReadService urlReadService;
    @Mock private ResourceRepository resources;
    @Mock private CvSourceReadRepository sourceReads;

    private CvApplicationService service;
    private CvApplication app;

    @BeforeEach
    void setUp() {
        AppUser user = new AppUser();
        user.setId(USER_ID);
        lenient().when(currentUserProvider.getCurrentUser()).thenReturn(user);
        lenient().when(goalRepository.findByIdAndUserId(GOAL_ID, USER_ID)).thenReturn(Optional.of(new Goal()));

        app = new CvApplication();
        app.setId(APP_ID);
        app.setAppUserId(USER_ID);
        app.setGoalId(GOAL_ID);
        app.setTitle("System Developer/Tester på Advania");
        app.setVacancyText("…");
        app.setPhase(CvPhase.ANALYSIS);

        lenient().when(applications.findByIdAndAppUserId(APP_ID, USER_ID)).thenReturn(Optional.of(app));
        lenient().when(applications.save(any())).thenAnswer(i -> i.getArgument(0));
        // By default every id asked about exists on this goal — the tests that care override it.
        lenient().when(resources.findExistingIds(eq(GOAL_ID), anyList())).thenAnswer(i -> i.getArgument(1));

        service = new CvApplicationService(applications, requirements, goalRepository,
                currentUserProvider, urlReadService, resources, sourceReads);
    }

    // ── fixtures ─────────────────────────────────────────────────────────────

    private Goal goal() {
        Goal goal = new Goal();
        goal.setId(GOAL_ID);
        return goal;
    }

    private Resource noteOnGoal(long id, String title, String body) {
        Resource r = new Resource();
        r.setId(id);
        r.setType("note");
        r.setTitle(title);
        r.setBody(body);
        r.setGoal(goal());
        lenient().when(resources.findById(id)).thenReturn(Optional.of(r));
        return r;
    }

    /** A map with two requirements, one answered at one employer; one ticked skill of two. */
    private Resource mapOnGoal() {
        Resource r = new Resource();
        r.setId(MAP_ID);
        r.setType("vacancy");
        r.setTitle("System Developer/Tester på Advania");
        r.setGoal(goal());
        r.setMapData("""
                {"skills":[{"id":"a","text":"Java","checked":true,"comments":[]},
                           {"id":"b","text":"Linux","checked":false,"comments":[]}],
                 "qualities":[{"id":"q","text":"ansvarstagande","checked":false,"comments":[]}],
                 "requirements":[
                   {"id":"r1","text":"Arbeta med API:er","important":true,
                    "companies":[{"id":"k1","label":"Zocom","text":"GraphQL i eget projekt"}]},
                   {"id":"r2","text":"Delta i code reviews","important":false,
                    "companies":[{"id":"k2","label":"","text":""}]}]}
                """);
        lenient().when(resources.findById(MAP_ID)).thenReturn(Optional.of(r));
        app.setMapResourceId(MAP_ID);
        return r;
    }

    private static CvApplicationService.AnalysisMeta advania() {
        return new CvApplicationService.AnalysisMeta(
                "System Developer/Tester", List.of("Systemutvecklare"), "Advania", "Malmö",
                "sv", "unspecified", null, "Kombinationen av utveckling och test");
    }

    // ── The analysis ─────────────────────────────────────────────────────────

    @Test
    @DisplayName("the advert's own facts are recorded, with its hard conditions kept one per line")
    void theAnalysisIsRecorded() {
        service.recordAnalysis(APP_ID, advania(), "done",
                List.of("Kräver svenskt medborgarskap", "  ", "Säkerhetsprövning"));

        assertThat(app.getRoleTitle()).isEqualTo("System Developer/Tester");
        assertThat(app.getCompanyName()).isEqualTo("Advania");
        assertThat(app.getLocation()).isEqualTo("Malmö");
        assertThat(app.getCoreMessage()).contains("utveckling och test");
        // An advert that does not mention a letter gets one: a personligt brev is the Swedish norm.
        assertThat(app.getLetterRequired()).isTrue();
        assertThat(app.getAnalysisState()).isEqualTo("done");
        assertThat(CvApplicationService.conditionsOf(app))
                .containsExactly("Kräver svenskt medborgarskap", "Säkerhetsprövning");
    }

    @Test
    @DisplayName("the analysis is only recorded in step 1 — once the map exists, re-reading would discard her work")
    void theAnalysisIsOnlyRecordedInStepOne() {
        app.setPhase(CvPhase.MAP);

        assertThatThrownBy(() -> service.recordAnalysis(APP_ID, advania(), "done", List.of()))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("only analysed in step 1");
    }

    @Test
    @DisplayName("the analysis hands the work to the vacancy map — not to a form, not to a checklist")
    void analysisEndsOnTheMap() {
        service.recordAnalysis(APP_ID, advania(), "done", List.of());

        service.finishAnalysis(APP_ID);

        assertThat(app.getPhase()).isEqualTo(CvPhase.MAP);
        assertThat(app.getAnalysisState()).isEqualTo("done");
    }

    @Test
    @DisplayName("an advert that stated no requirements keeps saying so after the move to the map")
    void anEmptyAdvertStaysEmpty() {
        service.recordAnalysis(APP_ID, advania(), "empty", List.of());

        service.finishAnalysis(APP_ID);

        assertThat(app.getPhase()).isEqualTo(CvPhase.MAP);
        assertThat(app.getAnalysisState()).isEqualTo("empty");
    }

    // ── Reading the map ──────────────────────────────────────────────────────

    @Test
    @DisplayName("progress is the map's requirements: how many have an answer under an employer")
    void progressComesFromTheMap() {
        mapOnGoal();
        app.setPhase(CvPhase.MAP);

        CvApplicationDto dto = service.getForClient(APP_ID);

        assertThat(dto.total()).isEqualTo(2);
        assertThat(dto.answered()).isEqualTo(1);
        assertThat(dto.openTopics()).isEqualTo(1);
        assertThat(dto.competences()).isEqualTo(2);
        assertThat(dto.mapResourceId()).isEqualTo(MAP_ID);
        assertThat(dto.step()).isEqualTo(2);
        assertThat(dto.steps()).isEqualTo(4);
    }

    @Test
    @DisplayName("the step messages get the map's counts and a real link to it")
    void factsComeFromTheMap() {
        mapOnGoal();
        app.setPhase(CvPhase.MAP);
        app.setAdvertConditions("Kräver svenskt medborgarskap");

        CvTransitions.Facts facts = service.facts(APP_ID);

        assertThat(facts.competences()).isEqualTo(2);
        assertThat(facts.requirements()).isEqualTo(2);
        assertThat(facts.cores()).isEqualTo(1);
        assertThat(facts.open()).isEqualTo(1);
        assertThat(facts.flags()).containsExactly("Kräver svenskt medborgarskap");
        assertThat(facts.mapLink()).isEqualTo("/goals/7?resource=362");
        assertThat(facts.mapNoteTitle()).isEqualTo("System Developer/Tester på Advania");
    }

    @Test
    @DisplayName("what she wrote on her map is part of the corpus the gates check a document against")
    void herMapIsInTheCorpus() {
        mapOnGoal();

        assertThat(service.evidenceCorpus(APP_ID)).contains("GraphQL i eget projekt");
    }

    @Test
    @DisplayName("an application with no map reads as an empty one, not as an error")
    void noMapReadsAsEmpty() {
        assertThat(service.mapDocument(app).isObject()).isTrue();
        assertThat(service.getForClient(APP_ID).total()).isZero();
    }

    @Test
    @DisplayName("a note is never taken for the map, and the map is never taken for a note")
    void theMapAndNotesAreNotConfused() {
        mapOnGoal();
        noteOnGoal(500L, "Mina uppgifter", "<p>x</p>");

        assertThat(service.mapOnGoal(app, 500L)).isEmpty();
        assertThat(service.noteOnGoal(app, MAP_ID)).isEmpty();
        assertThat(service.mapOnGoal(app, MAP_ID)).isPresent();
    }

    // ── Her details ──────────────────────────────────────────────────────────

    @Test
    @DisplayName("an existing note can be adopted as her details; a note of another goal cannot")
    void adoptingAnIntakeNote() {
        noteOnGoal(501L, "Profil", "<p>x</p>");
        service.useAsIntake(APP_ID, 501L);
        assertThat(app.getProfileNoteId()).isEqualTo(501L);

        lenient().when(resources.findById(999L)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.useAsIntake(APP_ID, 999L))
                .isInstanceOf(ResponseStatusException.class).hasMessageContaining("404");
    }

    @Test
    @DisplayName("there is one details note: a second approved note is not bound over it")
    void oneIntakeNote() {
        app.setProfileNoteId(500L);

        service.bindApprovedNote(APP_ID, 501L, "intake");

        assertThat(app.getProfileNoteId()).isEqualTo(500L);
    }

    @Test
    @DisplayName("a note approved during the map step is her details")
    void aNoteDuringTheMapIsHerDetails() {
        app.setPhase(CvPhase.MAP);

        service.bindApprovedNote(APP_ID, 777L, null);

        assertThat(app.getProfileNoteId()).isEqualTo(777L);
    }

    @Test
    @DisplayName("a note is bound to the document its proposal was for, whatever the step is now")
    void notesBindByDocument() {
        app.setPhase(CvPhase.MAP);

        service.bindApprovedNote(APP_ID, 777L, "cv");

        assertThat(app.getCvNoteId()).isEqualTo(777L);
    }

    @Test
    @DisplayName("a new application starts from the goal's existing details note — one per person")
    void theIntakeFormIsInherited() {
        CvApplication previous = new CvApplication();
        previous.setProfileNoteId(11L);
        when(applications.findByAppUserIdAndGoalIdOrderByUpdatedAtDesc(USER_ID, GOAL_ID))
                .thenReturn(List.of(previous));

        CvApplication created = service.create(GOAL_ID, null, null, "System Developer\nJava", null);

        assertThat(created.getProfileNoteId()).isEqualTo(11L);
        assertThat(created.getPhase()).isEqualTo(CvPhase.ANALYSIS);
    }

    // ── The phase machine ────────────────────────────────────────────────────

    @Test
    @DisplayName("nothing moves out of the analysis by request — the server moves it")
    void theAnalysisCannotBeSkipped() {
        assertThatThrownBy(() -> service.advancePhase(APP_ID, CvPhase.MAP))
                .isInstanceOf(ResponseStatusException.class).hasMessageContaining("analysis");
    }

    @Test
    @DisplayName("the map goes to the CV, never past it; and the CV can always go back to the map")
    void theMapToTheCv() {
        app.setPhase(CvPhase.MAP);
        assertThatThrownBy(() -> service.advancePhase(APP_ID, CvPhase.CV_REVIEW))
                .isInstanceOf(ResponseStatusException.class);

        assertThat(service.advancePhase(APP_ID, CvPhase.DRAFT).getPhase()).isEqualTo(CvPhase.DRAFT);
        // Backwards is always allowed: remembering another example is normal, not an error.
        assertThat(service.advancePhase(APP_ID, CvPhase.MAP).getPhase()).isEqualTo(CvPhase.MAP);
    }

    @Test
    @DisplayName("a phase name nobody recognises does not parse; every retired collection step is the map")
    void phaseNames() {
        assertThat(CvPhase.parse("nonsense")).isEmpty();
        assertThat(CvPhase.from("nonsense")).isEqualTo(CvPhase.ANALYSIS);
        for (String retired : List.of("profile", "intake", "competencies", "core", "requirements",
                "traits", "evidence")) {
            assertThat(CvPhase.parse(retired)).as(retired).contains(CvPhase.MAP);
        }
        assertThat(CvPhase.parse("deconstruct")).contains(CvPhase.ANALYSIS);
        assertThat(CvPhase.parse("summary")).contains(CvPhase.DRAFT);
    }

    @Test
    @DisplayName("the CV may finish straight from the review when no letter is wanted")
    void reviewMayFinishWithoutALetter() {
        app.setPhase(CvPhase.CV_REVIEW);

        assertThat(service.advancePhase(APP_ID, CvPhase.DONE).getPhase()).isEqualTo(CvPhase.DONE);
    }

    @Test
    @DisplayName("the letter decision is recorded and moves nothing")
    void theLetterDecisionMovesNothing() {
        app.setPhase(CvPhase.CV_REVIEW);

        service.setLetterRequired(APP_ID, false, "  Anna Berg  ");

        assertThat(app.getLetterRequired()).isFalse();
        assertThat(app.getContactName()).isEqualTo("Anna Berg");
        assertThat(app.getPhase()).isEqualTo(CvPhase.CV_REVIEW);
    }

    @Test
    @DisplayName("re-reading the advert is refused once a map exists — it is her page now")
    void rereadingWithAMapIsRefused() {
        mapOnGoal();
        app.setVacancyUrl("https://jobs.example.com/1");
        app.setPhase(CvPhase.MAP);

        assertThatThrownBy(() -> service.rereadVacancy(APP_ID))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("already has a map");
    }

    // ── The conversation ─────────────────────────────────────────────────────

    @Test
    @DisplayName("a transcript save based on an older copy is refused, so a stale device cannot erase newer turns")
    void aStaleTranscriptIsRefused() {
        app.setTranscriptRevision(3);

        assertThatThrownBy(() -> service.saveTranscript(APP_ID, "[{\"id\":\"1\"}]", 2L))
                .isInstanceOf(ResponseStatusException.class);
        service.saveTranscript(APP_ID, "[{\"id\":\"1\"}]", 3L);

        assertThat(app.getTranscriptRevision()).isEqualTo(4L);
    }

    @Test
    @DisplayName("a blank transcript reads back as nothing stored")
    void transcriptStartsAndClearsAsNull() {
        service.saveTranscript(APP_ID, "   ");

        assertThat(app.getTranscript()).isNull();
    }

    @Test
    @DisplayName("another user's application is a 404 — an id from the client is not a permission")
    void foreignApplicationIsNotFound() {
        when(applications.findByIdAndAppUserId(APP_ID, USER_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.get(APP_ID))
                .isInstanceOf(ResponseStatusException.class).hasMessageContaining("404");
    }

    @Test
    @DisplayName("an application needs either a link or the advert's text")
    void creationNeedsOneOrTheOther() {
        assertThatThrownBy(() -> service.create(GOAL_ID, null, null, null, null))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("either a link to the vacancy or its text");
    }

    @Test
    @DisplayName("a link alone is enough — the server fetches the advert rather than demanding it twice")
    void aLinkAloneIsEnough() {
        when(urlReadService.fetch("https://jobs.example.com/1"))
                .thenReturn(new UrlReadService.Fetched("QA-testare\nVi söker…", true));

        CvApplication created = service.create(GOAL_ID, null, "https://jobs.example.com/1", null, "sv");

        assertThat(created.getVacancyText()).contains("Vi söker");
        assertThat(created.getTitle()).isEqualTo("QA-testare");
    }

    @Test
    @DisplayName("a link that cannot be read fails HERE, with the reason, not halfway through a session")
    void anUnreadableLinkFailsAtCreation() {
        when(urlReadService.fetch("https://jobs.example.com/2"))
                .thenReturn(new UrlReadService.Fetched("The page returned HTTP 403", false));

        assertThatThrownBy(() -> service.create(GOAL_ID, null, "https://jobs.example.com/2", null, null))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("HTTP 403");
    }

    @Test
    @DisplayName("a note or map the user deleted stops being announced")
    void deletedResourcesArePruned() {
        app.setProfileNoteId(500L);
        app.setCvNoteId(600L);
        app.setMapResourceId(MAP_ID);
        when(resources.findExistingIds(eq(GOAL_ID), anyList())).thenReturn(List.of(600L));

        service.pruned(APP_ID);

        assertThat(app.getProfileNoteId()).isNull();
        assertThat(app.getMapResourceId()).isNull();
        assertThat(app.getCvNoteId()).isEqualTo(600L);
    }
}
