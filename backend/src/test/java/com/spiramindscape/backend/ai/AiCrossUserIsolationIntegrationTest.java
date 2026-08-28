package com.spiramindscape.backend.ai;

import com.spiramindscape.backend.ai.chat.GoalContextBuilder;
import com.spiramindscape.backend.ai.chat.ResourceReadService;
import com.spiramindscape.backend.ai.grow.GoalMemoryService;
import com.spiramindscape.backend.ai.proposal.AiProposalService;
import com.spiramindscape.backend.auth.AppUser;
import com.spiramindscape.backend.goal.Goal;
import com.spiramindscape.backend.goal.GoalRepository;
import com.spiramindscape.backend.goal.GoalService;
import com.spiramindscape.backend.resource.Resource;
import com.spiramindscape.backend.resource.ResourceRepository;
import com.spiramindscape.backend.support.BaseGraphQlIntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * **The AI sub-system's own cross-user boundary** (BUG-054), against a real database.
 *
 * <p>{@code CrossUserIsolationIntegrationTest} covers the GraphQL surface, where every read
 * has always gone through an owner-scoped repository method. The AI surface did not: it
 * takes a {@code goalId} in a POST body or on a URL and, until this class existed, passed
 * it straight into the prompt builder, the resource reader and the proposal listing. Three
 * unscoped queries, one root cause, and nothing failing anywhere.
 *
 * <p>The tests below are deliberately at the <b>service</b> level rather than through
 * {@code /api/ai/chat}: the chat endpoint needs a provider key and a live model to say
 * anything at all, whereas these are the exact queries that leaked, run against real rows
 * belonging to two real users. The plumbing that resolves the id before it reaches them is
 * pinned by {@code AiChatServiceGoalScopeTest}.
 *
 * <p>Every check is written the same way round: user B acts, and must see <b>nothing</b> of
 * user A's — not an error naming it, not a partial read, and never a result that differs
 * from what a made-up id returns.
 */
class AiCrossUserIsolationIntegrationTest extends BaseGraphQlIntegrationTest {

    @Autowired private GoalContextBuilder goalContextBuilder;
    @Autowired private ResourceReadService resourceReadService;
    @Autowired private AiProposalService proposalService;
    @Autowired private GoalMemoryService goalMemoryService;
    @Autowired private GoalService goalService;
    @Autowired private ResourceRepository resourceRepository;
    @Autowired private GoalRepository goals;

    private static final String SECRET_TITLE = "Leave the company by March";
    private static final String SECRET_NOTE = "<p>My manager does not know yet</p>";

    private Goal userAGoal;
    private Resource userANote;
    private AppUser userB;

    @BeforeEach
    void twoUsersWithOneSecretGoal() {
        // testUser is user A, and is the current user coming out of the base class.
        userAGoal = new Goal();
        userAGoal.setUser(testUser);
        userAGoal.setTitle(SECRET_TITLE);
        userAGoal.setDescription("<p>and here is exactly why</p>");
        userAGoal.setConfidence(4);
        userAGoal = goals.save(userAGoal);

        userANote = new Resource();
        userANote.setType("note");
        userANote.setTitle("Resignation draft");
        userANote.setBody(SECRET_NOTE);
        userANote.setGoal(userAGoal);
        userANote = resourceRepository.save(userANote);

        userB = createAdditionalUser("user-b-sub", "userB@example.com");
    }

    // ─── The system prompt's goal block ───────────────────────────────────────

    @Test
    @DisplayName("User B naming user A's goal id gets their own overview, not that goal")
    void goalContextIsOwnerScoped() {
        setCurrentUser(userB);

        String context = goalContextBuilder.build(userAGoal.getId());

        assertThat(context).doesNotContain(SECRET_TITLE);
        assertThat(context).doesNotContain("## Current Goal");
        assertThat(context).contains("All Goals");
    }

