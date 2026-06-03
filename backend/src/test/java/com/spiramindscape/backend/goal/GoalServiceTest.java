package com.spiramindscape.backend.goal;

import com.spiramindscape.backend.graphql.input.CreateGoalInput;
import com.spiramindscape.backend.graphql.input.UpdateGoalInput;
import com.spiramindscape.backend.graphql.input.UpdateOptionInput;
import org.junit.jupiter.api.DisplayName;
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

    // ─── findAll ──────────────────────────────────────────────────────────────

    @Test
    @DisplayName("findAll returns all goals ordered by createdAt from repository")
    void findAllReturnsDelegatedList() {
        Goal first = goal(1L);
        Goal second = goal(2L);
        when(goalRepository.findAllByOrderByCreatedAtAsc()).thenReturn(List.of(first, second));

        List<Goal> result = goalService.findAll();

        assertThat(result).containsExactly(first, second);
    }

    // ─── findById ─────────────────────────────────────────────────────────────

    @Test
    @DisplayName("findById returns goal when it exists")
    void findByIdReturnsGoalWhenFound() {
        Goal goal = goal(1L);
        when(goalRepository.findById(1L)).thenReturn(Optional.of(goal));

        Goal result = goalService.findById(1L);

        assertThat(result).isSameAs(goal);
    }

    @Test
    @DisplayName("findById throws IllegalArgumentException with message when goal does not exist")
    void findByIdThrowsWhenGoalNotFound() {
        when(goalRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> goalService.findById(99L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Goal not found: 99");
    }

    // ─── delete ───────────────────────────────────────────────────────────────

    @Test
    @DisplayName("delete removes goal via repository when goal exists")
    void deleteRemovesGoal() {
        Goal goal = goal(1L);
        when(goalRepository.findById(1L)).thenReturn(Optional.of(goal));

        goalService.delete(1L);

        verify(goalRepository).delete(goal);
    }

    @Test
    @DisplayName("delete throws IllegalArgumentException when goal does not exist")
    void deleteThrowsWhenGoalNotFound() {
        when(goalRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> goalService.delete(99L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Goal not found: 99");

        verify(goalRepository, never()).delete(any(Goal.class));
    }

    // ─── addOption ────────────────────────────────────────────────────────────

    @Test
    @DisplayName("addOption assigns position 0 when goal has no existing options")
    void addOptionAtPositionZeroWhenNoOptionsExist() {
        Goal goal = goal(1L);
        when(goalRepository.findById(1L)).thenReturn(Optional.of(goal));
        when(optionRepository.findMaxPositionByGoalId(1L)).thenReturn(-1);
        when(optionRepository.save(any(Option.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Option option = goalService.addOption(1L, "First option");

        assertThat(option.getPosition()).isZero();
        assertThat(option.getText()).isEqualTo("First option");
        assertThat(option.getSelected()).isFalse();
    }

    @Test
    @DisplayName("addOption: blank text throws with required message and does not save")
    void addOptionRejectsBlankText() {
        when(goalRepository.findById(1L)).thenReturn(Optional.of(goal(1L)));
        when(optionRepository.findMaxPositionByGoalId(1L)).thenReturn(-1);

        assertThatThrownBy(() -> goalService.addOption(1L, "   "))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Option text is required");

        verify(optionRepository, never()).save(any(Option.class));
    }

    @Test
    @DisplayName("addOption: text at the maximum length is accepted")
    void addOptionAcceptsTextAtMaximumLength() {
        Goal goal = goal(1L);
        when(goalRepository.findById(1L)).thenReturn(Optional.of(goal));
        when(optionRepository.findMaxPositionByGoalId(1L)).thenReturn(-1);
        when(optionRepository.save(any(Option.class))).thenAnswer(invocation -> invocation.getArgument(0));
        String maxText = "A".repeat(GoalService.MAX_OPTION_TEXT_LENGTH);

        Option option = goalService.addOption(1L, maxText);

        assertThat(option.getText()).isEqualTo(maxText);
        verify(optionRepository).save(any(Option.class));
    }

    @Test
    @DisplayName("addOption: text over the maximum length throws and does not save")
    void addOptionRejectsOversizedText() {
        when(goalRepository.findById(1L)).thenReturn(Optional.of(goal(1L)));
        when(optionRepository.findMaxPositionByGoalId(1L)).thenReturn(-1);

        assertThatThrownBy(() -> goalService.addOption(1L, "A".repeat(GoalService.MAX_OPTION_TEXT_LENGTH + 1)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Option text must be " + GoalService.MAX_OPTION_TEXT_LENGTH + " characters or fewer");

        verify(optionRepository, never()).save(any(Option.class));
    }

    @Test
    @DisplayName("addOption: throws NOT_FOUND when goal does not exist, option repository not touched")
    void addOptionThrowsWhenGoalNotFound() {
        when(goalRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> goalService.addOption(99L, "Some text"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Goal not found: 99");

        verifyNoInteractions(optionRepository);
    }

    // ─── removeOption ─────────────────────────────────────────────────────────

    @Test
    @DisplayName("removeOption deletes option when goal and option both exist")
    void removeOptionDeletesOption() {
        Goal goal = goal(1L);
        Option option = option(10L, goal, false, 0);
        when(goalRepository.findById(1L)).thenReturn(Optional.of(goal));
        when(optionRepository.findById(10L)).thenReturn(Optional.of(option));

        goalService.removeOption(1L, 10L);

        verify(optionRepository).delete(option);
    }

    @Test
    @DisplayName("removeOption throws NOT_FOUND when option does not exist")
    void removeOptionThrowsWhenOptionNotFound() {
        when(goalRepository.findById(1L)).thenReturn(Optional.of(goal(1L)));
        when(optionRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> goalService.removeOption(1L, 99L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Option not found: 99");

        verify(optionRepository, never()).delete(any(Option.class));
    }

    @Test
    @DisplayName("removeOption throws ValidationError when option belongs to another goal")
    void removeOptionRejectsOptionFromAnotherGoal() {
        Goal goal = goal(1L);
        Option otherGoalOption = option(10L, goal(2L), false, 0);
        when(goalRepository.findById(1L)).thenReturn(Optional.of(goal));
        when(optionRepository.findById(10L)).thenReturn(Optional.of(otherGoalOption));

        assertThatThrownBy(() -> goalService.removeOption(1L, 10L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Option does not belong to goal");

        verify(optionRepository, never()).delete(any(Option.class));
    }

    // ─── updateOption ─────────────────────────────────────────────────────────

    @Test
    @DisplayName("updateOption: blank text throws with required message and does not save")
    void updateOptionRejectsBlankText() {
        Goal goal = goal(1L);
        Option option = option(10L, goal, false, 0);
        when(goalRepository.findById(1L)).thenReturn(Optional.of(goal));
        when(optionRepository.findById(10L)).thenReturn(Optional.of(option));

        assertThatThrownBy(() -> goalService.updateOption(1L, 10L, new UpdateOptionInput("   ", null)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Option text is required");

        verify(optionRepository, never()).save(any(Option.class));
    }

    @Test
    @DisplayName("updateOption: text at the maximum length is accepted")
    void updateOptionAcceptsTextAtMaximumLength() {
        Goal goal = goal(1L);
        Option option = option(10L, goal, false, 0);
        when(goalRepository.findById(1L)).thenReturn(Optional.of(goal));
        when(optionRepository.findById(10L)).thenReturn(Optional.of(option));
        when(optionRepository.save(any(Option.class))).thenAnswer(invocation -> invocation.getArgument(0));
        String maxText = "A".repeat(GoalService.MAX_OPTION_TEXT_LENGTH);

        Option result = goalService.updateOption(1L, 10L, new UpdateOptionInput(maxText, null));

        assertThat(result.getText()).isEqualTo(maxText);
        verify(optionRepository).save(option);
    }

    @Test
    @DisplayName("updateOption: text over the maximum length throws and does not save")
    void updateOptionRejectsOversizedText() {
        Goal goal = goal(1L);
        Option option = option(10L, goal, false, 0);
        when(goalRepository.findById(1L)).thenReturn(Optional.of(goal));
        when(optionRepository.findById(10L)).thenReturn(Optional.of(option));

        assertThatThrownBy(() -> goalService.updateOption(1L, 10L,
                new UpdateOptionInput("A".repeat(GoalService.MAX_OPTION_TEXT_LENGTH + 1), null)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Option text must be " + GoalService.MAX_OPTION_TEXT_LENGTH + " characters or fewer");

        verify(optionRepository, never()).save(any(Option.class));
    }

    @Test
    @DisplayName("updateOption saves option with updated text when option belongs to goal")
    void updateOptionUpdatesText() {
        Goal goal = goal(1L);
        Option option = option(10L, goal, false, 0);
        when(goalRepository.findById(1L)).thenReturn(Optional.of(goal));
        when(optionRepository.findById(10L)).thenReturn(Optional.of(option));
        when(optionRepository.save(any(Option.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Option result = goalService.updateOption(1L, 10L, new UpdateOptionInput("New text", null));

        assertThat(result.getText()).isEqualTo("New text");
        assertThat(result.getSelected()).isFalse();
        verify(optionRepository).save(option);
    }

    @Test
    @DisplayName("updateOption saves option with updated selected flag")
    void updateOptionUpdatesSelectedFlag() {
        Goal goal = goal(1L);
        Option option = option(10L, goal, false, 0);
        when(goalRepository.findById(1L)).thenReturn(Optional.of(goal));
        when(optionRepository.findById(10L)).thenReturn(Optional.of(option));
        when(optionRepository.save(any(Option.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Option result = goalService.updateOption(1L, 10L, new UpdateOptionInput(null, true));

        assertThat(result.getSelected()).isTrue();
        assertThat(result.getText()).isEqualTo("Option 10");
    }

    // ─── findOptions ──────────────────────────────────────────────────────────

    @Test
    @DisplayName("findOptions returns empty list when goal exists but has no options")
    void findOptionsReturnsEmptyListWhenGoalHasNoOptions() {
        when(goalRepository.findById(1L)).thenReturn(Optional.of(goal(1L)));
        when(optionRepository.findByGoalIdOrderByPositionAscCreatedAtAsc(1L)).thenReturn(List.of());

        List<Option> result = goalService.findOptions(1L);

        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("findOptions throws NOT_FOUND when goal does not exist")
    void findOptionsThrowsWhenGoalNotFound() {
        when(goalRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> goalService.findOptions(99L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Goal not found: 99");

        verifyNoInteractions(optionRepository);
    }

    // ─── findOptionsByGoalIds ─────────────────────────────────────────────────

    @Test
    @DisplayName("findOptionsByGoalIds returns empty map for empty input without calling repository")
    void findOptionsByGoalIdsReturnsEmptyMapForEmptyInput() {
        Map<Long, List<Option>> result = goalService.findOptionsByGoalIds(List.of());

        assertThat(result).isEmpty();
        verifyNoInteractions(optionRepository);
    }

    @Test
    @DisplayName("findOptionsByGoalIds groups options by goal id and preserves order")
    void findOptionsByGoalIdsGroupsByGoalId() {
        Goal first = goal(1L);
        Goal second = goal(2L);
        Option a = option(10L, first, false, 0);
        Option b = option(11L, first, false, 1);
        Option c = option(12L, second, false, 0);
        when(optionRepository.findByGoalIdInOrderByGoalIdAscPositionAscCreatedAtAsc(List.of(1L, 2L)))
                .thenReturn(List.of(a, b, c));

        Map<Long, List<Option>> result = goalService.findOptionsByGoalIds(List.of(1L, 2L));

        assertThat(result.get(1L)).containsExactly(a, b);
        assertThat(result.get(2L)).containsExactly(c);
    }

    // ─── findConfidenceHistoryByGoalIds ───────────────────────────────────────

    @Test
    @DisplayName("findConfidenceHistoryByGoalIds returns empty map for empty input without calling repository")
    void findConfidenceHistoryByGoalIdsReturnsEmptyMapForEmptyInput() {
        Map<Long, List<ConfidenceHistory>> result = goalService.findConfidenceHistoryByGoalIds(List.of());

        assertThat(result).isEmpty();
        verifyNoInteractions(confidenceHistoryRepository);
    }

    @Test
    @DisplayName("findConfidenceHistoryByGoalIds groups history entries by goal id")
    void findConfidenceHistoryByGoalIdsGroupsByGoalId() {
        Goal first = goal(1L);
        Goal second = goal(2L);
        ConfidenceHistory h1 = history(1L, first, 7);
        ConfidenceHistory h2 = history(2L, first, 5);
        ConfidenceHistory h3 = history(3L, second, 8);
        when(confidenceHistoryRepository.findByGoalIdInOrderByAtDesc(List.of(1L, 2L)))
                .thenReturn(List.of(h1, h2, h3));

        Map<Long, List<ConfidenceHistory>> result = goalService.findConfidenceHistoryByGoalIds(List.of(1L, 2L));

        assertThat(result.get(1L)).containsExactly(h1, h2);
        assertThat(result.get(2L)).containsExactly(h3);
    }

    // ─── create ───────────────────────────────────────────────────────────────

    @Test
    @DisplayName("create: null description defaults to empty string")
    void createsGoalWithNullDescriptionDefaultsToEmptyString() {
        when(goalRepository.save(any(Goal.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Goal goal = goalService.create(new CreateGoalInput("Learn GraphQL", null, 5, null));

        assertThat(goal.getDescription()).isEqualTo("");
    }

    @Test
    @DisplayName("create: title at the maximum length is accepted")
    void createsGoalWithTitleAtMaximumLength() {
        when(goalRepository.save(any(Goal.class))).thenAnswer(invocation -> invocation.getArgument(0));
        String maxTitle = "A".repeat(GoalService.MAX_GOAL_TITLE_LENGTH);

        Goal goal = goalService.create(new CreateGoalInput(maxTitle, null, 5, null));

        assertThat(goal.getTitle()).isEqualTo(maxTitle);
        verify(goalRepository).save(any(Goal.class));
    }

    @Test
    @DisplayName("create: null title throws with required message and does not save")
    void rejectsGoalTitleOnCreateWhenNull() {
        assertThatThrownBy(() -> goalService.create(new CreateGoalInput(null, null, 5, null)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Goal title is required");

        verify(goalRepository, never()).save(any(Goal.class));
    }

    @Test
    @DisplayName("create: blank title throws with required message and does not save")
    void rejectsGoalTitleOnCreateWhenBlank() {
        assertThatThrownBy(() -> goalService.create(new CreateGoalInput("   ", null, 5, null)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Goal title is required");

        verify(goalRepository, never()).save(any(Goal.class));
    }

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
                .hasMessageContaining("Goal title must be " + GoalService.MAX_GOAL_TITLE_LENGTH + " characters or fewer");

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
                .hasMessageContaining("Goal description must be " + GoalService.MAX_GOAL_DESCRIPTION_LENGTH + " characters or fewer");

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
    @DisplayName("update: title is trimmed before storing")
    void trimsGoalTitleOnUpdate() {
        Goal goal = goal(1L);
        when(goalRepository.findById(1L)).thenReturn(Optional.of(goal));
        when(goalRepository.save(any(Goal.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Goal updated = goalService.update(1L,
                new UpdateGoalInput("   Updated title   ", null, null, null, null));

        assertThat(updated.getTitle()).isEqualTo("Updated title");
        verify(goalRepository).save(any(Goal.class));
    }

    @Test
    @DisplayName("update: description is changed to new non-empty value")
    void updatesGoalDescriptionToNewValue() {
        Goal goal = goal(1L);
        goal.setDescription("Old description");
        when(goalRepository.findById(1L)).thenReturn(Optional.of(goal));
        when(goalRepository.save(any(Goal.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Goal updated = goalService.update(1L,
                new UpdateGoalInput(null, "New description", null, null, null));

        assertThat(updated.getDescription()).isEqualTo("New description");
        verify(goalRepository).save(any(Goal.class));
    }

    @Test
    @DisplayName("update: rejects description longer than the maximum length")
    void rejectsGoalDescriptionOverMaxLengthOnUpdate() {
        Goal goal = goal(1L);
        when(goalRepository.findById(1L)).thenReturn(Optional.of(goal));

        assertThatThrownBy(() -> goalService.update(1L,
                new UpdateGoalInput(null, "A".repeat(GoalService.MAX_GOAL_DESCRIPTION_LENGTH + 1), null, null, null)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Goal description must be " + GoalService.MAX_GOAL_DESCRIPTION_LENGTH + " characters or fewer");

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
    @DisplayName("selectOption throws NOT_FOUND when option does not exist")
    void selectOptionThrowsWhenOptionNotFound() {
        when(goalRepository.findById(1L)).thenReturn(Optional.of(goal(1L)));
        when(optionRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> goalService.selectOption(1L, 99L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Option not found: 99");

        verify(optionRepository, never()).saveAll(any());
    }

    @Test
    @DisplayName("selectOption throws NOT_FOUND when goal does not exist, option repository not touched")
    void selectOptionThrowsWhenGoalNotFound() {
        when(goalRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> goalService.selectOption(99L, 10L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Goal not found: 99");

        verifyNoInteractions(optionRepository);
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
    @DisplayName("reorderOptions throws ValidationError when optionIds count does not match goal's option count")
    void reorderOptionsRejectsWrongCount() {
        Goal goal = goal(1L);
        Option first  = option(10L, goal, false, 0);
        Option second = option(11L, goal, false, 1);
        Option third  = option(12L, goal, false, 2);
        when(goalRepository.findById(1L)).thenReturn(Optional.of(goal));
        when(optionRepository.findByGoalIdOrderByPositionAscCreatedAtAsc(1L))
                .thenReturn(List.of(first, second, third));

        // Pass only two ids instead of three
        assertThatThrownBy(() -> goalService.reorderOptions(1L, List.of(10L, 11L)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Option ids list must contain all options");

        verify(optionRepository, never()).saveAll(any());
    }

    @Test
    @DisplayName("reorderOptions throws NOT_FOUND when goal does not exist, option repository not touched")
    void reorderOptionsThrowsWhenGoalNotFound() {
        when(goalRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> goalService.reorderOptions(99L, List.of(10L, 11L)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Goal not found: 99");

        verifyNoInteractions(optionRepository);
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

    private static ConfidenceHistory history(Long id, Goal goal, int confidence) {
        ConfidenceHistory h = new ConfidenceHistory();
        h.setGoal(goal);
        h.setConfidence(confidence);
        h.setAt(java.time.Instant.now());
        return h;
    }
}
