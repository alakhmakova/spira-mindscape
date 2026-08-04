package com.spiramindscape.backend.ai.chat;

import com.spiramindscape.backend.ai.chat.dto.ChatRequest;
import com.spiramindscape.backend.ai.grow.GoalMemoryService;
import com.spiramindscape.backend.ai.grow.GrowLibraryService;
import com.spiramindscape.backend.ai.key.AiKeyService;
import com.spiramindscape.backend.ai.provider.LlmMessage;
import com.spiramindscape.backend.ai.provider.LlmProvider;
import com.spiramindscape.backend.ai.provider.LlmProviderFactory;
import com.spiramindscape.backend.ai.provider.ProviderType;
import com.spiramindscape.backend.ai.provider.mistral.MistralOcrService;
import com.spiramindscape.backend.ai.proposal.AiProposalService;
import com.spiramindscape.backend.ai.safety.AbuseAuditLogger;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * An attached image must never reach the model as a silent gap (BUG-027).
 *
 * <p>Most Mistral chat models — including the default {@code mistral-large-latest} — are
 * text-only. The provider drops the picture, the turn still says one was attached, and the
 * model answers as if it had looked at it. So: a blind model gets the OCR text, or an explicit
 * note that it was shown nothing; it never gets the image.
 */
@ExtendWith(MockitoExtension.class)
class AiChatServiceImageAttachmentTest {

    private static final String PHOTO = "data:image/jpeg;base64,QUJD";

    @Mock private SafetyService safety;
    @Mock private AbuseAuditLogger abuseAuditLogger;
    @Mock private AiKeyService keyService;
    @Mock private LlmProviderFactory providerFactory;
    @Mock private GoalContextBuilder goalContextBuilder;
    @Mock private TavilySearchService searchService;
    @Mock private AiProposalService proposalService;
    @Mock private ResourceReadService resourceReadService;
    @Mock private UrlReadService urlReadService;
    @Mock private GrowLibraryService growLibrary;
    @Mock private GoalMemoryService goalMemory;
    @Mock private MistralOcrService mistralOcr;
    @Mock private LlmProvider provider;

    private AiChatService service;

    @BeforeEach
    void setUp() {
        service = new AiChatService(safety, abuseAuditLogger, keyService, providerFactory,
                goalContextBuilder, searchService, proposalService, resourceReadService,
                urlReadService, growLibrary, goalMemory, mistralOcr);
        lenient().when(safety.classify(anyString())).thenReturn(SafetyVerdict.ALLOWED);
        lenient().when(safety.referInstruction(any())).thenReturn("");
        lenient().when(goalContextBuilder.build(any())).thenReturn("");
        lenient().when(providerFactory.create(any(), anyString(), any())).thenReturn(provider);
        // The model answers straight away — no tool loop in these tests.
        lenient().doAnswer(inv -> {
            ((Runnable) inv.getArguments()[5]).run();
            return null;
        }).when(provider).streamChat(anyList(), anyString(), anyList(), any(), any(), any(), any());
    }

    private void useMistral(String model) {
        lenient().when(keyService.getKey(ProviderType.MISTRAL))
                .thenReturn(Optional.of(new AiKeyService.StoredKey("mistral-key", model)));
    }

    private static ChatRequest withPhoto(String provider) {
        return new ChatRequest(7L, "what is written here?", provider, "chat", List.of(),
                null, null, List.of(new ChatRequest.Attachment("note.jpg", "image/jpeg", PHOTO)));
    }

    @SuppressWarnings("unchecked")
    private LlmMessage lastUserMessage() {
        ArgumentCaptor<List<LlmMessage>> msgs = ArgumentCaptor.forClass(List.class);
        verify(provider, timeout(4000)).streamChat(
                msgs.capture(), anyString(), anyList(), any(), any(), any(), any());
        List<LlmMessage> sent = msgs.getValue();
        return sent.get(sent.size() - 1);
    }

    @Test
    @DisplayName("a text-only Mistral model gets the OCR text and never the picture")
    void blindModelGetsOcrText() {
        useMistral("mistral-large-latest");
        when(mistralOcr.extractText(eq("mistral-key"), eq(PHOTO), anyInt()))
                .thenReturn(Optional.of("Rågen Roast'n toast 263 kkal/100"));

        service.chat(withPhoto("MISTRAL"));

        LlmMessage sent = lastUserMessage();
        assertThat(sent.hasImages()).isFalse();
        assertThat(sent.content()).contains("Rågen Roast'n toast 263 kkal/100");
        // Flagged as a machine reading, so the model reports uncertainty instead of "I see…".
        assertThat(sent.content()).contains("OCR");
    }

    @Test
    @DisplayName("no OCR available → the model is told it was shown nothing, so it can't invent")
    void blindModelWithoutOcrIsToldTheTruth() {
        useMistral("mistral-large-latest");
        when(mistralOcr.extractText(anyString(), anyString(), anyInt())).thenReturn(Optional.empty());

        service.chat(withPhoto("MISTRAL"));

        LlmMessage sent = lastUserMessage();
        assertThat(sent.hasImages()).isFalse();
        assertThat(sent.content())
                .contains("NOT shown to you")
                .contains("mistral-large-latest")
                .contains("NEVER guess");
    }

    @Test
    @DisplayName("a vision model still gets the picture — and the OCR text alongside it")
    void visionModelKeepsTheImage() {
        useMistral("pixtral-large-latest");
        when(mistralOcr.extractText(anyString(), eq(PHOTO), anyInt()))
                .thenReturn(Optional.of("Melon 60 грамм"));

        service.chat(withPhoto("MISTRAL"));

        LlmMessage sent = lastUserMessage();
        assertThat(sent.hasImages()).isTrue();
        assertThat(sent.content()).contains("[Attached image: note.jpg]").contains("Melon 60 грамм");
    }

    @Test
    @DisplayName("Anthropic is untouched: the picture goes as-is, with no OCR call")
    void anthropicPathIsUnchanged() {
        lenient().when(keyService.getKey(ProviderType.ANTHROPIC))
                .thenReturn(Optional.of(new AiKeyService.StoredKey("claude-key", "claude-sonnet-4-6")));

        service.chat(withPhoto("ANTHROPIC"));

        LlmMessage sent = lastUserMessage();
        assertThat(sent.hasImages()).isTrue();
        assertThat(sent.content()).contains("[Attached image: note.jpg]");
        verify(mistralOcr, never()).extractText(anyString(), anyString(), anyInt());
    }
}
