package com.spiramindscape.backend.ai.chat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.spiramindscape.backend.ai.chat.dto.ChatRequest;
import com.spiramindscape.backend.ai.cv.CvAnalysisService;
import com.spiramindscape.backend.ai.cv.CvApplication;
import com.spiramindscape.backend.ai.cv.CvApplicationService;
import com.spiramindscape.backend.ai.cv.CvMapService;
import com.spiramindscape.backend.ai.cv.CvPhase;
import com.spiramindscape.backend.ai.cv.CvTransitions;
import com.spiramindscape.backend.ai.grow.GoalMemoryService;
import com.spiramindscape.backend.ai.key.AiKeyService;
import com.spiramindscape.backend.ai.prompt.PromptResources;
import com.spiramindscape.backend.ai.provider.LlmProvider;
import com.spiramindscape.backend.ai.provider.LlmProviderFactory;
import com.spiramindscape.backend.ai.provider.ProviderType;
import com.spiramindscape.backend.ai.provider.ToolCall;
import com.spiramindscape.backend.ai.provider.ToolSpec;
import com.spiramindscape.backend.ai.provider.cohere.CohereVisionReader;
import com.spiramindscape.backend.ai.provider.mistral.MistralOcrService;
import com.spiramindscape.backend.ai.proposal.AiProposalService;
import com.spiramindscape.backend.ai.safety.AbuseAuditLogger;
import com.spiramindscape.backend.ai.safety.SafetyService;
import com.spiramindscape.backend.ai.safety.SafetyVerdict;
import com.spiramindscape.backend.ai.search.TavilySearchService;
import com.spiramindscape.backend.goal.GoalService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.after;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * The CV path in {@link AiChatService}, run on the vacancy map (specs/2026-09-17-vacancy-map/).
 *
 * <p>What is tested here is what the prompt files cannot promise on their own:
 *
 * <ul>
 *   <li><b>The advert is read by the server</b>, never by the model.</li>
 *   <li><b>The map is the agenda, and she leads it</b> — the whole map with its paths reaches the
 *       coach, and nothing hands it one item per turn any more.</li>
 *   <li><b>Only she can end the map</b>: the move to the CV is refused on a turn she did not write.</li>
 *   <li><b>Each step carries only its own tools.</b></li>
 * </ul>
 *
 * <p>{@link PromptResources} is the real object, not a mock — otherwise these assertions only prove
 * that a mock returns what it was told to.
 */
@ExtendWith(MockitoExtension.class)
class AiChatServiceCvTest {

    private static final Long APP_ID = 55L;
    private static final ObjectMapper MAPPER = new ObjectMapper();

    /** Headings from the prompt files; changing one means changing that file. */
    private static final String WRITER_MARKER = "# How the interview runs";
    private static final String FORMAT_MARKER = "# Building the CV";
    private static final String LETTER_MARKER = "# The covering letter";
    private static final String COACH_MARKER = "# The arc of the session";

    @Mock private SafetyService safety;
    @Mock private AbuseAuditLogger abuseAuditLogger;
    @Mock private AiKeyService keyService;
    @Mock private LlmProviderFactory providerFactory;
    @Mock private GoalContextBuilder goalContextBuilder;
    @Mock private TavilySearchService searchService;
    @Mock private AiProposalService proposalService;
    @Mock private ResourceReadService resourceReadService;
    @Mock private UrlReadService urlReadService;
    @Mock private GoalMemoryService goalMemory;
    @Mock private MistralOcrService mistralOcr;
    @Mock private CohereVisionReader cohereVision;
    @Mock private GoalService goalService;
    @Mock private CvApplicationService cvApplications;
    @Mock private CvAnalysisService cvAnalysis;
    @Mock private CvMapService cvMaps;
    @Mock private LlmProvider provider;

    private AiChatService service;
    private CvApplication app;

