package com.spiramindscape.backend.goal;

import com.spiramindscape.backend.graphql.model.RealityPayload;
import org.junit.jupiter.api.DisplayName;
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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RealityServiceTest {

    @Mock
    private RealityRepository realityRepository;

    @Mock
    private GoalService goalService;

    @InjectMocks
    private RealityService realityService;

    // ─── normalizeKind ────────────────────────────────────────────────────────

    @Test
    @DisplayName("Normalizes singular and plural kind inputs to canonical plural form")
    void normalizesSingularAndPluralKinds() {
        assertThat(realityService.normalizeKind("action")).isEqualTo("actions");
        assertThat(realityService.normalizeKind("ACTIONS")).isEqualTo("actions");
        assertThat(realityService.normalizeKind("obstacle")).isEqualTo("obstacles");
        assertThat(realityService.normalizeKind("OBSTACLES")).isEqualTo("obstacles");
    }

    @Test
    @DisplayName("Rejects unknown kind with descriptive error message")
    void rejectsUnknownKind() {
        assertThatThrownBy(() -> realityService.normalizeKind("risk"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Unknown reality kind: risk");
    }

    // ─── buildRealityByGoalIds ────────────────────────────────────────────────

    @Test
    @DisplayName("buildRealityByGoalIds returns empty map for empty input without calling repository")
    void buildRealityByGoalIdsReturnsEmptyMapForEmptyInput() {
        Map<Long, RealityPayload> result = realityService.buildRealityByGoalIds(List.of());

        assertThat(result).isEmpty();
        verifyNoInteractions(realityRepository);
    }

    @Test
    @DisplayName("buildRealityByGoalIds groups items by kind and provides empty lists for goals without items")
    void buildsRealityByGoalIdsWithEmptyListsForGoalsWithoutItems() {
        Goal firstGoal = goal(1L);
        Goal secondGoal = goal(2L);
        when(realityRepository.findByGoalIdInOrderByGoalIdAscCreatedAtAsc(List.of(1L, 2L, 3L)))
                .thenReturn(List.of(
                        item(10L, firstGoal, "actions"),
                        item(11L, firstGoal, "obstacles"),
                        item(12L, secondGoal, "actions")
                ));

        Map<Long, RealityPayload> payloads = realityService.buildRealityByGoalIds(List.of(1L, 2L, 3L));

        assertThat(payloads.get(1L).actions()).extracting(RealityItem::getId).containsExactly(10L);
        assertThat(payloads.get(1L).obstacles()).extracting(RealityItem::getId).containsExactly(11L);
        assertThat(payloads.get(2L).actions()).extracting(RealityItem::getId).containsExactly(12L);
        assertThat(payloads.get(2L).obstacles()).isEmpty();
        assertThat(payloads.get(3L).actions()).isEmpty();
        assertThat(payloads.get(3L).obstacles()).isEmpty();
    }

    // ─── findItemById ─────────────────────────────────────────────────────────

    @Test
    @DisplayName("findItemById returns the reality item when it exists")
    void findItemByIdReturnsItemWhenFound() {
        Goal goal = goal(1L);
        RealityItem item = item(10L, goal, "actions");
        when(realityRepository.findById(10L)).thenReturn(Optional.of(item));

        RealityItem result = realityService.findItemById(10L);

        assertThat(result).isSameAs(item);
        assertThat(result.getId()).isEqualTo(10L);
        assertThat(result.getKind()).isEqualTo("actions");
    }

    @Test
    @DisplayName("findItemById throws NOT_FOUND when reality item does not exist")
    void findItemByIdThrowsWhenNotFound() {
        when(realityRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> realityService.findItemById(99L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Reality item not found: 99");
    }

    // ─── findByGoal ───────────────────────────────────────────────────────────

    @Test
    @DisplayName("findByGoal throws NOT_FOUND when goal does not exist")
    void findByGoalThrowsWhenGoalNotFound() {
        when(goalService.findById(99L)).thenThrow(new IllegalArgumentException("Goal not found: 99"));

        assertThatThrownBy(() -> realityService.findByGoal(99L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Goal not found: 99");
    }

    // ─── addItem ──────────────────────────────────────────────────────────────

    @Test
    @DisplayName("addItem saves item with normalized kind and returns updated reality payload")
    void addItemSavesItemAndReturnsUpdatedPayload() {
        Goal goal = goal(1L);
        when(goalService.findById(1L)).thenReturn(goal);
        when(realityRepository.findByGoalIdOrderByCreatedAtAsc(1L)).thenReturn(List.of(
                item(10L, goal, "actions")
        ));

        RealityPayload result = realityService.addItem(1L, "action", "Take notes");

        verify(realityRepository).save(any(RealityItem.class));
        assertThat(result.actions()).extracting(RealityItem::getId).containsExactly(10L);
        assertThat(result.obstacles()).isEmpty();
    }

    @Test
    @DisplayName("addItem throws NOT_FOUND when goal does not exist")
    void addItemThrowsWhenGoalNotFound() {
        when(goalService.findById(99L)).thenThrow(new IllegalArgumentException("Goal not found: 99"));

        assertThatThrownBy(() -> realityService.addItem(99L, "actions", "Take notes"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Goal not found: 99");
    }

    @Test
    @DisplayName("addItem stores item with normalized plural kind even when singular form is passed")
    void addItemNormalizesKindToPlural() {
        Goal goal = goal(1L);
        when(goalService.findById(1L)).thenReturn(goal);
        when(realityRepository.save(any(RealityItem.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(realityRepository.findByGoalIdOrderByCreatedAtAsc(1L)).thenReturn(List.of());

        realityService.addItem(1L, "action", "Take notes");

        verify(realityRepository).save(argThat(saved -> "actions".equals(saved.getKind())));
    }

    @Test
    @DisplayName("addItem rejects blank text")
    void addItemRejectsBlankText() {
        Goal goal = goal(1L);
        when(goalService.findById(1L)).thenReturn(goal);

        assertThatThrownBy(() -> realityService.addItem(1L, "actions", "   "))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Reality item text is required");
    }

    @Test
    @DisplayName("addItem trims whitespace from text before saving")
    void addItemTrimsWhitespaceFromText() {
        Goal goal = goal(1L);
        when(goalService.findById(1L)).thenReturn(goal);
        when(realityRepository.save(any(RealityItem.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(realityRepository.findByGoalIdOrderByCreatedAtAsc(1L)).thenReturn(List.of());

        realityService.addItem(1L, "actions", "   Read documentation   ");

        verify(realityRepository).save(argThat(saved -> "Read documentation".equals(saved.getText())));
    }

    @Test
    @DisplayName("addItem accepts text at maximum length")
    void addItemAcceptsTextAtMaximumLength() {
        Goal goal = goal(1L);
        when(goalService.findById(1L)).thenReturn(goal);
        when(realityRepository.save(any(RealityItem.class))).thenAnswer(invocation -> invocation.getArgument(0));
        String maxText = "A".repeat(RealityItem.MAX_REALITY_ITEM_TEXT_LENGTH);
        when(realityRepository.findByGoalIdOrderByCreatedAtAsc(1L)).thenReturn(List.of());

        realityService.addItem(1L, "actions", maxText);

        verify(realityRepository).save(argThat(saved -> maxText.equals(saved.getText())));
    }

    @Test
    @DisplayName("addItem rejects text exceeding maximum length")
    void addItemRejectsOversizedText() {
        Goal goal = goal(1L);
        when(goalService.findById(1L)).thenReturn(goal);
        String oversized = "A".repeat(RealityItem.MAX_REALITY_ITEM_TEXT_LENGTH + 1);

        assertThatThrownBy(() -> realityService.addItem(1L, "actions", oversized))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Reality item text must be");
    }

    // ─── updateItem ───────────────────────────────────────────────────────────

    @Test
    @DisplayName("updateItem updates text and returns updated payload")
    void updateItemUpdatesTextAndReturnsPayload() {
        Goal goal = goal(1L);
        RealityItem existing = item(10L, goal, "actions");
        when(realityRepository.findById(10L)).thenReturn(Optional.of(existing));
        when(realityRepository.findByGoalIdOrderByCreatedAtAsc(1L)).thenReturn(List.of(existing));

        RealityPayload result = realityService.updateItem(1L, "actions", 10L, "Updated text");

        verify(realityRepository).save(existing);
        assertThat(existing.getText()).isEqualTo("Updated text");
        assertThat(result.actions()).extracting(RealityItem::getId).containsExactly(10L);
    }

    @Test
    @DisplayName("updateItem throws NOT_FOUND when reality item does not exist")
    void updateItemThrowsWhenItemNotFound() {
        when(realityRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> realityService.updateItem(1L, "actions", 99L, "Updated"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Reality item not found: 99");

        verify(realityRepository, never()).save(any(RealityItem.class));
    }

    @Test
    @DisplayName("updateItem rejects item that belongs to a different goal")
    void updateItemRejectsCrossGoalItem() {
        Goal wrongGoal = goal(2L);
        RealityItem itemOfOtherGoal = item(10L, wrongGoal, "actions");
        when(realityRepository.findById(10L)).thenReturn(Optional.of(itemOfOtherGoal));

        assertThatThrownBy(() -> realityService.updateItem(1L, "actions", 10L, "Updated"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Reality item does not belong to goal/kind");
    }

    @Test
    @DisplayName("updateItem rejects item when kind does not match")
    void updateItemRejectsKindMismatch() {
        Goal goal = goal(1L);
        RealityItem actionItem = item(10L, goal, "actions");
        when(realityRepository.findById(10L)).thenReturn(Optional.of(actionItem));

        assertThatThrownBy(() -> realityService.updateItem(1L, "obstacles", 10L, "Updated"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Reality item does not belong to goal/kind");
    }

    @Test
    @DisplayName("updateItem rejects blank text")
    void updateItemRejectsBlankText() {
        Goal goal = goal(1L);
        RealityItem existing = item(10L, goal, "actions");
        when(realityRepository.findById(10L)).thenReturn(Optional.of(existing));

        assertThatThrownBy(() -> realityService.updateItem(1L, "actions", 10L, ""))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Reality item text is required");
    }

    @Test
    @DisplayName("updateItem accepts text at maximum length")
    void updateItemAcceptsTextAtMaximumLength() {
        Goal goal = goal(1L);
        RealityItem existing = item(10L, goal, "actions");
        when(realityRepository.findById(10L)).thenReturn(Optional.of(existing));
        when(realityRepository.save(any(RealityItem.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(realityRepository.findByGoalIdOrderByCreatedAtAsc(1L)).thenReturn(List.of(existing));
        String maxText = "A".repeat(RealityItem.MAX_REALITY_ITEM_TEXT_LENGTH);

        realityService.updateItem(1L, "actions", 10L, maxText);

        verify(realityRepository).save(argThat(saved -> maxText.equals(saved.getText())));
    }

    @Test
    @DisplayName("updateItem rejects text exceeding maximum length and does not save")
    void updateItemRejectsOversizedText() {
        Goal goal = goal(1L);
        RealityItem existing = item(10L, goal, "actions");
        when(realityRepository.findById(10L)).thenReturn(Optional.of(existing));
        String oversized = "A".repeat(RealityItem.MAX_REALITY_ITEM_TEXT_LENGTH + 1);

        assertThatThrownBy(() -> realityService.updateItem(1L, "actions", 10L, oversized))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Reality item text must be");

        verify(realityRepository, never()).save(any(RealityItem.class));
    }

    // ─── removeItem ───────────────────────────────────────────────────────────

    @Test
    @DisplayName("removeItem deletes item and returns updated payload without the removed item")
    void removeItemDeletesAndReturnsUpdatedPayload() {
        Goal goal = goal(1L);
        RealityItem existing = item(10L, goal, "actions");
        when(realityRepository.findById(10L)).thenReturn(Optional.of(existing));
        when(realityRepository.findByGoalIdOrderByCreatedAtAsc(1L)).thenReturn(List.of());

        RealityPayload result = realityService.removeItem(1L, "actions", 10L);

        verify(realityRepository).delete(existing);
        assertThat(result.actions()).isEmpty();
        assertThat(result.obstacles()).isEmpty();
    }

    @Test
    @DisplayName("removeItem throws NOT_FOUND when reality item does not exist")
    void removeItemThrowsWhenItemNotFound() {
        when(realityRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> realityService.removeItem(1L, "actions", 99L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Reality item not found: 99");

        verify(realityRepository, never()).delete(any(RealityItem.class));
    }

    @Test
    @DisplayName("removeItem rejects item when kind does not match")
    void removeItemRejectsKindMismatch() {
        Goal goal = goal(1L);
        RealityItem actionItem = item(10L, goal, "actions");
        when(realityRepository.findById(10L)).thenReturn(Optional.of(actionItem));

        assertThatThrownBy(() -> realityService.removeItem(1L, "obstacles", 10L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Reality item does not belong to goal/kind");

        verify(realityRepository, never()).delete(any(RealityItem.class));
    }

    @Test
    @DisplayName("removeItem rejects item that belongs to a different goal")
    void removeItemRejectsCrossGoalItem() {
        RealityItem itemOfOtherGoal = item(10L, goal(2L), "actions");
        when(realityRepository.findById(10L)).thenReturn(Optional.of(itemOfOtherGoal));

        assertThatThrownBy(() -> realityService.removeItem(1L, "actions", 10L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Reality item does not belong to goal/kind");
    }

    // ─── findByGoal ───────────────────────────────────────────────────────────

    @Test
    @DisplayName("findByGoal returns reality payload built from repository items for that goal")
    void findByGoalReturnsBuildReality() {
        Goal goal = goal(1L);
        when(goalService.findById(1L)).thenReturn(goal);
        when(realityRepository.findByGoalIdOrderByCreatedAtAsc(1L)).thenReturn(List.of(
                item(10L, goal, "actions"),
                item(11L, goal, "obstacles")
        ));

        RealityPayload result = realityService.findByGoal(1L);

        assertThat(result.actions()).extracting(RealityItem::getId).containsExactly(10L);
        assertThat(result.obstacles()).extracting(RealityItem::getId).containsExactly(11L);
    }

    // ─── helpers ──────────────────────────────────────────────────────────────

    private static Goal goal(Long id) {
        Goal goal = new Goal();
        goal.setId(id);
        goal.setTitle("Goal " + id);
        goal.setDescription("");
        goal.setConfidence(7);
        return goal;
    }

    private static RealityItem item(Long id, Goal goal, String kind) {
        RealityItem item = new RealityItem();
        item.setId(id);
        item.setGoal(goal);
        item.setKind(kind);
        item.setText("Item " + id);
        return item;
    }
}
