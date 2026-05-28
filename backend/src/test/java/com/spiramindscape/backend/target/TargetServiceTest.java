package com.spiramindscape.backend.target;

import com.spiramindscape.backend.goal.Goal;
import com.spiramindscape.backend.goal.GoalRepository;
import com.spiramindscape.backend.graphql.input.ChecklistItemInput;
import com.spiramindscape.backend.graphql.input.CreateTargetInput;
import com.spiramindscape.backend.graphql.input.UpdateTargetInput;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.data.Offset.offset;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TargetServiceTest {

    @Mock
    private TargetRepository targetRepository;

    @Mock
    private ChecklistItemRepository checklistItemRepository;

    @Mock
    private GoalRepository goalRepository;

    @InjectMocks
    private TargetService targetService;

    // ─── findById ─────────────────────────────────────────────────────────────

    @Test
    @DisplayName("findById returns target when it exists")
    void findByIdReturnsTargetWhenFound() {
        Target target = binaryTarget(10L, goal(1L), false);
        when(targetRepository.findById(10L)).thenReturn(Optional.of(target));

        Target result = targetService.findById(10L);

        assertThat(result).isSameAs(target);
    }

    @Test
    @DisplayName("findById throws IllegalArgumentException when target does not exist")
    void findByIdThrowsWhenTargetNotFound() {
        when(targetRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> targetService.findById(99L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Target not found: 99");
    }

    // ─── delete ───────────────────────────────────────────────────────────────

    @Test
    @DisplayName("delete removes target via repository when target exists")
    void deleteRemovesTarget() {
        Target target = binaryTarget(10L, goal(1L), false);
        when(targetRepository.findById(10L)).thenReturn(Optional.of(target));

        targetService.delete(10L);

        verify(targetRepository).delete(target);
    }

    @Test
    @DisplayName("delete throws IllegalArgumentException when target does not exist")
    void deleteThrowsWhenTargetNotFound() {
        when(targetRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> targetService.delete(99L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Target not found: 99");

        verify(targetRepository, never()).delete(any(Target.class));
    }

    // ─── calculateGoalProgressByGoalIds ───────────────────────────────────────

    @Test
    void calculatesGoalProgressAsAverageOfTargetProgress() {
        Goal goal = goal(1L);
        Target numeric = numericTarget(11L, goal, 0d, 5d, 10d);
        Target binary = binaryTarget(12L, goal, true);
        Target checklist = checklistTarget(13L, goal);

        when(targetRepository.findByGoalIdInOrderByGoalIdAscCreatedAtAsc(List.of(1L)))
                .thenReturn(List.of(numeric, binary, checklist));
        when(checklistItemRepository.findByTargetIdInOrderByTargetIdAscCreatedAtAsc(List.of(13L)))
                .thenReturn(List.of(
                        checklistItem(21L, checklist, true),
                        checklistItem(22L, checklist, false)
                ));

        Map<Long, Double> result = targetService.calculateGoalProgressByGoalIds(List.of(1L));

        assertThat(result.get(1L)).isCloseTo(2d / 3d, offset(0.000001d));
    }

    @Test
    @DisplayName("Goal progress is 1.0 when single binary target is done")
    void calculatesGoalProgressAsOneWhenSingleBinaryTargetIsDone() {
        Goal goal = goal(1L);
        Target binary = binaryTarget(11L, goal, true);

        when(targetRepository.findByGoalIdInOrderByGoalIdAscCreatedAtAsc(List.of(1L)))
                .thenReturn(List.of(binary));

        Map<Long, Double> result = targetService.calculateGoalProgressByGoalIds(List.of(1L));

        assertThat(result.get(1L)).isEqualTo(1d);
        verifyNoInteractions(checklistItemRepository);
    }

    @Test
    @DisplayName("Goal progress is 1.0 when single numeric target reaches its total")
    void calculatesGoalProgressAsOneWhenSingleNumericTargetReachesTotal() {
        Goal goal = goal(1L);
        Target numeric = numericTarget(11L, goal, 0d, 10d, 10d);

        when(targetRepository.findByGoalIdInOrderByGoalIdAscCreatedAtAsc(List.of(1L)))
                .thenReturn(List.of(numeric));

        Map<Long, Double> result = targetService.calculateGoalProgressByGoalIds(List.of(1L));

        assertThat(result.get(1L)).isEqualTo(1d);
        verifyNoInteractions(checklistItemRepository);
    }

    @Test
    @DisplayName("Goal progress is 1.0 when single checklist target has all items done")
    void calculatesGoalProgressAsOneWhenSingleChecklistTargetAllItemsDone() {
        Goal goal = goal(1L);
        Target checklist = checklistTarget(13L, goal);

        when(targetRepository.findByGoalIdInOrderByGoalIdAscCreatedAtAsc(List.of(1L)))
                .thenReturn(List.of(checklist));
        when(checklistItemRepository.findByTargetIdInOrderByTargetIdAscCreatedAtAsc(List.of(13L)))
                .thenReturn(List.of(
                        checklistItem(21L, checklist, true),
                        checklistItem(22L, checklist, true)
                ));

        Map<Long, Double> result = targetService.calculateGoalProgressByGoalIds(List.of(1L));

        assertThat(result.get(1L)).isEqualTo(1d);
    }

    @Test
    @DisplayName("Goal progress is calculated independently for each goal")
    void calculatesGoalProgressIndependentlyForEachGoal() {
        Goal goal1 = goal(1L);
        Goal goal2 = goal(2L);

        Target binary = binaryTarget(11L, goal1, true);
        Target numeric = numericTarget(12L, goal2, 0d, 5d, 10d);
        Target checklist = checklistTarget(13L, goal2);

        when(targetRepository.findByGoalIdInOrderByGoalIdAscCreatedAtAsc(List.of(1L, 2L)))
                .thenReturn(List.of(binary, numeric, checklist));
        when(checklistItemRepository.findByTargetIdInOrderByTargetIdAscCreatedAtAsc(List.of(13L)))
                .thenReturn(List.of(
                        checklistItem(21L, checklist, true),
                        checklistItem(22L, checklist, false)
                ));

        Map<Long, Double> result = targetService.calculateGoalProgressByGoalIds(List.of(1L, 2L));

        assertThat(result.get(1L)).isEqualTo(1d);
        assertThat(result.get(2L)).isCloseTo(0.5d, offset(0.000001d));
    }

    @Test
    void calculatesChecklistProgressFromRepositoryItemsInsteadOfTargetCollection() {
        Goal goal = goal(1L);
        Target checklist = checklistTarget(13L, goal);
        checklist.getItems().clear();

        when(checklistItemRepository.findByTargetIdInOrderByTargetIdAscCreatedAtAsc(List.of(13L)))
                .thenReturn(List.of(
                        checklistItem(21L, checklist, true),
                        checklistItem(22L, checklist, true),
                        checklistItem(23L, checklist, false)
                ));

        Map<Long, Double> result = targetService.calculateProgressByTargets(List.of(checklist));

        assertThat(result.get(13L)).isCloseTo(2d / 3d, offset(0.000001d));
    }

    @Test
    void calculatesNumericProgressWithInferredStartReverseDirectionAndClamping() {
        Goal goal = goal(1L);
        Target inferredStart = numericTarget(11L, goal, null, 14d, 10d);
        Target reverse = numericTarget(12L, goal, 100d, 80d, 70d);
        Target clamped = numericTarget(13L, goal, 0d, 12d, 10d);

        Map<Long, Double> result = targetService.calculateProgressByTargets(List.of(
                inferredStart,
                reverse,
                clamped
        ));

        assertThat(result.get(11L)).isZero();
        assertThat(result.get(12L)).isCloseTo(2d / 3d, offset(0.000001d));
        assertThat(result.get(13L)).isEqualTo(1d);
        verifyNoInteractions(checklistItemRepository);
    }

    @Test
    @DisplayName("Binary target progress is 0.0 when not done")
    void calculatesBinaryTargetProgressAsZeroWhenNotDone() {
        Target binary = binaryTarget(10L, goal(1L), false);

        Map<Long, Double> result = targetService.calculateProgressByTargets(List.of(binary));

        assertThat(result.get(10L)).isZero();
        verifyNoInteractions(checklistItemRepository);
    }

    @Test
    @DisplayName("Binary target progress is 1.0 when done")
    void calculatesBinaryTargetProgressAsOneWhenDone() {
        Target binary = binaryTarget(10L, goal(1L), true);

        Map<Long, Double> result = targetService.calculateProgressByTargets(List.of(binary));

        assertThat(result.get(10L)).isEqualTo(1d);
        verifyNoInteractions(checklistItemRepository);
    }

    @Test
    @DisplayName("Numeric target progress is 0.0 when current equals start")
    void calculatesNumericProgressAsZeroWhenCurrentEqualsStart() {
        Target numeric = numericTarget(10L, goal(1L), 0d, 0d, 10d);

        Map<Long, Double> result = targetService.calculateProgressByTargets(List.of(numeric));

        assertThat(result.get(10L)).isZero();
        verifyNoInteractions(checklistItemRepository);
    }

    @Test
    @DisplayName("Numeric target progress is 1.0 when current equals total")
    void calculatesNumericProgressAsOneWhenCurrentEqualsTotal() {
        Target numeric = numericTarget(10L, goal(1L), 0d, 10d, 10d);

        Map<Long, Double> result = targetService.calculateProgressByTargets(List.of(numeric));

        assertThat(result.get(10L)).isEqualTo(1d);
        verifyNoInteractions(checklistItemRepository);
    }

    @Test
    @DisplayName("Checklist target progress is 0.0 when no items are done")
    void calculatesChecklistProgressAsZeroWhenNoItemsDone() {
        Goal goal = goal(1L);
        Target checklist = checklistTarget(13L, goal);

        when(checklistItemRepository.findByTargetIdInOrderByTargetIdAscCreatedAtAsc(List.of(13L)))
                .thenReturn(List.of(
                        checklistItem(21L, checklist, false),
                        checklistItem(22L, checklist, false)
                ));

        Map<Long, Double> result = targetService.calculateProgressByTargets(List.of(checklist));

        assertThat(result.get(13L)).isZero();
    }

    @Test
    @DisplayName("Checklist target progress is 1.0 when all items are done")
    void calculatesChecklistProgressAsOneWhenAllItemsDone() {
        Goal goal = goal(1L);
        Target checklist = checklistTarget(13L, goal);

        when(checklistItemRepository.findByTargetIdInOrderByTargetIdAscCreatedAtAsc(List.of(13L)))
                .thenReturn(List.of(
                        checklistItem(21L, checklist, true),
                        checklistItem(22L, checklist, true)
                ));

        Map<Long, Double> result = targetService.calculateProgressByTargets(List.of(checklist));

        assertThat(result.get(13L)).isEqualTo(1d);
    }

    @Test
    @DisplayName("Goal progress is 0.0 when there are no targets")
    void calculatesGoalProgressAsZeroWhenNoTargets() {
        when(targetRepository.findByGoalIdInOrderByGoalIdAscCreatedAtAsc(List.of(1L)))
                .thenReturn(List.of());

        Map<Long, Double> result = targetService.calculateGoalProgressByGoalIds(List.of(1L));

        assertThat(result.get(1L)).isZero();
        verifyNoInteractions(checklistItemRepository);
    }

    @Test
    void createsNumericTargetWithCurrentSetToStart() {
        Goal goal = goal(1L);
        when(goalRepository.findById(1L)).thenReturn(Optional.of(goal));
        when(targetRepository.save(any(Target.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Target target = targetService.create(1L, new CreateTargetInput(
                "Read pages",
                "NUMERIC",
                null,
                10d,
                null,
                20d,
                "pages",
                null,
                null
        ), Map.of());

        assertThat(target.getGoal()).isSameAs(goal);
        assertThat(target.getType()).isEqualTo("numeric");
        assertThat(target.getStart()).isEqualTo(10d);
        assertThat(target.getCurrent()).isEqualTo(10d);
        assertThat(target.getTotal()).isEqualTo(20d);
        assertThat(target.getUnit()).isEqualTo("pages");
    }

    @Test
    @DisplayName("create numeric: descending range (start > total) — current initialised to start")
    void createsNumericTargetWithDescendingRange() {
        Goal goal = goal(1L);
        when(goalRepository.findById(1L)).thenReturn(Optional.of(goal));
        when(targetRepository.save(any(Target.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Target target = targetService.create(1L, new CreateTargetInput(
                "Reduce weight", "numeric", null, 64d, null, 54d, null, null, null
        ), Map.of());

        assertThat(target.getStart()).isEqualTo(64d);
        assertThat(target.getTotal()).isEqualTo(54d);
        assertThat(target.getCurrent()).isEqualTo(64d); // current = start
        verify(targetRepository).save(target);
    }

    @Test
    void rejectsNumericTargetCurrentOnCreate() {
        when(goalRepository.findById(1L)).thenReturn(Optional.of(goal(1L)));

        assertThatThrownBy(() -> targetService.create(1L, new CreateTargetInput(
                "Read pages",
                "numeric",
                null,
                0d,
                1d,
                10d,
                null,
                null,
                null
        ), Map.of("current", 1d)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Numeric target current cannot be set on create");
    }

    // ─── numeric create: missing / null / negative / equal ───────────────────

    @Test
    @DisplayName("create numeric: missing start throws 'Numeric target requires start'")
    void rejectsNumericTargetWithoutStartOnCreate() {
        when(goalRepository.findById(1L)).thenReturn(Optional.of(goal(1L)));

        assertThatThrownBy(() -> targetService.create(1L, new CreateTargetInput(
                "Read pages", "numeric", null, null, null, 10d, null, null, null
        ), Map.of()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Numeric target requires start");

        verify(targetRepository, never()).save(any());
    }

    @Test
    @DisplayName("create numeric: missing total throws 'Numeric target requires total'")
    void rejectsNumericTargetWithoutTotalOnCreate() {
        when(goalRepository.findById(1L)).thenReturn(Optional.of(goal(1L)));

        assertThatThrownBy(() -> targetService.create(1L, new CreateTargetInput(
                "Read pages", "numeric", null, 0d, null, null, null, null, null
        ), Map.of()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Numeric target requires total");

        verify(targetRepository, never()).save(any());
    }

    @Test
    @DisplayName("create numeric: negative start throws 'Numeric target start cannot be negative'")
    void rejectsNumericTargetWithNegativeStartOnCreate() {
        when(goalRepository.findById(1L)).thenReturn(Optional.of(goal(1L)));

        assertThatThrownBy(() -> targetService.create(1L, new CreateTargetInput(
                "Read pages", "numeric", null, -1d, null, 10d, null, null, null
        ), Map.of()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Numeric target start cannot be negative");

        verify(targetRepository, never()).save(any());
    }

    @Test
    @DisplayName("create numeric: negative total throws 'Numeric target target cannot be negative'")
    void rejectsNumericTargetWithNegativeTotalOnCreate() {
        when(goalRepository.findById(1L)).thenReturn(Optional.of(goal(1L)));

        assertThatThrownBy(() -> targetService.create(1L, new CreateTargetInput(
                "Read pages", "numeric", null, 0d, null, -10d, null, null, null
        ), Map.of()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Numeric target target cannot be negative");

        verify(targetRepository, never()).save(any());
    }

    @Test
    @DisplayName("create numeric: start == total throws 'Numeric target start and target must be different'")
    void rejectsNumericTargetWithEqualStartAndTotalOnCreate() {
        when(goalRepository.findById(1L)).thenReturn(Optional.of(goal(1L)));

        assertThatThrownBy(() -> targetService.create(1L, new CreateTargetInput(
                "Read pages", "numeric", null, 10d, null, 10d, null, null, null
        ), Map.of()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Numeric target start and target must be different");

        verify(targetRepository, never()).save(any());
    }

    @Test
    @DisplayName("create numeric: explicit start:null in rawInput throws 'Numeric target start cannot be null'")
    void rejectsExplicitNullStartOnCreate() {
        when(goalRepository.findById(1L)).thenReturn(Optional.of(goal(1L)));
        Map<String, Object> rawInput = new HashMap<>();
        rawInput.put("start", null);

        assertThatThrownBy(() -> targetService.create(1L, new CreateTargetInput(
                "Read pages", "numeric", null, null, null, 10d, null, null, null
        ), rawInput))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Numeric target start cannot be null");

        verify(targetRepository, never()).save(any());
    }

    @Test
    @DisplayName("create numeric: explicit total:null in rawInput throws 'Numeric target target cannot be null'")
    void rejectsExplicitNullTotalOnCreate() {
        when(goalRepository.findById(1L)).thenReturn(Optional.of(goal(1L)));
        Map<String, Object> rawInput = new HashMap<>();
        rawInput.put("total", null);

        assertThatThrownBy(() -> targetService.create(1L, new CreateTargetInput(
                "Read pages", "numeric", null, 0d, null, null, null, null, null
        ), rawInput))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Numeric target target cannot be null");

        verify(targetRepository, never()).save(any());
    }

    @Test
    @DisplayName("create numeric: explicit current:null in rawInput throws 'Numeric target current cannot be null'")
    void rejectsExplicitNullCurrentOnCreate() {
        when(goalRepository.findById(1L)).thenReturn(Optional.of(goal(1L)));
        Map<String, Object> rawInput = new HashMap<>();
        rawInput.put("current", null);

        assertThatThrownBy(() -> targetService.create(1L, new CreateTargetInput(
                "Read pages", "numeric", null, 0d, null, 10d, null, null, null
        ), rawInput))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Numeric target current cannot be null");

        verify(targetRepository, never()).save(any());
    }

    // ─── numeric update: null / negative / equal / range / happy path ─────────

    @Test
    @DisplayName("update numeric: explicit start:null in rawInput throws 'Numeric target start cannot be null'")
    void rejectsExplicitNullStartOnUpdate() {
        Target target = numericTarget(10L, goal(1L), 0d, 5d, 10d);
        when(targetRepository.findById(10L)).thenReturn(Optional.of(target));
        Map<String, Object> rawInput = new HashMap<>();
        rawInput.put("start", null);

        assertThatThrownBy(() -> targetService.update(10L, new UpdateTargetInput(
                null, null, null, null, null, null, null, null, null
        ), false, rawInput))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Numeric target start cannot be null");

        verify(targetRepository, never()).save(any());
    }

    @Test
    @DisplayName("update numeric: explicit current:null in rawInput throws 'Numeric target current cannot be null'")
    void rejectsExplicitNullCurrentOnUpdate() {
        Target target = numericTarget(10L, goal(1L), 0d, 5d, 10d);
        when(targetRepository.findById(10L)).thenReturn(Optional.of(target));
        Map<String, Object> rawInput = new HashMap<>();
        rawInput.put("current", null);

        assertThatThrownBy(() -> targetService.update(10L, new UpdateTargetInput(
                null, null, null, null, null, null, null, null, null
        ), false, rawInput))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Numeric target current cannot be null");

        verify(targetRepository, never()).save(any());
    }

    @Test
    @DisplayName("update numeric: explicit total:null in rawInput throws 'Numeric target target cannot be null'")
    void rejectsExplicitNullTotalOnUpdate() {
        Target target = numericTarget(10L, goal(1L), 0d, 5d, 10d);
        when(targetRepository.findById(10L)).thenReturn(Optional.of(target));
        Map<String, Object> rawInput = new HashMap<>();
        rawInput.put("total", null);

        assertThatThrownBy(() -> targetService.update(10L, new UpdateTargetInput(
                null, null, null, null, null, null, null, null, null
        ), false, rawInput))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Numeric target target cannot be null");

        verify(targetRepository, never()).save(any());
    }

    @Test
    @DisplayName("update numeric: start < 0 throws 'Numeric target start cannot be negative'")
    void rejectsNegativeStartOnUpdate() {
        Target target = numericTarget(10L, goal(1L), 0d, 5d, 10d);
        when(targetRepository.findById(10L)).thenReturn(Optional.of(target));

        assertThatThrownBy(() -> targetService.update(10L, new UpdateTargetInput(
                null, null, null, -1d, null, null, null, null, null
        ), false, Map.of()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Numeric target start cannot be negative");

        verify(targetRepository, never()).save(any());
    }

    @Test
    @DisplayName("update numeric: current < 0 throws 'Numeric target current cannot be negative'")
    void rejectsNegativeCurrentOnUpdate() {
        Target target = numericTarget(10L, goal(1L), 0d, 5d, 10d);
        when(targetRepository.findById(10L)).thenReturn(Optional.of(target));

        assertThatThrownBy(() -> targetService.update(10L, new UpdateTargetInput(
                null, null, null, null, -1d, null, null, null, null
        ), false, Map.of()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Numeric target current cannot be negative");

        verify(targetRepository, never()).save(any());
    }

    @Test
    @DisplayName("update numeric: total < 0 throws 'Numeric target target cannot be negative'")
    void rejectsNegativeTotalOnUpdate() {
        Target target = numericTarget(10L, goal(1L), 0d, 5d, 10d);
        when(targetRepository.findById(10L)).thenReturn(Optional.of(target));

        assertThatThrownBy(() -> targetService.update(10L, new UpdateTargetInput(
                null, null, null, null, null, -1d, null, null, null
        ), false, Map.of()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Numeric target target cannot be negative");

        verify(targetRepository, never()).save(any());
    }

    @Test
    @DisplayName("update numeric: new start equals existing total throws 'start and target must be different'")
    void rejectsEqualStartAndTotalAfterUpdate() {
        // target: start=0, current=5, total=10 — updating start to 10 makes start==total
        Target target = numericTarget(10L, goal(1L), 0d, 5d, 10d);
        when(targetRepository.findById(10L)).thenReturn(Optional.of(target));

        assertThatThrownBy(() -> targetService.update(10L, new UpdateTargetInput(
                null, null, null, 10d, null, null, null, null, null
        ), false, Map.of()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Numeric target start and target must be different");

        verify(targetRepository, never()).save(any());
    }

    @Test
    @DisplayName("update numeric: current below ascending range throws 'must be between start and target'")
    void rejectsCurrentBelowAscendingRangeOnUpdate() {
        // start=1, current=5, total=10 — current=0 is below start
        Target target = numericTarget(10L, goal(1L), 1d, 5d, 10d);
        when(targetRepository.findById(10L)).thenReturn(Optional.of(target));

        assertThatThrownBy(() -> targetService.update(10L, new UpdateTargetInput(
                null, null, null, null, 0d, null, null, null, null
        ), false, Map.of()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Numeric target current must be between start and target");

        verify(targetRepository, never()).save(any());
    }

    @Test
    @DisplayName("update numeric: current above ascending range throws 'must be between start and target'")
    void rejectsCurrentAboveAscendingRangeOnUpdate() {
        // start=1, current=5, total=10 — current=11 exceeds total
        Target target = numericTarget(10L, goal(1L), 1d, 5d, 10d);
        when(targetRepository.findById(10L)).thenReturn(Optional.of(target));

        assertThatThrownBy(() -> targetService.update(10L, new UpdateTargetInput(
                null, null, null, null, 11d, null, null, null, null
        ), false, Map.of()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Numeric target current must be between start and target");

        verify(targetRepository, never()).save(any());
    }

    @Test
    @DisplayName("update numeric: current outside descending range throws 'must be between start and target'")
    void rejectsCurrentOutsideDescendingRangeOnUpdate() {
        // start=64, current=60, total=54 — valid range [54, 64]
        Target target = numericTarget(10L, goal(1L), 64d, 60d, 54d);
        when(targetRepository.findById(10L)).thenReturn(Optional.of(target));

        // above start (> 64)
        assertThatThrownBy(() -> targetService.update(10L, new UpdateTargetInput(
                null, null, null, null, 65d, null, null, null, null
        ), false, Map.of()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Numeric target current must be between start and target");

        // below total (< 54)
        assertThatThrownBy(() -> targetService.update(10L, new UpdateTargetInput(
                null, null, null, null, 53d, null, null, null, null
        ), false, Map.of()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Numeric target current must be between start and target");

        verify(targetRepository, never()).save(any());
    }

    @Test
    @DisplayName("update numeric: changing start persists and shifts progress")
    void canUpdateStartValueForNumericTarget() {
        // start=5, current=5, total=10 → update start to 2 → progress=(5-2)/(10-2)=3/8
        Target target = numericTarget(10L, goal(1L), 5d, 5d, 10d);
        when(targetRepository.findById(10L)).thenReturn(Optional.of(target));
        when(targetRepository.save(any(Target.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Target result = targetService.update(10L, new UpdateTargetInput(
                null, null, null, 2d, null, null, null, null, null
        ), false, Map.of());

        assertThat(result.getStart()).isEqualTo(2d);
        assertThat(result.getCurrent()).isEqualTo(5d);
        assertThat(result.getTotal()).isEqualTo(10d);
        verify(targetRepository).save(target);
    }

    @Test
    @DisplayName("update numeric: changing total persists and shifts progress")
    void canUpdateTotalValueForNumericTarget() {
        // start=0, current=5, total=10 → update total to 20 → progress=5/20=0.25
        Target target = numericTarget(10L, goal(1L), 0d, 5d, 10d);
        when(targetRepository.findById(10L)).thenReturn(Optional.of(target));
        when(targetRepository.save(any(Target.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Target result = targetService.update(10L, new UpdateTargetInput(
                null, null, null, null, null, 20d, null, null, null
        ), false, Map.of());

        assertThat(result.getTotal()).isEqualTo(20d);
        assertThat(result.getStart()).isEqualTo(0d);
        assertThat(result.getCurrent()).isEqualTo(5d);
        verify(targetRepository).save(target);
    }

    // ─── binary create / update ───────────────────────────────────────────────

    @Test
    @DisplayName("create binary: target saved with done=false and type 'binary'")
    void createsBinaryTargetWithDoneFalse() {
        Goal goal = goal(1L);
        when(goalRepository.findById(1L)).thenReturn(Optional.of(goal));
        when(targetRepository.save(any(Target.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Target target = targetService.create(1L, new CreateTargetInput(
                "Send email", "binary", null, null, null, null, null, null, null
        ), Map.of());

        assertThat(target.getType()).isEqualTo("binary");
        assertThat(target.getDone()).isFalse();
        verify(targetRepository).save(target);
    }

    @Test
    @DisplayName("update binary: done=true is persisted")
    void updatesBinaryTargetDoneToTrue() {
        Target target = binaryTarget(10L, goal(1L), false);
        when(targetRepository.findById(10L)).thenReturn(Optional.of(target));
        when(targetRepository.save(any(Target.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Target result = targetService.update(10L, new UpdateTargetInput(
                null, null, null, null, null, null, null, true, null
        ), false, Map.of());

        assertThat(result.getDone()).isTrue();
        verify(targetRepository).save(target);
    }

    @Test
    @DisplayName("update binary: done=false resets to not done")
    void updatesBinaryTargetDoneToFalse() {
        Target target = binaryTarget(10L, goal(1L), true);
        when(targetRepository.findById(10L)).thenReturn(Optional.of(target));
        when(targetRepository.save(any(Target.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Target result = targetService.update(10L, new UpdateTargetInput(
                null, null, null, null, null, null, null, false, null
        ), false, Map.of());

        assertThat(result.getDone()).isFalse();
        verify(targetRepository).save(target);
    }

    @Test
    void rejectsBinaryTargetCreatedAlreadyDone() {
        when(goalRepository.findById(1L)).thenReturn(Optional.of(goal(1L)));

        assertThatThrownBy(() -> targetService.create(1L, new CreateTargetInput(
                "Send email",
                "binary",
                null,
                null,
                null,
                null,
                null,
                true,
                null
        ), Map.of()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Cannot create binary target as already done");
    }

    // ─── checklist create ─────────────────────────────────────────────────────

    @Test
    @DisplayName("create checklist: items are saved and attached to the target")
    void createsChecklistTargetWithItems() {
        Goal goal = goal(1L);
        when(goalRepository.findById(1L)).thenReturn(Optional.of(goal));
        when(targetRepository.save(any(Target.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Target target = targetService.create(1L, new CreateTargetInput(
                "Prepare launch",
                "checklist",
                null, null, null, null, null, null,
                List.of(
                        new ChecklistItemInput(null, "Step 1", false, null, null),
                        new ChecklistItemInput(null, "Step 2", true,  null, null)
                )
        ), Map.of());

        assertThat(target.getType()).isEqualTo("checklist");
        assertThat(target.getItems()).hasSize(2);
        assertThat(target.getItems().get(0).getText()).isEqualTo("Step 1");
        assertThat(target.getItems().get(0).getDone()).isFalse();
        assertThat(target.getItems().get(1).getText()).isEqualTo("Step 2");
        assertThat(target.getItems().get(1).getDone()).isTrue();
        verify(targetRepository).save(target);
    }

    @Test
    @DisplayName("create checklist: null items throws 'Checklist target requires at least one item'")
    void rejectsChecklistTargetWithNullItemsOnCreate() {
        when(goalRepository.findById(1L)).thenReturn(Optional.of(goal(1L)));

        assertThatThrownBy(() -> targetService.create(1L, new CreateTargetInput(
                "Prepare launch", "checklist", null, null, null, null, null, null, null
        ), Map.of()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Checklist target requires at least one item");

        verify(targetRepository, never()).save(any());
    }

    @Test
    @DisplayName("create checklist: empty items list throws 'Checklist target requires at least one item'")
    void rejectsChecklistTargetWithEmptyItemsOnCreate() {
        when(goalRepository.findById(1L)).thenReturn(Optional.of(goal(1L)));

        assertThatThrownBy(() -> targetService.create(1L, new CreateTargetInput(
                "Prepare launch", "checklist", null, null, null, null, null, null, List.of()
        ), Map.of()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Checklist target requires at least one item");

        verify(targetRepository, never()).save(any());
    }

    @Test
    @DisplayName("create binary/numeric with items throws 'Only checklist targets can have items'")
    void rejectsItemsOnNonChecklistTypeOnCreate() {
        when(goalRepository.findById(1L)).thenReturn(Optional.of(goal(1L)));

        assertThatThrownBy(() -> targetService.create(1L, new CreateTargetInput(
                "Send email", "binary", null, null, null, null, null, null,
                List.of(new ChecklistItemInput(null, "Step 1", false, null, null))
        ), Map.of()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Only checklist targets can have items");

        verify(targetRepository, never()).save(any());
    }

    @Test
    void rejectsChecklistTargetWithBlankItemTextOnCreate() {
        when(goalRepository.findById(1L)).thenReturn(Optional.of(goal(1L)));

        assertThatThrownBy(() -> targetService.create(1L, new CreateTargetInput(
                "Prepare launch",
                "checklist",
                null,
                null,
                null,
                null,
                null,
                null,
                List.of(new ChecklistItemInput(null, "   ", false, null, null))
        ), Map.of()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Checklist item text cannot be blank");
    }

    // ─── checklist update ─────────────────────────────────────────────────────

    @Test
    @DisplayName("update checklist: empty items list throws 'Checklist target requires at least one item'")
    void rejectsEmptyItemsOnChecklistUpdate() {
        Target checklist = checklistTarget(13L, goal(1L));
        ChecklistItem existing = checklistItem(21L, checklist, false);
        checklist.getItems().add(existing);
        when(targetRepository.findById(13L)).thenReturn(Optional.of(checklist));

        assertThatThrownBy(() -> targetService.update(13L, new UpdateTargetInput(
                null, null, null, null, null, null, null, null, List.of()
        ), false, Map.of()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Checklist target requires at least one item");

        verify(targetRepository, never()).save(any());
    }

    @Test
    @DisplayName("update checklist: blank item text throws 'Checklist item text cannot be blank'")
    void rejectsBlankItemTextOnChecklistUpdate() {
        Target checklist = checklistTarget(13L, goal(1L));
        ChecklistItem existing = checklistItem(21L, checklist, false);
        checklist.getItems().add(existing);
        when(targetRepository.findById(13L)).thenReturn(Optional.of(checklist));

        assertThatThrownBy(() -> targetService.update(13L, new UpdateTargetInput(
                null, null, null, null, null, null, null, null,
                List.of(new ChecklistItemInput("21", "   ", false, null, null))
        ), false, Map.of()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Checklist item text cannot be blank");

        verify(targetRepository, never()).save(any());
    }

    @Test
    @DisplayName("update checklist: unknown item ID throws 'Checklist item not found'")
    void rejectsNonExistentItemIdOnChecklistUpdate() {
        Target checklist = checklistTarget(13L, goal(1L));
        when(targetRepository.findById(13L)).thenReturn(Optional.of(checklist));

        assertThatThrownBy(() -> targetService.update(13L, new UpdateTargetInput(
                null, null, null, null, null, null, null, null,
                List.of(new ChecklistItemInput("999", "Ghost step", false, null, null))
        ), false, Map.of()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Checklist item not found");

        verify(targetRepository, never()).save(any());
    }

    @Test
    @DisplayName("update non-checklist target with items throws 'Only checklist targets can have items'")
    void rejectsItemsOnNonChecklistTargetOnUpdate() {
        Target binary = binaryTarget(10L, goal(1L), false);
        when(targetRepository.findById(10L)).thenReturn(Optional.of(binary));

        assertThatThrownBy(() -> targetService.update(10L, new UpdateTargetInput(
                null, null, null, null, null, null, null, null,
                List.of(new ChecklistItemInput(null, "Step 1", false, null, null))
        ), false, Map.of()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Only checklist targets can have items");

        verify(targetRepository, never()).save(any());
    }

    @Test
    void rejectsDuplicateChecklistItemIdsOnUpdate() {
        Target checklist = checklistTarget(13L, goal(1L));
        ChecklistItem existing = checklistItem(21L, checklist, false);
        checklist.getItems().add(existing);
        when(targetRepository.findById(13L)).thenReturn(Optional.of(checklist));

        assertThatThrownBy(() -> targetService.update(13L, new UpdateTargetInput(
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                List.of(
                        new ChecklistItemInput("21", "First", false, null, null),
                        new ChecklistItemInput("21", "Duplicate", true, null, null)
                )
        ), false, Map.of()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Checklist item ids must be unique");
    }

    private static Goal goal(Long id) {
        Goal goal = new Goal();
        goal.setId(id);
        goal.setTitle("Goal " + id);
        goal.setDescription("");
        goal.setConfidence(7);
        return goal;
    }

    private static Target numericTarget(Long id, Goal goal, Double start, Double current, Double total) {
        Target target = target(id, goal, "numeric");
        target.setStart(start);
        target.setCurrent(current);
        target.setTotal(total);
        return target;
    }

    private static Target binaryTarget(Long id, Goal goal, boolean done) {
        Target target = target(id, goal, "binary");
        target.setDone(done);
        return target;
    }

    private static Target checklistTarget(Long id, Goal goal) {
        return target(id, goal, "checklist");
    }

    private static Target target(Long id, Goal goal, String type) {
        Target target = new Target();
        target.setId(id);
        target.setGoal(goal);
        target.setType(type);
        target.setTitle(type + " target");
        return target;
    }

    private static ChecklistItem checklistItem(Long id, Target target, boolean done) {
        ChecklistItem item = new ChecklistItem();
        item.setId(id);
        item.setTarget(target);
        item.setText("Item " + id);
        item.setDone(done);
        return item;
    }
}
