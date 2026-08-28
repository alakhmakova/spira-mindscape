package com.spiramindscape.backend.ai.chat;

import com.spiramindscape.backend.ai.grow.GoalMemoryService;
import com.spiramindscape.backend.ai.key.AiKeyService;
import com.spiramindscape.backend.ai.prompt.PromptResources;
import com.spiramindscape.backend.ai.provider.LlmProviderFactory;
import com.spiramindscape.backend.ai.provider.cohere.CohereVisionReader;
import com.spiramindscape.backend.ai.provider.mistral.MistralOcrService;
import com.spiramindscape.backend.ai.proposal.AiProposalService;
import com.spiramindscape.backend.ai.safety.AbuseAuditLogger;
import com.spiramindscape.backend.goal.GoalService;
import com.spiramindscape.backend.ai.safety.SafetyService;
import com.spiramindscape.backend.ai.search.TavilySearchService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * **What a GROW session leaves behind** — the record the user is offered to save.
 *
 * <p>The coach used to hand back one free-text {@code summary}, and what arrived was prose: the
 * outcome, the block and the commitment dissolved into a paragraph. Readable once, useless
 * afterwards — a later session cannot pick any of the three out of it, and neither can the person
 * reading the card (owner, 2026-08-24: "должно прослеживаться четко outcome, blocks and
 * commitment"). Worse, before that the card saved **the coach's last chat message**, which by then
 * was the goodbye.
 *
 * <p>The fix is structural rather than exhortative: {@code end_session} takes three fields, and
 * this composes them. Asking prose to have a shape does not give it one.
 */
@ExtendWith(MockitoExtension.class)
class SessionRecordTest {

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

    private AiChatService service;

    @BeforeEach
    void setUp() {
        service = new AiChatService(safety, abuseAuditLogger, keyService, providerFactory,
                goalContextBuilder, searchService, proposalService, resourceReadService,
                urlReadService, new PromptResources(), goalMemory, mistralOcr, cohereVision, goalService);
    }

    @Test
    @DisplayName("the three parts are each named, so a later session can find them")
    void composesTheThreeParts() {
        String record = service.composeSessionRecord("""
                {"outcome":"Send three applications a week, starting Monday",
                 "blocks":"Waiting for an answer feels like a reason to pause",
                 "commitment":"Two applications before Friday, whatever the recruiter says"}
                """);

        assertThat(record)
                .contains("**Outcome:**")
                .contains("Send three applications a week, starting Monday")
                .contains("**What was in the way:**")
                .contains("Waiting for an answer feels like a reason to pause")
                .contains("**Commitment:**")
                .contains("Two applications before Friday");
        // The order is the order of the work: what they want, what stopped them, what they will do.
        assertThat(record.indexOf("**Outcome:**"))
                .isLessThan(record.indexOf("**What was in the way:**"));
        assertThat(record.indexOf("**What was in the way:**"))
                .isLessThan(record.indexOf("**Commitment:**"));
    }

    @Test
    @DisplayName("the block's KIND is written down, so the next session can recognise the wall")
    void keepsTheBlockKind() {
        // The method has always asked the coach to sort the block into what kind of thing it was —
        // a belief, an assumption, a fear, an unmet need — precisely so the next session can tell
        // whether it is meeting the same wall in new clothes. It also said to keep that "for your
        // own use", which meant it was thought and then thrown away: nothing carried it forward.
        // The record IS what carries things forward, so it belongs in the record.
        String record = service.composeSessionRecord("""
                {"outcome":"Two applications before Friday",
                 "blocks":"Waiting feels like a reason to stop",
                 "block_kind":"an unexamined belief",
                 "commitment":"Send one on Wednesday"}
                """);

        assertThat(record).contains("Waiting feels like a reason to stop");
        assertThat(record).contains("an unexamined belief");
        // Beside the block, not a heading of its own — it qualifies the block, it is not a part.
        assertThat(record).doesNotContain("**Block kind");
    }

    @Test
    @DisplayName("no kind named — the record simply doesn't mention one")
    void omitsAnUnknownBlockKind() {
        String record = service.composeSessionRecord("""
                {"outcome":"An outcome","blocks":"A block","block_kind":"","commitment":"c"}
                """);

        assertThat(record).contains("**What was in the way:** A block")
                .doesNotContain("_(");
    }

    @Test
    @DisplayName("a session that reached no commitment SAYS so — it is never left out")
    void statesTheMissingCommitment() {
        // The alternative — omitting the heading — makes a session that stopped short read like
        // one that finished, and the next session opens believing something is underway.
        String record = service.composeSessionRecord("""
                {"outcome":"Keep applying while an interview is outstanding",
                 "blocks":"The not-knowing",
                 "commitment":"",
                 "not_reached":"What a concrete first step would be"}
                """);

        assertThat(record).contains("**Commitment:**").contains("No commitment yet.");
        assertThat(record).contains("**Not reached:**")
                .contains("What a concrete first step would be");
    }

    @Test
    @DisplayName("'not reached' is absent when the session did reach its end")
    void omitsNotReachedWhenTheSessionCompleted() {
        String record = service.composeSessionRecord("""
                {"outcome":"An outcome","blocks":"A block","commitment":"A commitment"}
                """);

        assertThat(record).doesNotContain("**Not reached:**");
    }

    @Test
    @DisplayName("a coach that still sends one blob has its words kept, not dropped")
    void fallsBackToTheOldSingleField() {
        // Providers differ in how strictly they follow a changed tool schema, and a record is the
        // one thing in a session that must not be lost to a shape mismatch.
        String record = service.composeSessionRecord("""
                {"summary":"We named the freeze and stopped there."}
                """);

        assertThat(record).isEqualTo("We named the freeze and stopped there.");
    }

    @Test
    @DisplayName("an unusable payload yields an empty record, never an exception")
    void survivesRubbish() {
        assertThat(service.composeSessionRecord("not json at all")).isEmpty();
        assertThat(service.composeSessionRecord(null)).isEmpty();
        assertThat(service.composeSessionRecord("{}")).isEmpty();
    }
}