    @Test
    @DisplayName("The owner still gets the full goal block")
    void goalContextStillWorksForTheOwner() {
        String context = goalContextBuilder.build(userAGoal.getId());

        assertThat(context).contains("## Current Goal").contains(SECRET_TITLE);
    }

    @Test
    @DisplayName("A foreign goal id is indistinguishable from one that never existed")
    void foreignGoalReadsLikeAnUnknownGoal() {
        setCurrentUser(userB);

        assertThat(goalContextBuilder.build(userAGoal.getId()))
                .isEqualTo(goalContextBuilder.build(9_999_999L));
    }

    // ─── The read_resource tool ───────────────────────────────────────────────

    @Test
    @DisplayName("User B cannot read a note on user A's goal, even naming both ids correctly")
    void resourceReadIsOwnerScoped() {
        setCurrentUser(userB);

        // Both ids are right — the resource really is on that goal. The only thing wrong is
        // who is asking, and that is the whole boundary.
        String read = resourceReadService.read(userAGoal.getId(), userANote.getId());

        assertThat(read).isEqualTo("Resource not found.");
        assertThat(read).doesNotContain("manager");
    }

    @Test
    @DisplayName("The owner still reads their own note")
    void resourceReadStillWorksForTheOwner() {
        assertThat(resourceReadService.read(userAGoal.getId(), userANote.getId()))
                .contains("My manager does not know yet");
    }

    @Test
    @DisplayName("User B cannot view an image on user A's goal")
    void resourceImageReadIsOwnerScoped() {
        Resource photo = new Resource();
        photo.setType("file");
        photo.setTitle("Payslip");
        photo.setMime("image/png");
        photo.setDataUrl("data:image/png;base64,QUJD");
        photo.setGoal(userAGoal);
        photo = resourceRepository.save(photo);

        setCurrentUser(userB);

        assertThat(resourceReadService.readImage(userAGoal.getId(), photo.getId())).isEmpty();
    }

    @Test
    @DisplayName("User B cannot resolve user A's resource as a message attachment")
    void attachmentResolutionIsOwnerScoped() {
        setCurrentUser(userB);

        assertThat(resourceReadService.resolveOwnedAttachment(userANote.getId())).isEmpty();
    }

    // ─── The proposal listing ─────────────────────────────────────────────────

    @Test
    @DisplayName("User B does not see user A's pending proposals for a goal")
    void proposalsForGoalAreOwnerScoped() {
        // User A's assistant proposes a change.
        proposalService.create(userAGoal.getId(), "edit_goal", "{\"title\":\"" + SECRET_TITLE + "\"}");

        setCurrentUser(userB);

        assertThat(proposalService.listPendingForGoal(userAGoal.getId())).isEmpty();
    }

    @Test
    @DisplayName("The owner still sees their own pending proposals for that goal")
    void proposalsForGoalStillWorkForTheOwner() {
        proposalService.create(userAGoal.getId(), "edit_goal", "{\"title\":\"x\"}");

        assertThat(proposalService.listPendingForGoal(userAGoal.getId())).hasSize(1);
    }

    // ─── Session memory and the ownership predicate itself ────────────────────

    @Test
    @DisplayName("User B loads no GROW memory from user A's goal")
    void growMemoryIsOwnerScoped() {
        goalMemoryService.append(userAGoal.getId(), "We agreed she would hand in her notice.");

        setCurrentUser(userB);

        assertThat(goalMemoryService.memoryBlock(userAGoal.getId())).isEmpty();
    }

    @Test
    @DisplayName("isOwnedByCurrentUser is false for a foreign goal, a missing goal and null")
    void ownershipPredicate() {
        assertThat(goalService.isOwnedByCurrentUser(userAGoal.getId())).isTrue();

        setCurrentUser(userB);

        assertThat(goalService.isOwnedByCurrentUser(userAGoal.getId())).isFalse();
        assertThat(goalService.isOwnedByCurrentUser(9_999_999L)).isFalse();
        assertThat(goalService.isOwnedByCurrentUser(null)).isFalse();
    }
}
