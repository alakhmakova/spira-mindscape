package com.spiramindscape.backend.ai.chat;

import com.spiramindscape.backend.ai.chat.dto.ChatRequest;
import com.spiramindscape.backend.ai.grow.GoalMemoryService;
import com.spiramindscape.backend.ai.key.AiKeyService;
import com.spiramindscape.backend.ai.provider.LlmMessage;
import com.spiramindscape.backend.ai.prompt.PromptResources;
import com.spiramindscape.backend.ai.provider.LlmProvider;
import com.spiramindscape.backend.ai.provider.LlmProviderFactory;
import com.spiramindscape.backend.ai.provider.ProviderType;
import com.spiramindscape.backend.ai.provider.cohere.CohereVisionReader;
import com.spiramindscape.backend.ai.provider.mistral.MistralOcrService;
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
        // The chat resolves the request's goalId to an OWNED id before using it
        // (BUG-054); these fixtures speak for a goal the signed-in user owns.
        lenient().when(goalService.isOwnedByCurrentUser(any())).thenReturn(true);
        lenient().when(goalContextBuilder.build(any())).thenReturn("");
        lenient().when(providerFactory.create(any(), anyString(), any())).thenReturn(provider);
        // The model answers straight away — no tool loop in these tests.
        lenient().doAnswer(inv -> {
            ((Runnable) inv.getArguments()[5]).run();
            return null;
        }).when(provider).streamChat(anyList(), anyString(), anyList(), any(), any(), any(), any());
    }

    /**
     * The wording that tells the model what kind of reading it is holding now belongs to the
     * reader, so a mocked reader has to supply it — see {@code ImageTextReader.describeReading}.
     */
    private void readersDescribeThemselves() {
        lenient().when(mistralOcr.describeReading()).thenReturn(
                "text read out of the image by OCR — it may contain mistakes");
        lenient().when(cohereVision.describeReading()).thenReturn(
                "text read out of the image by a vision model — it may contain mistakes");
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
        readersDescribeThemselves();
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

    // ── A blind model on a provider that is NOT Mistral ──────────────────────
    //
    // The owner's question, in code (2026-08-28): "on Mistral I also use a blind model — how did
    // you solve that? do the same for Cohere." The answer is that nothing is per-provider —
    // `visionContextFor` keys off `modelCanSeeImages(provider, model)`, so Cohere on a text-only
    // Command model already takes the same road Mistral Large does. Nothing asserted it for a
    // provider other than Mistral, though, which is the gap these two close.

    private void useCohere(String model) {
        lenient().when(keyService.getKey(ProviderType.COHERE))
                .thenReturn(Optional.of(new AiKeyService.StoredKey("cohere-key", model)));
    }

    @Test
    @DisplayName("a blind Cohere model reads the photo on its OWN key — no second key needed")
    void blindCohereModelReadsWithItsOwnKey() {
        // The owner's ask (2026-08-28): choosing Cohere must not oblige anyone to fetch a second
        // API key from a second company just to attach a photo. Cohere's vision model does the
        // reading, on the Cohere key — the same shape as a Mistral user, whose OCR has always
        // run on their own key.
        readersDescribeThemselves();
        useCohere("command-a-03-2025");
        lenient().when(keyService.getKey(ProviderType.MISTRAL)).thenReturn(Optional.empty());
        when(cohereVision.extractText(eq("cohere-key"), eq(PHOTO), anyInt()))
                .thenReturn(Optional.of("Rågen Roast'n toast 263 kkal/100"));

        service.chat(withPhoto("COHERE"));

        LlmMessage sent = lastUserMessage();
        assertThat(sent.hasImages()).isFalse();
        assertThat(sent.content()).contains("Rågen Roast'n toast 263 kkal/100");
        // Reported as a transcription, never as sight.
        assertThat(sent.content()).contains("vision model");
        // And no Mistral key was wanted at any point.
        verify(mistralOcr, never()).extractText(anyString(), anyString(), anyInt());
    }

    @Test
    @DisplayName("a Cohere reading that finds nothing still leaves the model unable to invent")
    void blindCohereModelWithNothingReadableIsToldTheTruth() {
        readersDescribeThemselves();
        useCohere("command-a-03-2025");
        when(cohereVision.extractText(anyString(), anyString(), anyInt()))
                .thenReturn(Optional.empty());

        service.chat(withPhoto("COHERE"));

        LlmMessage sent = lastUserMessage();
        assertThat(sent.hasImages()).isFalse();
        assertThat(sent.content())
                .contains("NOT shown to you")
                .contains("command-a-03-2025")
                .contains("NEVER guess");
    }

    @Test
    @DisplayName("a provider with no reader of its own still borrows a saved Mistral key")
    void anthropicBorrowsTheMistralKey() {
        // Anthropic, OpenAI and Gemini have no OCR of their own, so the fallback still applies —
        // which is why the rule is "own provider first", not "own provider only".
        readersDescribeThemselves();
        lenient().when(keyService.getKey(ProviderType.ANTHROPIC))
                .thenReturn(Optional.of(new AiKeyService.StoredKey("anthropic-key", "gpt-3.5")));
        useMistral(null);
        when(mistralOcr.extractText(eq("mistral-key"), eq(PHOTO), anyInt()))
                .thenReturn(Optional.of("borrowed reading"));

        service.chat(withPhoto("ANTHROPIC"));

        assertThat(lastUserMessage().content()).contains("borrowed reading");
    }

    @Test
    @DisplayName("a Cohere VISION model gets the picture itself")
    void cohereVisionModelGetsThePicture() {
        useCohere("command-a-vision-07-2025");

        service.chat(withPhoto("COHERE"));

        assertThat(lastUserMessage().hasImages()).isTrue();
    }

    @Test
    @DisplayName("the DEFAULT provider's vision model gets the picture, with no OCR involved")
    void anthropicVisionModelGetsThePicture() {
        // Every other case here is Mistral, because Mistral is where the blindness problem
        // lives. But Anthropic is the default provider, so the ordinary path — take a photo,
        // send it, the model actually sees it — deserves its own guard. No Mistral key is
        // configured, so this also proves the picture survives without OCR in the picture.
        lenient().when(keyService.getKey(ProviderType.ANTHROPIC))
                .thenReturn(Optional.of(new AiKeyService.StoredKey("chat-key", "claude-sonnet-4-6")));

        service.chat(withPhoto("ANTHROPIC"));

        LlmMessage sent = lastUserMessage();
        assertThat(sent.hasImages()).isTrue();
        assertThat(sent.content()).contains("[Attached image: note.jpg]");
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
