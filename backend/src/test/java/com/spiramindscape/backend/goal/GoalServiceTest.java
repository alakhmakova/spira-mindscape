package com.spiramindscape.backend.goal;

import com.spiramindscape.backend.graphql.input.CreateGoalInput;
import com.spiramindscape.backend.graphql.input.UpdateGoalInput;
import com.spiramindscape.backend.graphql.input.UpdateOptionInput;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class GoalServiceTest {

    @Mock
    private GoalRepository goalRepository;

    @Mock
    private OptionRepository optionRepository;
    @Mock
    private ConfidenceHistoryRepository confidenceHistoryRepository;

    @InjectMocks
    private GoalService goalService;

    @Test
    void createsGoalWithTrimmedTitleAndDescription() {
        when(goalRepository.save(any(Goal.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Goal goal = goalService.create(new CreateGoalInput(
                "   Learn GraphQL   ",
                "   Study resolvers   ",
                5,
                null
        ));

        assertThat(goal.getTitle()).isEqualTo("Learn GraphQL");
        assertThat(goal.getDescription()).isEqualTo("Study resolvers");
        assertThat(goal.getConfidence()).isEqualTo(5);
    }

    @Test
    void createsGoalWithDescriptionAtMaximumLength() {
        when(goalRepository.save(any(Goal.class))).thenAnswer(invocation -> invocation.getArgument(0));
        String description = "A".repeat(GoalService.MAX_GOAL_DESCRIPTION_LENGTH);

        Goal goal = goalService.create(new CreateGoalInput(
                "Learn GraphQL",
                description,
                5,
                null
        ));

        assertThat(goal.getDescription()).isEqualTo(description);
    }

    @Test
    void rejectsGoalTitleLongerThanMaximumLength() {
        assertThatThrownBy(() -> goalService.create(new CreateGoalInput(
                "A".repeat(GoalService.MAX_GOAL_TITLE_LENGTH + 1),
                null,
                5,
                null
        )))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Goal title must be 200 characters or fewer");

        verify(goalRepository, never()).save(any(Goal.class));
    }

    @Test
    void rejectsGoalDescriptionLongerThanMaximumLength() {
        assertThatThrownBy(() -> goalService.create(new CreateGoalInput(
                "Learn GraphQL",
                "A".repeat(GoalService.MAX_GOAL_DESCRIPTION_LENGTH + 1),
                5,
                null
        )))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Goal description must be 5000 characters or fewer");

        verify(goalRepository, never()).save(any(Goal.class));
    }

    @Test
    void updatesAndClearsGoalDescriptionWhenExplicitNullProvided() {
        Goal goal = goal(1L);
        goal.setDescription("Before");
        when(goalRepository.findById(1L)).thenReturn(Optional.of(goal));
        when(goalRepository.save(any(Goal.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Goal updated = goalService.update(1L,
                new UpdateGoalInput(null, null, null, null, null),
                Collections.singletonMap("description", null));

        assertThat(updated.getTitle()).isEqualTo("Goal 1");
        assertThat(updated.getDescription()).isEqualTo("");
    }

    @Test
    void updateGoalIgnoresExplicitNullTitle() {
        Goal goal = goal(1L);
        when(goalRepository.findById(1L)).thenReturn(Optional.of(goal));
        when(goalRepository.save(any(Goal.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Goal updated = goalService.update(1L,
                new UpdateGoalInput(null, null, null, null, null),
                Collections.singletonMap("title", null));

        assertThat(updated.getTitle()).isEqualTo("Goal 1");
    }

    @Test
    void rejectsUpdateGoalWithBlankTitleAfterTrimming() {
        Goal goal = goal(1L);
        when(goalRepository.findById(1L)).thenReturn(Optional.of(goal));

        assertThatThrownBy(() -> goalService.update(1L,
                new UpdateGoalInput("   ", null, null, null, null),
                Map.of("title", "   ")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Goal title is required");

        verify(goalRepository, never()).save(any(Goal.class));
    }

    @Test
    void addsOptionAtNextPositionUnselected() {
        Goal goal = goal(1L);
        when(goalRepository.findById(1L)).thenReturn(Optional.of(goal));
        when(optionRepository.findMaxPositionByGoalId(1L)).thenReturn(2);
        when(optionRepository.save(any(Option.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Option option = goalService.addOption(1L, "Try smaller scope");

        assertThat(option.getGoal()).isSameAs(goal);
        assertThat(option.getText()).isEqualTo("Try smaller scope");
        assertThat(option.getSelected()).isFalse();
        assertThat(option.getPosition()).isEqualTo(3);
    }

    @Test
    void selectOptionSelectsOneAndDeselectsOthers() {
        Goal goal = goal(1L);
        Option first = option(10L, goal, true, 0);
        Option second = option(11L, goal, false, 1);
        Option third = option(12L, goal, true, 2);
        when(goalRepository.findById(1L)).thenReturn(Optional.of(goal));
        when(optionRepository.findById(11L)).thenReturn(Optional.of(second));
        when(optionRepository.findByGoalIdOrderByPositionAscCreatedAtAsc(1L))
                .thenReturn(List.of(first, second, third));

        Option selected = goalService.selectOption(1L, 11L);

        assertThat(selected).isSameAs(second);
        assertThat(first.getSelected()).isFalse();
        assertThat(second.getSelected()).isTrue();
        assertThat(third.getSelected()).isFalse();
        verify(optionRepository).saveAll(List.of(first, second, third));
    }

    @Test
    void updateOptionRejectsOptionFromAnotherGoal() {
        Goal goal = goal(1L);
        Option otherGoalOption = option(10L, goal(2L), false, 0);
        when(goalRepository.findById(1L)).thenReturn(Optional.of(goal));
        when(optionRepository.findById(10L)).thenReturn(Optional.of(otherGoalOption));

        assertThatThrownBy(() -> goalService.updateOption(1L, 10L, new UpdateOptionInput("Updated", null)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Option does not belong to goal");
    }

    @Test
    void reorderOptionsRejectsMissingOptionId() {
        Goal goal = goal(1L);
        Option first = option(10L, goal, false, 0);
        Option second = option(11L, goal, false, 1);
        when(goalRepository.findById(1L)).thenReturn(Optional.of(goal));
        when(optionRepository.findByGoalIdOrderByPositionAscCreatedAtAsc(1L))
                .thenReturn(List.of(first, second));

        assertThatThrownBy(() -> goalService.reorderOptions(1L, List.of(10L, 99L)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Option not found or does not belong to goal: 99");
    }

    @Test
    void updatesAchievedAt() {
        Goal goal = goal(1L);
        when(goalRepository.findById(1L)).thenReturn(Optional.of(goal));
        when(goalRepository.save(any(Goal.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Instant achievedAt = Instant.parse("2026-05-16T10:00:00Z");
        Goal updated = goalService.update(1L,
                new UpdateGoalInput(null, null, null, null, achievedAt));

        assertThat(updated.getAchievedAt()).isEqualTo(achievedAt);
    }

    @Test
    void clearsAchievedAtWhenExplicitNullProvided() {
        Goal goal = goal(1L);
        goal.setAchievedAt(Instant.now());
        when(goalRepository.findById(1L)).thenReturn(Optional.of(goal));
        when(goalRepository.save(any(Goal.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Goal updated = goalService.update(1L,
                new UpdateGoalInput(null, null, null, null, null),
                Collections.singletonMap("achievedAt", null));

        assertThat(updated.getAchievedAt()).isNull();
    }

    @Test
    void createsConfidenceHistoryOnGoalCreation() {
        when(goalRepository.save(any(Goal.class))).thenAnswer(invocation -> {
            Goal g = invocation.getArgument(0);
            g.setId(10L);
            return g;
        });

        goalService.create(new CreateGoalInput("Test", null, 7, null));

        verify(confidenceHistoryRepository).save(argThat(h ->
                h.getConfidence() == 7 && h.getGoal().getId() == 10L
        ));
    }

    @Test
    void createsConfidenceHistoryOnGoalUpdateWhenConfidenceChanged() {
        Goal goal = goal(1L);
        goal.setConfidence(5);
        when(goalRepository.findById(1L)).thenReturn(Optional.of(goal));
        when(goalRepository.save(any(Goal.class))).thenAnswer(invocation -> invocation.getArgument(0));

        goalService.update(1L, new UpdateGoalInput(null, null, 8, null, null));

        verify(confidenceHistoryRepository).save(argThat(h ->
                h.getConfidence() == 8 && h.getGoal().getId() == 1L
        ));
    }

    @Test
    void doesNotCreateConfidenceHistoryWhenConfidenceNotChanged() {
        Goal goal = goal(1L);
        goal.setConfidence(5);
        when(goalRepository.findById(1L)).thenReturn(Optional.of(goal));
        when(goalRepository.save(any(Goal.class))).thenAnswer(invocation -> invocation.getArgument(0));

        goalService.update(1L, new UpdateGoalInput("New Title", null, null, null, null));
        goalService.update(1L, new UpdateGoalInput(null, null, 5, null, null));

        verify(confidenceHistoryRepository, never()).save(any());
    }

    private static Goal goal(Long id) {
        Goal goal = new Goal();
        goal.setId(id);
        goal.setTitle("Goal " + id);
        goal.setDescription("");
        goal.setConfidence(7);
        return goal;
    }

    private static Option option(Long id, Goal goal, boolean selected, int position) {
        Option option = new Option();
        option.setId(id);
        option.setGoal(goal);
        option.setText("Option " + id);
        option.setSelected(selected);
        option.setPosition(position);
        return option;
    }
}
