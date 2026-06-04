package com.spiramindscape.backend.ai.chat;

import com.spiramindscape.backend.goal.Goal;
import com.spiramindscape.backend.goal.GoalRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link GoalContextBuilder}. The key behaviour is what the AI sees
 * when no goal is open (global / All-Goals chat): an explicit "no goal" block that
 * disambiguates "create a goal" requests — including the Russian «цель», which can
 * mean either Goal or Target — so the model proposes a Goal, not a target.
 */
@ExtendWith(MockitoExtension.class)
class GoalContextBuilderTest {

    @Mock private GoalRepository goalRepository;
    @InjectMocks private GoalContextBuilder builder;

    @Test
    @DisplayName("build(null) returns the 'no goal open' context and never hits the repository")
    void nullGoalIdReturnsNoGoalContext() {
        String context = builder.build(null);

        assertThat(context).isEqualTo(GoalContextBuilder.NO_GOAL_CONTEXT);
        assertThat(context)
                .contains("No goal is open")
                .contains("new_goal")
                .contains("цель"); // explicitly disambiguates the Russian word
        verifyNoInteractions(goalRepository);
    }

    @Test
    @DisplayName("build(unknownId) falls back to the 'no goal open' context")
    void unknownGoalIdReturnsNoGoalContext() {
        when(goalRepository.findById(999L)).thenReturn(Optional.empty());

        assertThat(builder.build(999L)).isEqualTo(GoalContextBuilder.NO_GOAL_CONTEXT);
    }

    @Test
    @DisplayName("build(existingId) returns the goal's context block with title and confidence")
    void existingGoalReturnsGoalContext() {
        Goal goal = new Goal();
        goal.setTitle("Learn GraphQL");
        goal.setConfidence(7);
        when(goalRepository.findById(1L)).thenReturn(Optional.of(goal));

        String context = builder.build(1L);

        assertThat(context)
                .contains("## Current Goal")
                .contains("Learn GraphQL")
                .contains("7/10");
        assertThat(context).doesNotContain("No goal is open");
    }
}
