package com.spiramindscape.backend.ai.chat;

import com.spiramindscape.backend.ai.chat.dto.ChatRequest;
import com.spiramindscape.backend.ai.grow.GoalMemoryService;
import com.spiramindscape.backend.ai.key.AiKeyService;
import com.spiramindscape.backend.ai.prompt.PromptResources;
import com.spiramindscape.backend.ai.provider.LlmProvider;
import com.spiramindscape.backend.ai.provider.LlmProviderFactory;
import com.spiramindscape.backend.ai.provider.ProviderType;
import com.spiramindscape.backend.ai.provider.cohere.CohereVisionReader;
import com.spiramindscape.backend.ai.provider.mistral.MistralOcrService;
import com.spiramindscape.backend.ai.provider.ToolCall;
import com.spiramindscape.backend.ai.provider.ToolSpec;
import com.spiramindscape.backend.ai.proposal.AiProposalService;
import com.spiramindscape.backend.ai.safety.AbuseAuditLogger;
import com.spiramindscape.backend.goal.GoalService;
import com.spiramindscape.backend.ai.safety.SafetyService;
import com.spiramindscape.backend.ai.safety.SafetyVerdict;
import com.spiramindscape.backend.ai.search.TavilySearchService;
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
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

/**
 * The agentic loop must never leave the user with "no response". If the model
 * keeps calling a looping tool (e.g. web_search) until the iteration cap, the
 * loop gives one FINAL turn WITHOUT looping tools so it produces an answer
 * instead of ending right after an unanswered search (BUG-016 follow-up). When
 * the model finishes normally, no extra turn is added.
 */
@ExtendWith(MockitoExtension.class)
class AiChatServiceAgenticLoopTest {

    // Mirror of AiChatService.MAX_TOOL_ITERATIONS.
    private static final int MAX_ITERATIONS = 6;

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
    @Mock private com.spiramindscape.backend.ai.cv.CvApplicationService cvApplications;
    @Mock private LlmProvider provider;

    private AiChatService service;

    @BeforeEach
    void setUp() {
        service = new AiChatService(safety, abuseAuditLogger, keyService, providerFactory,
                goalContextBuilder, searchService, proposalService, resourceReadService,
                urlReadService, new PromptResources(), goalMemory, mistralOcr, cohereVision, goalService, cvApplications);
        lenient().when(safety.classify(anyString())).thenReturn(SafetyVerdict.ALLOWED);
        lenient().when(safety.referInstruction(any())).thenReturn("");
        // The chat resolves the request's goalId to an OWNED id before using it
        // (BUG-054); these fixtures speak for a goal the signed-in user owns.
        lenient().when(goalService.isOwnedByCurrentUser(any())).thenReturn(true);
        lenient().when(goalContextBuilder.build(any())).thenReturn("");
        lenient().when(keyService.getKey(ProviderType.ANTHROPIC))
                .thenReturn(Optional.of(new AiKeyService.StoredKey("chat-key", "claude")));
        // A Tavily key makes web_search a real looping tool.
        lenient().when(keyService.getKey(ProviderType.TAVILY))
                .thenReturn(Optional.of(new AiKeyService.StoredKey("tavily-key", null)));
        lenient().when(searchService.search(anyString(), anyString())).thenReturn("search results");
        lenient().when(providerFactory.create(eq(ProviderType.ANTHROPIC), anyString(), anyString()))
                .thenReturn(provider);
    }

    private static ChatRequest chatRequest() {
        return new ChatRequest(7L, "search the web for four products and list them",
                "ANTHROPIC", "chat", List.of(), null, null);
    }

    @SuppressWarnings("unchecked")
    private static Consumer<ToolCall> onToolCall(Object[] args) {
        return (Consumer<ToolCall>) args[4];
    }

    private static Runnable onComplete(Object[] args) {
        return (Runnable) args[5];
    }

    @SuppressWarnings("unchecked")
    private static Consumer<String> onToken(Object[] args) {
        return (Consumer<String>) args[3];
    }