    @BeforeEach
    void setUp() throws Exception {
        service = new AiChatService(safety, abuseAuditLogger, keyService, providerFactory,
                goalContextBuilder, searchService, proposalService, resourceReadService,
                urlReadService, new PromptResources(), goalMemory, mistralOcr, cohereVision,
                goalService, cvApplications, cvAnalysis, cvMaps);
        lenient().when(safety.classify(anyString())).thenReturn(SafetyVerdict.ALLOWED);
        lenient().when(safety.referInstruction(any())).thenReturn("");
        lenient().when(goalService.isOwnedByCurrentUser(any())).thenReturn(true);
        lenient().when(goalContextBuilder.build(any())).thenReturn("");
        lenient().when(goalMemory.memoryBlock(any())).thenReturn("");
        lenient().when(keyService.getKey(ProviderType.ANTHROPIC))
                .thenReturn(Optional.of(new AiKeyService.StoredKey("chat-key", "claude")));
        lenient().when(providerFactory.create(eq(ProviderType.ANTHROPIC), anyString(), anyString()))
                .thenReturn(provider);

        app = new CvApplication();
        app.setId(APP_ID);
        app.setGoalId(7L);
        app.setTitle("System Developer/Tester på Advania");
        app.setRoleTitle("System Developer/Tester");
        app.setCompanyName("Advania");
        app.setLocation("Malmö");
        app.setVacancyText("Vi söker en QA-testare …");
        app.setVacancyLanguage("sv");
        app.setConversationLanguage("ru");
        app.setAnalysisState("done");
        app.setAnnouncedStep(2);
        app.setPhase(CvPhase.MAP);
        lenient().when(cvApplications.get(APP_ID)).thenReturn(app);
        // The state block reads the PRUNED application — one whose pointers to deleted notes have
        // been cleared — so a fixture that stubs only `get` renders nothing.
        lenient().when(cvApplications.pruned(APP_ID)).thenReturn(app);
        lenient().when(cvApplications.mapDocument(any())).thenReturn(map());
        lenient().when(cvApplications.sourceReads(APP_ID)).thenReturn(List.of());
        lenient().when(cvApplications.noteOnGoal(any(), any())).thenReturn(Optional.empty());
        lenient().when(cvApplications.intakeCandidate(any())).thenReturn(Optional.empty());
        lenient().when(cvApplications.facts(APP_ID)).thenReturn(facts());
    }

    /** A small map: one ticked skill, one unticked, a requirement answered at one employer, one open. */
    private static JsonNode map() throws Exception {
        return MAPPER.readTree("""
                {"facts":{"location":"Malmö","education":"Högskoleexamen","educationNote":"BSc 2019"},
                 "skills":[{"id":"a","text":"Java","checked":true,"comments":[]},
                           {"id":"b","text":"Kubernetes","checked":false,"comments":[]}],
                 "qualities":[{"id":"q","text":"ansvarstagande","checked":true,
                               "comments":[{"id":"c","text":"Tog över releasen när teamledaren var sjuk","at":"x"}]}],
                 "requirements":[
                   {"id":"r1","text":"Arbeta med API:er och integrationer","important":true,
                    "companies":[{"id":"k1","label":"Advania","text":"GraphQL-API i eget projekt"},
                                 {"id":"k2","label":"","text":""}]},
                   {"id":"r2","text":"Delta i code reviews","important":false,
                    "companies":[{"id":"k3","label":"","text":""}]}]}
                """);
    }

    private static CvTransitions.Facts facts() {
        return new CvTransitions.Facts("System Developer/Tester på Advania", "System Developer/Tester",
                "Advania", "Malmö", "Kombinationen av utveckling och test", "exists", "Мои данные",
                null, List.of(), 2, 1, 2, 1, List.of(), 1, "System Developer/Tester på Advania", null, true,
                "/goals/7?resource=362");
    }

