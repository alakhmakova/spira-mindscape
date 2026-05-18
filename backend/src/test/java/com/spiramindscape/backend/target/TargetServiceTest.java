package com.spiramindscape.backend.target;

import com.spiramindscape.backend.goal.Goal;
import com.spiramindscape.backend.goal.GoalRepository;
import com.spiramindscape.backend.graphql.input.ChecklistItemInput;
import com.spiramindscape.backend.graphql.input.CreateTargetInput;
import com.spiramindscape.backend.graphql.input.UpdateTargetInput;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.data.Offset.offset;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

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
