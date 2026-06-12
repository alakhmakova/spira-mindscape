package com.spiramindscape.backend.ai.chat;

import com.spiramindscape.backend.ai.chat.dto.ChatRequest;
import com.spiramindscape.backend.ai.grow.GoalMemoryService;
import com.spiramindscape.backend.ai.grow.GrowLibraryService;
import com.spiramindscape.backend.ai.key.AiKeyService;
import com.spiramindscape.backend.ai.provider.LlmProvider;
import com.spiramindscape.backend.ai.provider.LlmProviderFactory;
import com.spiramindscape.backend.ai.provider.ProviderType;
import com.spiramindscape.backend.ai.proposal.AiProposalService;
import com.spiramindscape.backend.ai.safety.SafetyService;
import com.spiramindscape.backend.ai.search.TavilySearchService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.after;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * GROW-path guards in {@link AiChatService}: a session must be grounded in the
 * coaching library or refuse — there is no code path that reaches the LLM with
 * the bare prompt. Regular chat must stay untouched by the library.
 */
@ExtendWith(MockitoExtension.class)
class AiChatServiceGrowTest {

    private static final String EXCERPTS_MARKER = "Ask powerful open questions.";
    private static final String EXCERPTS_BLOCK =
            "COACHING LIBRARY — excerpts:\n[Excerpt 1 — \"Test Book\"]\n" + EXCERPTS_MARKER;

    @Mock private SafetyService safety;
    @Mock private AiKeyService keyService;
    @Mock private LlmProviderFactory providerFactory;
    @Mock private GoalContextBuilder goalContextBuilder;
    @Mock private TavilySearchService searchService;
    @Mock private AiProposalService proposalService;
    @Mock private ResourceReadService resourceReadService;
    @Mock private UrlReadService urlReadService;
    @Mock private GrowLibraryService growLibrary;
    @Mock private GoalMemoryService goalMemory;
    @Mock private LlmProvider provider;

    private AiChatService service;

    @BeforeEach
    void setUp() {
        service = new AiChatService(safety, keyService, providerFactory, goalContextBuilder,
                searchService, proposalService, resourceReadService, urlReadService, growLibrary,
                goalMemory);
        lenient().when(safety.isSafe(anyString())).thenReturn(true);
        lenient().when(goalMemory.memoryBlock(any())).thenReturn("");
        lenient().when(goalContextBuilder.build(any())).thenReturn("");
        lenient().when(keyService.getKey(ProviderType.ANTHROPIC))
                .thenReturn(Optional.of(new AiKeyService.StoredKey("chat-key", "claude")));
        lenient().when(providerFactory.create(eq(ProviderType.ANTHROPIC), anyString(), anyString()))
                .thenReturn(provider);
    }

    private static ChatRequest request(String sessionType) {
        return new ChatRequest(7L, "I want to talk about my goal", "ANTHROPIC", sessionType,
                List.of(), null, null);
    }

    private static ChatRequest growRequest(Integer totalMinutes, Integer remainingSeconds) {
        return new ChatRequest(7L, "I want to talk about my goal", "ANTHROPIC", "grow",
                List.of(), totalMinutes, remainingSeconds);
    }

    @Test
    @DisplayName("GROW without a Mistral key refuses before any LLM or library work")
    void growWithoutMistralKeyRefuses() {
        when(keyService.getKey(ProviderType.MISTRAL)).thenReturn(Optional.empty());

        SseEmitter emitter = service.chat(request("grow"));

        assertThat(emitter).isNotNull();
        verifyNoInteractions(providerFactory, growLibrary);
    }