    private static ChatRequest cvRequest(Long applicationId) {
        return new ChatRequest(7L, "Вот моя история", "ANTHROPIC", "cv",
                List.of(), null, null, null, applicationId);
    }

    private static ChatRequest controlRequest(String control) {
        return new ChatRequest(7L, "[Continue]", "ANTHROPIC", "cv",
                List.of(), null, null, null, APP_ID, control, "ru-RU");
    }

    /**
     * One turn, and then what the provider was actually given. The recorded invocations are cleared
     * FIRST, so a test that changes the phase and calls again reads its own turn's prompt.
     */
    private String promptFor(Long applicationId) {
        clearInvocations(provider);
        service.chat(cvRequest(applicationId));
        return capturedSystemPrompt();
    }

    private String capturedSystemPrompt() {
        ArgumentCaptor<String> systemPrompt = ArgumentCaptor.forClass(String.class);
        verify(provider, timeout(2000).atLeastOnce()).streamChat(
                anyList(), systemPrompt.capture(), anyList(), any(), any(), any(), any());
        List<String> all = systemPrompt.getAllValues();
        return all.get(all.size() - 1);
    }

    @SuppressWarnings("unchecked")
    private List<ToolSpec> capturedTools() {
        ArgumentCaptor<List<ToolSpec>> tools = ArgumentCaptor.forClass(List.class);
        verify(provider, timeout(2000).atLeastOnce()).streamChat(
                anyList(), anyString(), tools.capture(), any(), any(), any(), any());
        List<List<ToolSpec>> all = tools.getAllValues();
        return all.get(all.size() - 1);
    }

    private List<String> toolNamesFor(Long applicationId) {
        promptFor(applicationId);
        return capturedTools().stream().map(ToolSpec::name).toList();
    }

    /** The model's first turn calls {@code tool} with {@code args}; its second says "ok". */
    @SuppressWarnings("unchecked")
    private void modelCallsOnce(String tool, String args) {
        AtomicInteger turn = new AtomicInteger();
        doAnswer(inv -> {
            Object[] a = inv.getArguments();
            if (turn.getAndIncrement() == 0) {
                ((Consumer<ToolCall>) a[4]).accept(new ToolCall("c1", tool, args));
            } else {
                ((Consumer<String>) a[3]).accept("ok");
            }
            ((Runnable) a[5]).run();
            return null;
        }).when(provider).streamChat(anyList(), anyString(), anyList(), any(), any(), any(), any());
    }

    // ── Which method the coach gets ──────────────────────────────────────────

    @Test
    @DisplayName("a CV session gets the writer's method, and never the GROW coach's")
    void writerMethodNotCoachMethod() {
        assertThat(promptFor(APP_ID)).contains(WRITER_MARKER).doesNotContain(COACH_MARKER);
    }

    @Test
    @DisplayName("while the map is being filled the CV's format is NOT in context — that invites writing")
    void formatIsAbsentOnTheMap() {
        assertThat(promptFor(APP_ID)).doesNotContain(FORMAT_MARKER).doesNotContain(LETTER_MARKER);
    }

    @Test
    @DisplayName("the CV's format arrives when the CV is actually being written, the letter's last")
    void formatArrivesForTheDraft() {
        app.setPhase(CvPhase.DRAFT);
        assertThat(promptFor(APP_ID)).contains(FORMAT_MARKER).doesNotContain(LETTER_MARKER);

        app.setPhase(CvPhase.LETTER);
        assertThat(promptFor(APP_ID)).contains(LETTER_MARKER);
    }

    // ── The four steps, and who leads the map ────────────────────────────────

    @Test
    @DisplayName("every turn is told the step the user sees, and the language to talk in")
    void theStepAndLanguageReachThePrompt() {
        assertThat(promptFor(APP_ID))
                .contains("Step 2 of 4 — The vacancy map")
                .contains("Talk to the user in: Russian");
    }

