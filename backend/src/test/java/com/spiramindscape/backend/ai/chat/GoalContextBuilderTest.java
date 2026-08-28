package com.spiramindscape.backend.ai.chat;

import com.spiramindscape.backend.auth.AppUser;
import com.spiramindscape.backend.auth.CurrentUserProvider;
import com.spiramindscape.backend.goal.Goal;
import com.spiramindscape.backend.goal.GoalRepository;
import com.spiramindscape.backend.goal.GoalService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link GoalContextBuilder}.
 *
 * <p>The key behaviour is what the AI sees on the All-Goals overview (no goal id):
 * a list of the user's goals plus the actions available there (edit a goal's card
 * fields, open a goal, start deletion, create a new goal). With a goal id it
 * returns that goal's full block.
 *
 * <p>The other behaviour worth a test is the security boundary (BUG-054): the goal id
 * arrives in the chat request body, so the lookup must be owner-scoped. A goal that
 * belongs to someone else has to be indistinguishable from one that does not exist.
 */
@ExtendWith(MockitoExtension.class)
class GoalContextBuilderTest {

    private static final long CURRENT_USER = 7L;

    @Mock private GoalRepository goalRepository;
    @Mock private GoalService goalService;
    @Mock private CurrentUserProvider currentUserProvider;
    @InjectMocks private GoalContextBuilder builder;

    @BeforeEach
    void signIn() {
        AppUser me = new AppUser();
        me.setId(CURRENT_USER);
        lenient().when(currentUserProvider.getCurrentUser()).thenReturn(me);
    }

    @Test
    @DisplayName("build(null) lists the user's goals and the All-Goals actions")
    void globalContextListsGoals() {
        when(goalService.findAll()).thenReturn(List.of(
                goal(12L, "Learn GraphQL", 7, LocalDate.parse("2026-12-31")),
                goal(15L, "Run a marathon", 4, null)));

        String context = builder.build(null);

        assertThat(context)
                .contains("All Goals")
                .contains("goal id=12").contains("Learn GraphQL").contains("7/10").contains("2026-12-31")
                .contains("goal id=15").contains("Run a marathon").contains("no deadline")
                // available actions are advertised so the AI knows what it may propose
                .contains("edit_goal").contains("open_goal").contains("delete_goal").contains("new_goal");
    }

    @Test
    @DisplayName("build(null) with no goals invites creating one")
    void globalContextEmpty() {
        when(goalService.findAll()).thenReturn(List.of());

        String context = builder.build(null);

        assertThat(context).contains("no goals yet").contains("new_goal");
    }

    @Test
    @DisplayName("build(existingId) returns the goal's context with its id, title and confidence")
    void existingGoalReturnsGoalContext() {
        Goal goal = goal(1L, "Learn GraphQL", 7, null);
        when(goalRepository.findByIdAndUserId(1L, CURRENT_USER)).thenReturn(Optional.of(goal));

        String context = builder.build(1L);

        assertThat(context)
                .contains("## Current Goal")
                .contains("Goal id:").contains("1")
                .contains("Learn GraphQL")
                .contains("7/10");
        assertThat(context).doesNotContain("All Goals");
    }

    // ─── Owner scoping (BUG-054) ──────────────────────────────────────────────

    @Test
    @DisplayName("A goal belonging to another user is never put in the prompt")
    void anotherUsersGoalIsNotReadable() {
        // The row exists, but not for THIS user, so the owner-scoped lookup misses.
        when(goalRepository.findByIdAndUserId(99L, CURRENT_USER)).thenReturn(Optional.empty());
        when(goalService.findAll()).thenReturn(List.of(goal(3L, "My own goal", 5, null)));

        String context = builder.build(99L);

        // Falls back to the caller's OWN overview — no trace of the other goal.
        assertThat(context).contains("All Goals").contains("My own goal");
        assertThat(context).doesNotContain("## Current Goal");
    }

    @Test
    @DisplayName("A goal id is never looked up unscoped")
    void neverUsesTheUnscopedLookup() {
        when(goalRepository.findByIdAndUserId(99L, CURRENT_USER)).thenReturn(Optional.empty());
        when(goalService.findAll()).thenReturn(List.of());

        builder.build(99L);

        // The regression this guards: findById(id) reads any user's row. If it ever comes
        // back, the test above could still pass on a mock that returns empty for both.
        verify(goalRepository, never()).findById(any());
    }

    @Test
    @DisplayName("A goal that does not exist reads exactly like one owned by someone else")
    void missingAndForeignAreIndistinguishable() {
        when(goalRepository.findByIdAndUserId(any(), eq(CURRENT_USER))).thenReturn(Optional.empty());
        when(goalService.findAll()).thenReturn(List.of());

        assertThat(builder.build(99L)).isEqualTo(builder.build(123456L));
    }

    private static Goal goal(Long id, String title, int confidence, LocalDate deadline) {
        Goal g = new Goal();
        g.setId(id);
        g.setTitle(title);
        g.setConfidence(confidence);
        if (deadline != null) {
            g.setDeadline(deadline.atStartOfDay().toInstant(java.time.ZoneOffset.UTC));
        }
        return g;
    }
}