    @Test
    @DisplayName("a model that searches every turn gets a forced final answer turn (no looping tools)")
    void forcesFinalTurnWhenCapReachedMidSearch() {
        // Every turn only calls web_search — the model never answers on its own.
        doAnswer(inv -> {
            Object[] a = inv.getArguments();
            onToolCall(a).accept(new ToolCall("c" + System.nanoTime(), "web_search", "{\"query\":\"q\"}"));
            onComplete(a).run();
            return null;
        }).when(provider).streamChat(anyList(), anyString(), anyList(), any(), any(), any(), any());

        service.chat(chatRequest());

        ArgumentCaptor<List<ToolSpec>> tools = ArgumentCaptor.forClass(List.class);
        // MAX_ITERATIONS loop turns + exactly one forced final turn.
        verify(provider, timeout(4000).times(MAX_ITERATIONS + 1)).streamChat(
                anyList(), anyString(), tools.capture(), any(), any(), any(), any());

        List<List<ToolSpec>> allTools = tools.getAllValues();
        // Loop turns offer web_search…
        assertThat(hasTool(allTools.get(0), "web_search")).isTrue();
        // …but the final turn does NOT (so it can't loop again — it must answer).
        assertThat(hasTool(allTools.get(MAX_ITERATIONS), "web_search")).isFalse();
        // The final turn still allows writing to the goal.
        assertThat(hasTool(allTools.get(MAX_ITERATIONS), "propose_goal_change")).isTrue();
    }

    @Test
    @DisplayName("a model that answers after one search adds no extra turn")
    void noExtraTurnWhenModelFinishes() {
        AtomicInteger turn = new AtomicInteger();
        doAnswer(inv -> {
            Object[] a = inv.getArguments();
            if (turn.getAndIncrement() == 0) {
                onToolCall(a).accept(new ToolCall("c1", "web_search", "{\"query\":\"q\"}"));
            } else {
                onToken(a).accept("Here are the results.");
            }
            onComplete(a).run();
            return null;
        }).when(provider).streamChat(anyList(), anyString(), anyList(), any(), any(), any(), any());

        service.chat(chatRequest());

        // Turn 0 searches, turn 1 answers → loop ends; no forced final turn.
        verify(provider, timeout(4000).times(2)).streamChat(
                anyList(), anyString(), anyList(), any(), any(), any(), any());
    }

    /**
     * BUG-068: Mistral's own account Limits page showed some of its chat models capped at
     * 1.00 request/second — a ceiling two agentic-loop iterations trip on their own, since a
     * tool-only turn can return in well under a second. The loop must space its OWN calls to
     * Mistral out by at least that long.
     */
    @Test
    @DisplayName("two loop iterations against Mistral are at least ~1.1s apart")
    void pacesConsecutiveMistralCalls() {
        lenient().when(provider.providerType()).thenReturn(ProviderType.MISTRAL);
        lenient().when(providerFactory.create(eq(ProviderType.MISTRAL), anyString(), anyString()))
                .thenReturn(provider);
        lenient().when(keyService.getKey(ProviderType.MISTRAL))
                .thenReturn(Optional.of(new AiKeyService.StoredKey("mistral-key", "mistral-large-latest")));

        List<Long> callStartNanos = new java.util.concurrent.CopyOnWriteArrayList<>();
        AtomicInteger turn = new AtomicInteger();
        doAnswer(inv -> {
            callStartNanos.add(System.nanoTime());
            Object[] a = inv.getArguments();
            if (turn.getAndIncrement() == 0) {
                onToolCall(a).accept(new ToolCall("c1", "web_search", "{\"query\":\"q\"}"));
            } else {
                onToken(a).accept("Done.");
            }
            onComplete(a).run();
            return null;
        }).when(provider).streamChat(anyList(), anyString(), anyList(), any(), any(), any(), any());

        ChatRequest request = new ChatRequest(7L, "what's in my goal?", "MISTRAL", "chat",
                List.of(), null, null);
        service.chat(request);

        verify(provider, timeout(4000).times(2)).streamChat(
                anyList(), anyString(), anyList(), any(), any(), any(), any());
        assertThat(callStartNanos).hasSize(2);
        long gapMillis = (callStartNanos.get(1) - callStartNanos.get(0)) / 1_000_000;
        assertThat(gapMillis).isGreaterThanOrEqualTo(1000);
    }

    private static boolean hasTool(List<ToolSpec> tools, String name) {
        return tools != null && tools.stream().anyMatch(t -> name.equals(t.name()));
    }
}