    @Test
    @DisplayName("the plumbing states the new process: four steps, she leads the map, one part at a time")
    void theProcessIsStated() {
        assertThat(promptFor(APP_ID))
                .contains("1 Job analysis · 2 The vacancy map · 3 Your CV · 4 Cover letter")
                .contains("SHE leads it")
                .contains("ONE part at a time")
                .contains("Never march her through the parts in an order of your own")
                .doesNotContain("record_answer").doesNotContain("record_verdicts")
                .doesNotContain("cv_confirm_intake");
    }

    @Test
    @DisplayName("the coach can explain what each part of the map becomes in the CV")
    void theMappingToTheCvIsInThePrompt() {
        assertThat(promptFor(APP_ID))
                .contains("tick = she has it → the competence block")
                .contains("bullet under THAT employer")
                .contains("the letter's motivation");
    }

    @Test
    @DisplayName("the advert is not re-sent, and the coach is told never to ask for it")
    void theAdvertIsKnownToExistWithoutBeingResent() {
        assertThat(promptFor(APP_ID))
                .contains("is stored with this application")
                .contains("NEVER ask the user to paste it")
                .doesNotContain("Vi söker en QA-testare");
    }

    @Test
    @DisplayName("the whole map reaches the coach with its paths, fenced as her data")
    void theMapReachesThePromptWithPaths() {
        String prompt = promptFor(APP_ID);

        assertThat(prompt)
                .contains("MAP — her own page")
                .contains("\"Java\" · ticked")
                .contains("\"Kubernetes\" · not ticked")
                .contains("VERY IMPORTANT")
                .contains("(/requirements/0/companies/0)")
                .contains("GraphQL-API i eget projekt")
                .contains("Company 2 (unnamed): (empty)")
                .doesNotContain("CURRENT ITEM");
        int herText = prompt.indexOf("GraphQL-API i eget projekt");
        assertThat(prompt.lastIndexOf("<<UNTRUSTED_CONTENT>>", herText)).isGreaterThan(0);
    }

    @Test
    @DisplayName("the map's counts reach the prompt, so 'I have read the advert' has facts under it")
    void theMapCounts() {
        assertThat(promptFor(APP_ID))
                .contains("Vacancy map: 2 skills (1 ticked), 2 requirements (1 with an answer)");
    }

    @Test
    @DisplayName("a map that was never built is stated as a fact")
    void anUnbuiltMapIsStated() {
        app.setAnalysisState("failed");

        assertThat(promptFor(APP_ID))
                .contains("Vacancy map: NOT built")
                .contains("Never say you have read the advert");
    }

    @Test
    @DisplayName("an advert with no requirements puts the advert, fenced, in front of the coach on the map step")
    void anAdvertWithNoRequirements() {
        app.setAnalysisState("empty");

        assertThat(promptFor(APP_ID))
                .contains("THE ADVERT states no requirements")
                .contains("Vi söker en QA-testare");
    }

    @Test
    @DisplayName("her details are one part of the map step — spelled out, with what not to take from an old CV")
    void herDetailsAreAPart() {
        String prompt = promptFor(APP_ID);

        assertThat(prompt)
                .contains("HER DETAILS (a part of the map step")
                .contains("cv_document=intake")
                .contains("NO description of the work");
    }

    @Test
    @DisplayName("the move to the CV is gated on her word, in the block as well as the plumbing")
    void theMoveToTheCvIsHers() {
        assertThat(promptFor(APP_ID))
                .contains("ONLY after she has said the map is finished AND agreed to start the CV");
    }

    // ── The material the CV is written from ─────────────────────────────────

