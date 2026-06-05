package com.spiramindscape.backend.ai.chat;

import com.spiramindscape.backend.goal.Goal;
import com.spiramindscape.backend.goal.GoalRepository;
import com.spiramindscape.backend.goal.GoalService;
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
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link GoalContextBuilder}.
 *
 * <p>The key behaviour is what the AI sees on the All-Goals overview (no goal id):
 * a list of the user's goals plus the actions available there (edit a goal's card
 * fields, open a goal, start deletion, create a new goal). With a goal id it
 * returns that goal's full block.
 */
@ExtendWith(MockitoExtension.class)
class GoalContextBuilderTest {

    @Mock private GoalRepository goalRepository;
    @Mock private GoalService goalService;
    @InjectMocks private GoalContextBuilder builder;

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
        when(goalRepository.findById(1L)).thenReturn(Optional.of(goal));

        String context = builder.build(1L);

        assertThat(context)
                .contains("## Current Goal")
                .contains("Goal id:").contains("1")
                .contains("Learn GraphQL")
                .contains("7/10");
        assertThat(context).doesNotContain("All Goals");
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
