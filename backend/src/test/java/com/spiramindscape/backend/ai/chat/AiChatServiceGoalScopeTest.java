package com.spiramindscape.backend.ai.chat;

import com.spiramindscape.backend.ai.chat.dto.ChatRequest;
import com.spiramindscape.backend.ai.grow.GoalMemoryService;
import com.spiramindscape.backend.ai.key.AiKeyService;
import com.spiramindscape.backend.ai.prompt.PromptResources;
import com.spiramindscape.backend.ai.provider.LlmProvider;
import com.spiramindscape.backend.ai.provider.LlmProviderFactory;
import com.spiramindscape.backend.ai.provider.ProviderType;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * **The goal id in a chat request is untrusted input** (BUG-054).
 *
 * <p>It arrives in the POST body and everything the turn does is keyed off it: which goal's
 * text goes into the system prompt, whether the {@code read_resource} tool is offered and
 * what it may read, which goal a proposal is written against, and which goal's session
 * memory is loaded. Until this class existed, none of that was checked — a signed-in user
 * could put someone else's goal id in the body and have the model read that goal back to
 * them.
 *
 * <p>The chat resolves the id to an <b>owned</b> id once, at the top of {@code chat()}, and
 * a foreign or missing id becomes {@code null} — the same "no goal open" state the
 * All-Goals chat runs in. These tests pin that down at the seam where it matters: what the
 * provider is actually handed.
 *
 * <p>Companion coverage: {@code GoalContextBuilderTest} (the prompt block),
 * {@code ResourceReadServiceTest} (the tool's own boundary),
 * {@code AiProposalServiceTest} (the proposal listing) and
 * {@code AiCrossUserIsolationIntegrationTest} (the HTTP endpoints, against a real database).
 */
@ExtendWith(MockitoExtension.class)
class AiChatServiceGoalScopeTest {

    /** A goal id the signed-in user does not own — the whole point of the class. */
    private static final long FOREIGN_GOAL = 4242L;
    private static final long OWN_GOAL = 7L;

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
    @Mock private LlmProvider provider;

    private AiChatService service;

    @BeforeEach
    void setUp() {
        service = new AiChatService(safety, abuseAuditLogger, keyService, providerFactory,
                goalContextBuilder, searchService, proposalService, resourceReadService,
                urlReadService, new PromptResources(), goalMemory, mistralOcr, cohereVision, goalService);
        lenient().when(safety.classify(anyString())).thenReturn(SafetyVerdict.ALLOWED);
        lenient().when(safety.referInstruction(any())).thenReturn("");
        lenient().when(goalContextBuilder.build(any())).thenReturn("");
        lenient().when(goalMemory.memoryBlock(any())).thenReturn("");
        lenient().when(keyService.getKey(ProviderType.ANTHROPIC))
                .thenReturn(Optional.of(new AiKeyService.StoredKey("chat-key", "claude")));
        lenient().when(providerFactory.create(any(), anyString(), any())).thenReturn(provider);
        // Complete the stream immediately; these tests only care what went IN.
        lenient().doAnswer(inv -> {
            ((Runnable) inv.getArguments()[5]).run();
            return null;
        }).when(provider).streamChat(anyList(), anyString(), anyList(), any(), any(), any(), any());
    }

    private static ChatRequest chatOn(Long goalId) {
        return new ChatRequest(goalId, "what is on this goal?", "ANTHROPIC", "chat",
                List.of(), null, null);
    }

    private static ChatRequest growOn(Long goalId) {
        return new ChatRequest(goalId, "let's start", "ANTHROPIC", "grow",
                List.of(), 30, 1800);
    }

    @SuppressWarnings("unchecked")
    private List<ToolSpec> toolsSentToProvider() {
        ArgumentCaptor<List<ToolSpec>> tools = ArgumentCaptor.forClass(List.class);
        verify(provider, timeout(4000)).streamChat(
                anyList(), anyString(), tools.capture(), any(), any(), any(), any());
        return tools.getValue();
    }

    // ─── A goal the user does not own ─────────────────────────────────────────

    @Test
    @DisplayName("A foreign goal id never reaches the prompt builder")
    void foreignGoalIsNotBuiltIntoThePrompt() {
        when(goalService.isOwnedByCurrentUser(FOREIGN_GOAL)).thenReturn(false);

        service.chat(chatOn(FOREIGN_GOAL));

        // null == the All-Goals overview, built from the CALLER's own goals.
        verify(goalContextBuilder, timeout(4000)).build(null);
        verify(goalContextBuilder, never()).build(FOREIGN_GOAL);
    }

    @Test
    @DisplayName("A foreign goal id does not get the read_resource tool offered")
    void foreignGoalGetsNoResourceTool() {
        when(goalService.isOwnedByCurrentUser(FOREIGN_GOAL)).thenReturn(false);

        service.chat(chatOn(FOREIGN_GOAL));

        // Without a goal, the model has no id to pass and the tool has nothing to scope to.
        // Leaving it offered would hand the model a lever aimed at someone else's data.
        assertThat(toolsSentToProvider())
                .extracting(ToolSpec::name)
                .doesNotContain("read_resource");
    }

    @Test
    @DisplayName("A GROW session on a foreign goal loads no session memory from it")
    void foreignGoalLoadsNoMemory() {
        when(goalService.isOwnedByCurrentUser(FOREIGN_GOAL)).thenReturn(false);

        service.chat(growOn(FOREIGN_GOAL));

        verify(goalMemory, timeout(4000)).memoryBlock(null);
        verify(goalMemory, never()).memoryBlock(FOREIGN_GOAL);
    }

    @Test
    @DisplayName("A goal that does not exist is treated exactly like a foreign one")
    void unknownGoalBehavesLikeAForeignGoal() {
        // isOwnedByCurrentUser answers false for both, on purpose: the chat must not be able
        // to tell "no such goal" from "someone else's goal", or the endpoint becomes a probe
        // for which goal ids exist.
        when(goalService.isOwnedByCurrentUser(999_999L)).thenReturn(false);

        service.chat(chatOn(999_999L));

        verify(goalContextBuilder, timeout(4000)).build(null);
    }

    // ─── The user's own goal still works ──────────────────────────────────────

    @Test
    @DisplayName("An owned goal is passed through untouched")
    void ownedGoalIsUsed() {
        when(goalService.isOwnedByCurrentUser(OWN_GOAL)).thenReturn(true);

        service.chat(chatOn(OWN_GOAL));

        verify(goalContextBuilder, timeout(4000)).build(OWN_GOAL);
        assertThat(toolsSentToProvider())
                .extracting(ToolSpec::name)
                .contains("read_resource");
    }

    @Test
    @DisplayName("A chat with no goal at all never asks about ownership")
    void globalChatSkipsTheCheck() {
        service.chat(chatOn(null));

        verify(goalContextBuilder, timeout(4000)).build(null);
        // A null id is the All-Goals chat, not an attempt to reach a goal — no query for it.
        verify(goalService, never()).isOwnedByCurrentUser(any());
    }

    @Test
    @DisplayName("Ownership is resolved once per request, not per use of the id")
    void ownershipIsCheckedOnce() {
        when(goalService.isOwnedByCurrentUser(OWN_GOAL)).thenReturn(true);

        service.chat(growOn(OWN_GOAL));

        // The prompt, the memory block, the tool list and the agentic loop all take the id.
        // Re-querying for each would put four round trips on every turn of every chat.
        verify(goalService, timeout(4000)).isOwnedByCurrentUser(eq(OWN_GOAL));
    }
}