    @Test
    @DisplayName("the CV is written from the map: ticked skills, her answers by employer, and the gaps")
    void theMaterialBlock() {
        app.setPhase(CvPhase.DRAFT);

        String prompt = promptFor(APP_ID);

        assertThat(prompt)
                .contains("MATERIAL")
                .contains("Skills she HAS").contains("Java")
                .contains("Skills she has NOT ticked").contains("Kubernetes")
                .contains("at Advania: GraphQL-API i eget projekt")
                .contains("Delta i code reviews").contains("no answer — leave it OUT")
                .contains("Tog över releasen")
                .contains("NEXT ACTION: read the sources she pointed at")
                .doesNotContain("MAP — her own page");
    }

    // ── Tools per step ───────────────────────────────────────────────────────

    @Test
    @DisplayName("the map step carries map_write and the move, and none of the retired queue tools")
    void mapTools() {
        assertThat(toolNamesFor(APP_ID))
                .contains("map_write", "cv_phase_done", "read_resource", "propose_goal_change")
                .doesNotContain("record_verdicts", "record_answer", "no_answer", "cv_confirm_intake",
                        "record_requirements");
    }

    @Test
    @DisplayName("the writing steps keep the phase move and the letter decision, and not map_write")
    void writingToolsPerStep() {
        app.setPhase(CvPhase.CV_REVIEW);

        assertThat(toolNamesFor(APP_ID))
                .contains("cv_phase_done", "record_letter_decision", "propose_goal_change")
                .doesNotContain("map_write");
    }

    @Test
    @DisplayName("a CV session has no end_session and is never offered web_search")
    void noCoachToolsAndNoSearch() {
        when(keyService.getKey(ProviderType.TAVILY))
                .thenReturn(Optional.of(new AiKeyService.StoredKey("tavily-key", "")));

        List<String> names = toolNamesFor(APP_ID);

        assertThat(names).doesNotContain("end_session", "web_search");
        assertThat(names).contains("read_url");
    }

    @Test
    @DisplayName("ordinary chat never gets the CV tools")
    void plainChatHasNoCvTools() {
        service.chat(new ChatRequest(7L, "hello", "ANTHROPIC", "chat", List.of(), null, null));

        assertThat(capturedTools()).extracting(ToolSpec::name)
                .doesNotContain("map_write", "cv_phase_done", "record_letter_decision");
    }

    // ── Only she ends the map ───────────────────────────────────────────────

    @Test
    @DisplayName("the move from the map to the CV is refused on a turn she did not write")
    void theMapCannotBeLeftOnAControlTurn() {
        modelCallsOnce("cv_phase_done", "{\"next\":\"draft\"}");

        service.chat(controlRequest("continue"));

        verify(provider, timeout(2000).times(2))
                .streamChat(anyList(), anyString(), anyList(), any(), any(), any(), any());
        verify(cvApplications, never()).advancePhase(anyLong(), any());
    }

    @Test
    @DisplayName("on a turn she wrote, the move to the CV goes through")
    void theMapIsLeftOnHerWord() {
        modelCallsOnce("cv_phase_done", "{\"next\":\"draft\"}");

        service.chat(cvRequest(APP_ID));

        verify(cvApplications, timeout(2000)).advancePhase(APP_ID, CvPhase.DRAFT);
    }

    @Test
    @DisplayName("map_write goes to the map service with her words and the replace flag as sent")
    void mapWriteReachesTheMapService() {
        when(cvMaps.write(eq(APP_ID), eq(7L), anyList()))
                .thenReturn(new CvMapService.WriteResult(List.of("/skills/1/checked"), List.of()));
        modelCallsOnce("map_write",
                "{\"writes\":[{\"path\":\"/skills/1/checked\",\"value\":true}]}");

        service.chat(cvRequest(APP_ID));

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<CvMapService.Write>> writes = ArgumentCaptor.forClass(List.class);
        verify(cvMaps, timeout(2000)).write(eq(APP_ID), eq(7L), writes.capture());
        assertThat(writes.getValue()).hasSize(1);
        assertThat(writes.getValue().get(0).path()).isEqualTo("/skills/1/checked");
        assertThat(writes.getValue().get(0).value().asBoolean()).isTrue();
        assertThat(writes.getValue().get(0).replace()).isFalse();
    }