    @Test
    @DisplayName("a retrieval failure refuses the session — the LLM is never called")
    void growRetrievalFailureNeverCallsLlm() {
        when(keyService.getKey(ProviderType.MISTRAL))
                .thenReturn(Optional.of(new AiKeyService.StoredKey("mistral-key", null)));
        when(growLibrary.buildQuery(any())).thenReturn("query");
        doThrow(new IllegalStateException("The coaching library is empty"))
                .when(growLibrary).retrieveExcerpts(anyString(), anyString());

        service.chat(request("grow"));

        verify(growLibrary, timeout(2000)).retrieveExcerpts(anyString(), eq("mistral-key"));
        verify(provider, after(300).never())
                .streamChat(anyList(), anyString(), anyList(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("a GROW turn reaches the LLM with the retrieved excerpts in the system prompt")
    void growInjectsExcerptsIntoSystemPrompt() {
        when(keyService.getKey(ProviderType.MISTRAL))
                .thenReturn(Optional.of(new AiKeyService.StoredKey("mistral-key", null)));
        when(growLibrary.buildQuery(any())).thenReturn("query");
        when(growLibrary.retrieveExcerpts(anyString(), anyString())).thenReturn(EXCERPTS_BLOCK);

        SseEmitter emitter = service.chat(request("grow"));

        ArgumentCaptor<String> systemPrompt = ArgumentCaptor.forClass(String.class);
        verify(provider, timeout(2000)).streamChat(
                anyList(), systemPrompt.capture(), anyList(), any(), any(), any(), any());
        assertThat(systemPrompt.getValue()).contains(EXCERPTS_MARKER);
        verify(growLibrary, timeout(2000)).ensureEmbedded(eq("mistral-key"), any());
        // First-ever session may need to embed ~1k chunks before the LLM starts.
        assertThat(emitter.getTimeout()).isEqualTo(10 * 60 * 1000L);
    }

    @Test
    @DisplayName("session timing reaches the system prompt; expired time demands a closing reply")
    void growTimingShapesPrompt() {
        when(keyService.getKey(ProviderType.MISTRAL))
                .thenReturn(Optional.of(new AiKeyService.StoredKey("mistral-key", null)));
        when(growLibrary.buildQuery(any())).thenReturn("query");
        when(growLibrary.retrieveExcerpts(anyString(), anyString())).thenReturn(EXCERPTS_BLOCK);

        service.chat(growRequest(30, 0));

        ArgumentCaptor<String> systemPrompt = ArgumentCaptor.forClass(String.class);
        verify(provider, timeout(2000)).streamChat(
                anyList(), systemPrompt.capture(), anyList(), any(), any(), any(), any());
        assertThat(systemPrompt.getValue())
                .contains("SESSION TIMING")
                .contains("30-minute")
                .contains("time is now UP");
    }

    @Test
    @DisplayName("saved session memory reaches the GROW system prompt")
    void growIncludesSessionMemory() {
        when(keyService.getKey(ProviderType.MISTRAL))
                .thenReturn(Optional.of(new AiKeyService.StoredKey("mistral-key", null)));
        when(growLibrary.buildQuery(any())).thenReturn("query");
        when(growLibrary.retrieveExcerpts(anyString(), anyString())).thenReturn(EXCERPTS_BLOCK);
        when(goalMemory.memoryBlock(7L))
                .thenReturn("PREVIOUS GROW SESSIONS\nClarified: senior QA role.");

        service.chat(request("grow"));

        ArgumentCaptor<String> systemPrompt = ArgumentCaptor.forClass(String.class);
        verify(provider, timeout(2000)).streamChat(
                anyList(), systemPrompt.capture(), anyList(), any(), any(), any(), any());
        assertThat(systemPrompt.getValue())
                .contains("PREVIOUS GROW SESSIONS")
                .contains("Clarified: senior QA role.");
    }

    @Test
    @DisplayName("regular chat never touches the coaching library or the Mistral key")
    void regularChatUnaffected() {
        when(keyService.getKey(ProviderType.TAVILY)).thenReturn(Optional.empty());

        SseEmitter emitter = service.chat(request("chat"));

        ArgumentCaptor<String> systemPrompt = ArgumentCaptor.forClass(String.class);
        verify(provider, timeout(2000)).streamChat(
                anyList(), systemPrompt.capture(), anyList(), any(), any(), any(), any());
        assertThat(systemPrompt.getValue()).doesNotContain(EXCERPTS_MARKER);
        verify(keyService, never()).getKey(ProviderType.MISTRAL);
        verifyNoInteractions(growLibrary);
        assertThat(emitter.getTimeout()).isEqualTo(3 * 60 * 1000L);
    }
}
