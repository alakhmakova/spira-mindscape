package com.spiramindscape.backend.ai.chat;

import com.spiramindscape.backend.ai.grow.GoalMemoryService;
import com.spiramindscape.backend.ai.key.AiKeyService;
import com.spiramindscape.backend.ai.prompt.PromptResources;
import com.spiramindscape.backend.ai.provider.LlmProviderFactory;
import com.spiramindscape.backend.ai.provider.cohere.CohereVisionReader;
import com.spiramindscape.backend.ai.provider.mistral.MistralOcrService;
import com.spiramindscape.backend.ai.proposal.AiProposalService;
import com.spiramindscape.backend.ai.safety.AbuseAuditLogger;
import com.spiramindscape.backend.ai.safety.SafetyService;
import com.spiramindscape.backend.ai.search.TavilySearchService;
import com.spiramindscape.backend.goal.GoalService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * **What the user actually reads when a provider refuses** (BUG-058).
 *
 * <p>This is the one piece of text in the AI path that is written for the person, not the model,
 * and it shipped in a shape that hid the answer. The message was truncated at 300 characters —
 * and every provider spends its opening on an apology and a documentation link, putting the
 * specifics at the end. Google's quota refusal is 405 characters, so the cut landed mid-word:
 *
 * <pre>
 * …To monitor your current usage, head to: https://ai.dev/rate-limit.
 * * Quota exceeded for metric: generativelanguage.googleapis.c…
 * </pre>
 *
 * <p>Everything that would have told the owner what to do — {@code limit: 20},
 * {@code model: gemini-3.5-flash}, {@code Please retry in 57.8s} — was in the part thrown away.
 * They reported the failure to us as that exact truncated string, having no way to know which
 * model or which allowance was involved.
 */
@ExtendWith(MockitoExtension.class)
class ProviderErrorMessageTest {

    /** Google's real answer, copied from the local backend log on 2026-08-28. */
    private static final String GEMINI_QUOTA_ERROR = """
            Gemini API error 429: [{
                "error": {
                  "code": 429,
                  "message": "You exceeded your current quota, please check your plan and \
            billing details. For more information on this error, head to: \
            https://ai.google.dev/gemini-api/docs/rate-limits. To monitor your current usage, \
            head to: https://ai.dev/rate-limit. \\n* Quota exceeded for metric: \
            generativelanguage.googleapis.com/generate_content_free_tier_requests, limit: 20, \
            model: gemini-3.5-flash\\nPlease retry in 57.880548541s.",
                  "status": "RESOURCE_EXHAUSTED"
                }
            }]
            """;

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

    private AiChatService service() {
        return new AiChatService(safety, abuseAuditLogger, keyService, providerFactory,
                goalContextBuilder, searchService, proposalService, resourceReadService,
                urlReadService, new PromptResources(), goalMemory, mistralOcr, cohereVision, goalService);
    }

    @Test
    @DisplayName("A quota refusal keeps the model, the limit and the wait — the parts to act on")
    void keepsTheActionablePartOfAQuotaError() {
        String shown = service().friendlyError(new RuntimeException(GEMINI_QUOTA_ERROR));

        // Each of these was cut off by the old 300-character truncation, and each is the
        // difference between "something went wrong" and "switch model or enable billing".
        assertThat(shown).contains("limit: 20");
        assertThat(shown).contains("gemini-3.5-flash");
        assertThat(shown).contains("57.8");
    }

    @Test
    @DisplayName("It still says what happened, in the provider's own words")
    void keepsTheExplanation() {
        String shown = service().friendlyError(new RuntimeException(GEMINI_QUOTA_ERROR));

        assertThat(shown).contains("exceeded your current quota");
        assertThat(shown).doesNotContain("RESOURCE_EXHAUSTED");   // the JSON envelope stays out
    }

    @Test
    @DisplayName("A provider that writes an essay is still cut, so a chat bubble stays a bubble")
    void aVeryLongMessageIsStillBounded() {
        String essay = "The provider says: " + "blah ".repeat(500);

        String shown = service().friendlyError(new RuntimeException(
                "OpenAI API error 400: {\"error\":{\"message\":\"" + essay + "\"}}"));

        assertThat(shown.length()).isLessThanOrEqualTo(601);   // the cap plus the ellipsis
        assertThat(shown).endsWith("…");
    }

    /** Mistral's whole answer to a per-minute limit, from Cloud Run on 2026-08-30. */
    private static final String MISTRAL_RATE_LIMIT =
            "Mistral API error 429: {\"object\":\"error\",\"message\":\"Rate limit exceeded\","
            + "\"type\":\"rate_limited\",\"param\":null,\"code\":\"1300\",\"raw_status_code\":429}";

    @Test
    @DisplayName("A rate limit says what to do, because the provider's own three words do not")
    void aRateLimitIsExplained() {
        // "Rate limit exceeded" is the entire message the owner saw, twice on one screen. It
        // does not say who is limiting them, that it clears by itself, or that the app already
        // waited and tried again.
        String shown = service().friendlyError(new RuntimeException(MISTRAL_RATE_LIMIT));

        assertThat(shown).contains("per minute");
        assertThat(shown).contains("about a minute");
        assertThat(shown).contains("Rate limit exceeded");   // the provider still speaks
    }

    @Test
    @DisplayName("A spent quota is NOT dressed up as a wait — nothing reopens in a minute")
    void aQuotaErrorIsNotCalledARateLimit() {
        // The advice would be a lie here, and it would send the user back to try again into an
        // allowance that is gone until their billing period turns over.
        String shown = service().friendlyError(new RuntimeException(GEMINI_QUOTA_ERROR));

        assertThat(shown).doesNotContain("about a minute");
        assertThat(shown).startsWith("You exceeded your current quota");
    }

    @Test
    @DisplayName("A failure with no provider text still gets a sentence, not a status code")
    void fallsBackToAReadableHint() {
        // No JSON to mine — the user must still be told something they can act on.
        assertThat(service().friendlyError(new RuntimeException("401 Unauthorized")))
                .contains("API key");
        assertThat(service().friendlyError(new RuntimeException("boom")))
                .isEqualTo("AI service error. Please try again.");
    }
}