    // ── The server's own words ───────────────────────────────────────────────

    @Test
    @DisplayName("a new application opens with the server's own introduction — no model call")
    void theOpeningIsTheServersOwn() {
        app.setPhase(CvPhase.ANALYSIS);
        app.setAnnouncedStep(null);

        service.chat(controlRequest("open"));

        verify(cvApplications, timeout(2000)).markAnnounced(APP_ID, 0);
        verify(provider, after(300).never())
                .streamChat(anyList(), anyString(), anyList(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("the map step is announced before the model speaks — and the map is made sure of first")
    void theMapStepIsAnnounced() {
        app.setAnnouncedStep(1);

        promptFor(APP_ID);

        verify(cvMaps, timeout(2000)).ensure(APP_ID, 7L);
        verify(cvApplications, timeout(2000)).markAnnounced(APP_ID, 2);
    }

    @Test
    @DisplayName("the analysis runs when the work is in step 1, and the model is not asked to do it")
    void theServerRunsTheAnalysis() {
        app.setPhase(CvPhase.ANALYSIS);
        app.setAnalysisState("none");
        when(cvAnalysis.run(eq(APP_ID), eq(7L), any(), any(), any(), anyLong()))
                .thenReturn(CvAnalysisService.Outcome.DONE);

        service.chat(cvRequest(APP_ID));

        verify(cvAnalysis, timeout(2000).atLeastOnce()).run(eq(APP_ID), eq(7L), any(), any(), any(), anyLong());
    }

    @Test
    @DisplayName("a provider outage during the analysis ends the turn with the honest reason, and no model call")
    void aBusyProviderIsReported() {
        app.setPhase(CvPhase.ANALYSIS);
        app.setAnalysisState("none");
        when(cvAnalysis.run(eq(APP_ID), eq(7L), any(), any(), any(), anyLong()))
                .thenReturn(CvAnalysisService.Outcome.BUSY);

        service.chat(cvRequest(APP_ID));

        verify(cvAnalysis, timeout(2000).atLeastOnce()).run(eq(APP_ID), eq(7L), any(), any(), any(), anyLong());
        verify(provider, after(300).never())
                .streamChat(anyList(), anyString(), anyList(), any(), any(), any(), any());
    }

    // ── Degrading, and our own plumbing ─────────────────────────────────────

    @Test
    @DisplayName("no application id: the turn is told to ask for the advert, not to start inventing")
    void noApplicationIsStatedPlainly() {
        assertThat(promptFor(null))
                .contains("APPLICATION: none is open")
                .contains("do not begin an interview");
    }

    @Test
    @DisplayName("an application that cannot be loaded degrades rather than 500ing mid-stream")
    void anUnloadableApplicationDegrades() {
        when(cvApplications.pruned(APP_ID))
                .thenThrow(new org.springframework.web.server.ResponseStatusException(
                        org.springframework.http.HttpStatus.NOT_FOUND, "gone"));

        String prompt = promptFor(APP_ID);

        assertThat(prompt).contains(WRITER_MARKER).contains("could not be loaded");
    }

    @Test
    @DisplayName("the untrusted-content markers are stripped out of anything proposed for saving")
    void fenceMarkersNeverReachADocument() {
        String payload = "{\"kind\":\"note\",\"title\":\"CV\",\"value\":"
                + "\"<<UNTRUSTED_CONTENT>>\\n**Kompetenser**\\nJava\\n<<END_UNTRUSTED_CONTENT>>\"}";

        String cleaned = AiChatService.stripFenceMarkers(payload);

        assertThat(cleaned).doesNotContain("UNTRUSTED_CONTENT")
                .contains("**Kompetenser**").contains("Java")
                .startsWith("{\"kind\":\"note\"");
        assertThat(AiChatService.stripFenceMarkers(null)).isNull();
    }
}
