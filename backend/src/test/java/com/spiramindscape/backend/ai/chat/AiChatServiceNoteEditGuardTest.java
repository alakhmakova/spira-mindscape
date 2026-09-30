package com.spiramindscape.backend.ai.chat;

import com.spiramindscape.backend.ai.chat.dto.ChatRequest;
import com.spiramindscape.backend.ai.grow.GoalMemoryService;
import com.spiramindscape.backend.ai.key.AiKeyService;
import com.spiramindscape.backend.ai.prompt.PromptResources;
import com.spiramindscape.backend.ai.proposal.AiProposalService;
import com.spiramindscape.backend.ai.proposal.dto.ProposalDto;
import com.spiramindscape.backend.ai.provider.LlmMessage;
import com.spiramindscape.backend.ai.provider.LlmProvider;
import com.spiramindscape.backend.ai.provider.LlmProviderFactory;
import com.spiramindscape.backend.ai.provider.ProviderType;
import com.spiramindscape.backend.ai.provider.ToolCall;
import com.spiramindscape.backend.ai.provider.cohere.CohereVisionReader;
import com.spiramindscape.backend.ai.provider.mistral.MistralOcrService;
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

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.after;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * An AI note edit must never replace what the user wrote from the model's memory of it
 * (found live on 2026-09-15: five CV-profile rewrites in one session, each dropping the
 * owner's own corrections). The loop refuses such a proposal before any card is shown and
 * tells the model why, so it can read the note and try again inside the same request.
 */
@ExtendWith(MockitoExtension.class)
class AiChatServiceNoteEditGuardTest {

    private static final Instant VERSION = Instant.parse("2026-09-15T10:02:11.123456Z");
    private static final String BODY = "<h2>Contact</h2><p>Old contact</p><h2>Experience</h2><p>Java at Spira</p>";

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
        lenient().when(goalService.isOwnedByCurrentUser(any())).thenReturn(true);
        lenient().when(goalContextBuilder.build(any())).thenReturn("");
        lenient().when(keyService.getKey(ProviderType.ANTHROPIC))
                .thenReturn(Optional.of(new AiKeyService.StoredKey("chat-key", "claude")));
        lenient().when(providerFactory.create(eq(ProviderType.ANTHROPIC), anyString(), anyString()))
                .thenReturn(provider);
        lenient().when(resourceReadService.ownedNote(7L, 12L))
                .thenReturn(Optional.of(new ResourceReadService.OwnedNote(12L, "Profile", BODY, VERSION)));
        lenient().when(resourceReadService.read(7L, 12L)).thenReturn("[note id=12]" + BODY);
        lenient().when(proposalService.create(any(), anyString(), anyString()))
                .thenAnswer(i -> new ProposalDto(99L, 7L, "edit_note", i.getArgument(2), null, Instant.now()));
    }

    private static ChatRequest request() {
        return new ChatRequest(7L, "update my profile", "ANTHROPIC", "chat", List.of(), null, null);
    }

    private static ToolCall propose(String json) {
        return new ToolCall("p" + System.nanoTime(), "propose_goal_change", json);
    }

    /**
     * Scripts the model: turn {@code i} emits {@code turns.get(i)} (a tool call, or text when null)
     * and records what the model was shown on every turn.
     */
    @SuppressWarnings("unchecked")
    private List<List<LlmMessage>> script(List<ToolCall> turns) {
        List<List<LlmMessage>> seen = new CopyOnWriteArrayList<>();
        AtomicInteger turn = new AtomicInteger();
        doAnswer(inv -> {
            Object[] a = inv.getArguments();
            seen.add(new ArrayList<>((List<LlmMessage>) a[0]));
            int i = turn.getAndIncrement();
            ToolCall call = i < turns.size() ? turns.get(i) : null;
            if (call != null) ((Consumer<ToolCall>) a[4]).accept(call);
            else ((Consumer<String>) a[3]).accept("Done.");
            ((Runnable) a[5]).run();
            return null;
        }).when(provider).streamChat(anyList(), anyString(), anyList(), any(), any(), any(), any());
        return seen;
    }

    private static String lastToolResult(List<LlmMessage> messages) {
        for (int i = messages.size() - 1; i >= 0; i--) {
            if ("tool".equals(messages.get(i).role())) return messages.get(i).content();
        }
        return "";
    }

    @Test
    @DisplayName("a rewrite without a read in this reply is refused, and the model is told to read the note")
    void rewriteWithoutReadIsRefused() {
        var seen = script(java.util.Arrays.asList(
                propose("{\"kind\":\"edit_note\",\"id\":\"12\",\"mode\":\"replace_all\",\"title\":\"Profile\","
                        + "\"value\":\"<p>From memory</p>\"}"),
                null));

        service.chat(request());

        verify(provider, timeout(4000).times(2)).streamChat(anyList(), anyString(), anyList(), any(), any(), any(), any());
        verify(proposalService, after(300).never()).create(any(), anyString(), anyString());
        assertThat(lastToolResult(seen.get(1)))
                .startsWith("NOT SHOWN to the user")
                .contains("read_resource with id=12");
    }

    @Test
    @DisplayName("read first, then rewrite a section: the card carries the server's version and diff, not the model's")
    void readThenRewriteIsShownWithServerStamps() {
        script(java.util.Arrays.asList(
                new ToolCall("r1", "read_resource", "{\"id\":\"12\"}"),
                propose("{\"kind\":\"edit_note\",\"id\":\"12\",\"mode\":\"replace_section\",\"section\":\"Contact\","
                        + "\"value\":\"<p>New contact</p>\",\"baseUpdatedAt\":\"1999-01-01T00:00:00Z\","
                        + "\"diff\":{\"added\":[\"fake\"],\"removed\":[]}}")));

        service.chat(request());

        ArgumentCaptor<String> payload = ArgumentCaptor.forClass(String.class);
        verify(proposalService, timeout(4000)).create(eq(7L), eq("edit_note"), payload.capture());
        assertThat(payload.getValue())
                .contains("\"mode\":\"replace_section\"")
                .contains("\"baseUpdatedAt\":\"" + VERSION + "\"")
                .contains("\"added\":[\"new contact\"]")
                .contains("\"removed\":[\"old contact\"]")
                .doesNotContain("1999")
                .doesNotContain("fake")
                .contains("\"title\":\"Profile\"");
    }

    @Test
    @DisplayName("an append with nothing new is not shown")
    void appendOfKnownContentIsRefused() {
        var seen = script(java.util.Arrays.asList(
                propose("{\"kind\":\"edit_note\",\"id\":\"12\",\"value\":\"<p>Java at   Spira</p>\"}"),
                null));

        service.chat(request());

        verify(provider, timeout(4000).times(2)).streamChat(anyList(), anyString(), anyList(), any(), any(), any(), any());
        verify(proposalService, never()).create(any(), anyString(), anyString());
        assertThat(lastToolResult(seen.get(1))).contains("already in note 12");
    }

    @Test
    @DisplayName("an append needs no read, and a note that is not on this goal is refused")
    void appendNeedsNoReadButTheNoteMustExist() {
        var seen = script(java.util.Arrays.asList(
                propose("{\"kind\":\"edit_note\",\"id\":\"335\",\"value\":\"<p>Kotlin</p>\"}"),
                propose("{\"kind\":\"edit_note\",\"id\":\"12\",\"mode\":\"append_to_section\",\"section\":\"Experience\","
                        + "\"value\":\"<p>Kotlin</p>\"}")));

        service.chat(request());

        ArgumentCaptor<String> payload = ArgumentCaptor.forClass(String.class);
        verify(proposalService, timeout(4000).times(1)).create(eq(7L), eq("edit_note"), payload.capture());
        assertThat(lastToolResult(seen.get(1))).contains("no note with id=335");
        assertThat(payload.getValue()).contains("\"mode\":\"append_to_section\"").contains("\"removed\":[]");
    }
}
